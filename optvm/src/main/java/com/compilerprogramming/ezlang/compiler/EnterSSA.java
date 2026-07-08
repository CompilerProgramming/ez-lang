package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.exceptions.CompilerException;

import java.util.*;

/**
 * Transform a bytecode function to Semi-pruned SSA form.
 * The algorithm is described in the paper
 * 'Practical Improvements to the Construction and Destruction
 * of Single Static Assigment Form' by Preston Briggs.
 *
 * See docs/ssa-construction.md for a description of this implementation.
 */
public class EnterSSA {

    CompiledFunction function;
    DominatorTree domTree;
    /**
     * Non-local names are set of variables that are live
     * on entry to _some_ BasicBlock in the program.
     *
     * Note that a variable in the Literature is represented
     * by a Register in our implementation
     */
    Register[] nonLocalNames;
    BBSet[] blockSets;
    /**
     * Basic blocks in reverse post order
     */
    List<BasicBlock> blocks;
    /**
     * Used to generate SSA version for each variable (register)
     */
    int[] counters;
    VersionStack[] stacks;

    public EnterSSA(CompiledFunction bytecodeFunction, EnumSet<Options> options) {
        this.function = bytecodeFunction;
        if (function.isSSA) throw new CompilerException("Supplied function is already in SSA form");
        setupGlobals();
        computeDomTreeAndDominanceFrontiers();
        if (options.contains(Options.DUMP_PRE_SSA_DOMTREE)) {
            System.out.println("Pre SSA Dominator Tree");
            System.out.println(domTree.generateDotOutput());
        }
        if (options.contains(Options.DUMP_PRE_SSA_DOMFRONTIERS)) {
            System.out.println("Pre SSA Dominance Frontiers");
            System.out.println(domTree.listDomFrontiers());
        }
        this.blocks = domTree.blocks;   // the blocks are ordered reverse post order
        findNonLocalNames();
        new Liveness(bytecodeFunction); // EWe require liveness info to construct pruned ssa
        if (options.contains(Options.DUMP_PRE_SSA_LIVENESS)) {
            System.out.println("Pre SSA Liveness");
            System.out.println(bytecodeFunction.toStr(new StringBuilder(), true));
        }
        insertPhis();
        renameVars();
        bytecodeFunction.isSSA = true;
        bytecodeFunction.hasLiveness = false;
        if (options.contains(Options.DUMP_SSA_IR)) bytecodeFunction.dumpIR(false, "Post SSA IR");
    }

    private void computeDomTreeAndDominanceFrontiers() {
        domTree = new DominatorTree(function.entry);
    }

    private void setupGlobals() {
        nonLocalNames = new Register[function.frameSize()];
        blockSets = new BBSet[function.frameSize()];
    }

    /**
     * Compute set of registers that are live across multiple blocks
     * i.e. are not exclusively used in a single block.
     */
    private void findNonLocalNames() {
        for (BasicBlock block : blocks) {
            var varKill = new HashSet<Integer>();
            for (Instruction instruction: block.instructions) {
                for (Register reg : instruction.uses()) {
                    if (!varKill.contains(reg.nonSSAId())) {
                        nonLocalNames[reg.nonSSAId()] = reg;
                    }
                }
                if (instruction.definesVar()) {
                    Register reg = instruction.def();
                    varKill.add(reg.nonSSAId());
                    if (blockSets[reg.nonSSAId()] == null) {
                        blockSets[reg.nonSSAId()] = new BBSet();
                    }
                    blockSets[reg.nonSSAId()].add(block);
                }
            }
        }
    }

    void insertPhis() {
        for (int i = 0; i < nonLocalNames.length; i++) {
            Register x = nonLocalNames[i];
            if (x != null) {
                var visited = new BitSet();
                var worklist = new WorkList(blockSets[x.nonSSAId()].blocks);
                var b = worklist.pop();
                while (b != null) {
                    visited.set(b.bid);
                    for (BasicBlock d: b.dominationFrontier) {
                        // Perform a liveness check to avoid inserting
                        // phi when variable is dead
                        // Inserting dead phis causes problems during renaming
                        // because there will not be a definition available
                        if (d.liveIn.contains(x)) {
                            // insert phi for x in d
                            d.insertPhiFor(x);
                            if (!visited.get(d.bid))
                                worklist.push(d);
                        }
                    }
                    b = worklist.pop();
                }
            }
        }
    }

    void renameVars() {
        initVersionCounters();
        search(function.entry);
    }

    /**
     * Creates and pushes new name
     */
    Register makeVersion(Register reg) {
        int id = reg.nonSSAId();
        int version = counters[id];
        var ssaReg = function.registerPool.ssaReg(reg, version);
        stacks[id].push(ssaReg);
        counters[id] = counters[id] + 1;
        return ssaReg;
    }

    /**
     * Recursively walk the Dominator Tree, renaming variables.
     * Implementation is based on the algorithm in the Preston Briggs
     * paper Practical Improvements to the Construction and Destruction of
     * Static Single Assignment Form
     */
    void search(BasicBlock block) {
        // Replace v = phi(...) with v_i = phi(...)
        for (Instruction.Phi phi: block.phis()) {
            Register ssaReg = makeVersion(phi.value());
            phi.replaceValue(ssaReg);
        }
        // for each instruction v = x op y
        // first replace x,y
        // then replace v
        for (Instruction instruction: block.instructions) {
            if (instruction instanceof Instruction.Phi)
                continue;
            // first replace x,y
            var uses = instruction.uses();
            if (!uses.isEmpty()) {
                Register[] newUses = new Register[uses.size()];
                for (int i = 0; i < newUses.length; i++) {
                    Register oldReg = uses.get(i);
                    newUses[i] = stacks[oldReg.nonSSAId()].top();
                }
                instruction.replaceUses(newUses);
            }
            // then replace v
            if (instruction.definesVar()) {
                Register ssaReg = makeVersion(instruction.def());
                instruction.replaceDef(ssaReg);
            }
        }
        // Update phis in successor blocks
        for (BasicBlock s: block.successors) {
            int j = s.whichPred(block);
            for (Instruction.Phi phi: s.phis()) {
                Register oldReg = phi.inputAsRegister(j);
                phi.replaceInput(j, stacks[oldReg.nonSSAId()].top());
            }
        }
        // Recurse down the dominator tree
        for (BasicBlock c: block.dominatedChildren) {
            search(c);
        }
        // Pop stacks for defs
        for (Instruction i: block.instructions) {
            // Phis don't answer to definesVar() or def()
            if (i.definesVar() || i instanceof Instruction.Phi) {
                var reg = i instanceof Instruction.Phi phi ? phi.value() : i.def();
                stacks[reg.nonSSAId()].pop();
            }
        }
    }

    private void initVersionCounters() {
        counters = new int[nonLocalNames.length];
        stacks = new VersionStack[nonLocalNames.length];
        for (int i = 0; i < nonLocalNames.length; i++) {
            counters[i] = 0;
            stacks[i] = new VersionStack();
        }
    }

    static class BBSet {
        Set<BasicBlock> blocks = new HashSet<>();
        void add(BasicBlock block) { blocks.add(block); }
    }

    static class VersionStack {
        List<Register.SSARegister> stack = new ArrayList<>();
        void push(Register.SSARegister r) { stack.add(r); }
        Register.SSARegister top() {
            if (stack.isEmpty())
                throw new CompilerException("Variable may not be initialized");
            return stack.getLast();
        }
        void pop() { stack.removeLast(); }
    }

    /**
     * Simple worklist
     */
    public static class WorkList {

        private ArrayList<BasicBlock> blocks;
        private final BitSet members;

        WorkList() {
            blocks = new ArrayList<>();
            members = new BitSet();
        }
        WorkList(Collection<BasicBlock> blocks) {
            this();
            addAll(blocks);
        }
        public BasicBlock push( BasicBlock x ) {
            if( x==null ) return null;
            int idx = x.bid;
            if( !members.get(idx) ) {
                members.set(idx);
                blocks.add(x);
            }
            return x;
        }
        public void addAll( Collection<BasicBlock> ary ) {
            for( BasicBlock n : ary )
                push(n);
        }
        BasicBlock pop() {
            if ( blocks.isEmpty() )
                return null;
            var x = blocks.removeFirst();
            members.clear(x.bid);
            return x;
        }
    }
}
