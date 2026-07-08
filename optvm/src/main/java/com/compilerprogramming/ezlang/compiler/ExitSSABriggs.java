package com.compilerprogramming.ezlang.compiler;

import java.util.*;

/**
 * Converts from SSA form to non-SSA form.
 * Implementation is based on description in
 * 'Practical Improvements to the Construction and Destruction
 * of Static Single Assignment Form' by Preston Briggs.
 *
 * The JikesRVM LeaveSSA implements a version of the
 * same algorithm.
 */
public class ExitSSABriggs {

    CompiledFunction function;
    NameStack[] stacks;
    DominatorTree tree;

    public ExitSSABriggs(CompiledFunction function, EnumSet<Options> options) {
        this.function = function;
        if (!function.isSSA) throw new IllegalStateException();
        function.livenessAnalysis();
        if (options.contains(Options.DUMP_SSA_LIVENESS)) function.dumpIR(true, "SSA Liveness Analysis");
        tree = new DominatorTree(function.entry);
        if (options.contains(Options.DUMP_SSA_DOMTREE)) {
            System.out.println("Pre SSA Dominator Tree");
            System.out.println(tree.generateDotOutput());
        }
        initStack();
        insertCopies(function.entry);
        removePhis();
        function.isSSA = false;
        if (options.contains(Options.DUMP_POST_SSA_IR)) function.dumpIR(false, "After exiting SSA");
    }

    private void removePhis() {
        for (BasicBlock block : tree.blocks) {
            block.instructions.removeIf(instruction -> instruction instanceof Instruction.Phi);
        }
    }

    /* Algorithm for iterating through blocks to perform phi replacement */
    private void insertCopies(BasicBlock block) {
        List<Integer> pushed = new ArrayList<>();
        for (Instruction i: block.instructions) {
            // replace all uses u with stacks[i]
            replaceUses(i);
        }
        scheduleCopies(block, pushed);
        for (BasicBlock c: block.dominatedChildren) {
            insertCopies(c);
        }
        for (Integer name: pushed) {
            stacks[name].pop();
        }
    }

    /**
     * replace all uses u with stacks[i]
     */
    private void replaceUses(Instruction i) {
        if (i instanceof Instruction.Phi)
            // FIXME check this can never be valid
            // tests 8/9 in TestInterpreter invoke on Phi but
            // replacements are same as existing inputs
            return;
        var oldUses = i.uses();
        Register[] newUses = new Register[oldUses.size()];
        for (int u = 0; u < oldUses.size(); u++) {
            Register use = oldUses.get(u);
            if (!stacks[use.id].isEmpty())
                newUses[u] = stacks[use.id].top();
            else
                newUses[u] = use;
        }
        i.replaceUses(newUses);
    }

    static class CopyItem {
        /** Phi input can be a register or a constant so we record the operand */
        final Operand src;
        /** The phi destination */
        final Register dest;
        /** The basic block where the phi was present */
        final BasicBlock destBlock;
        boolean removed;

        public CopyItem(Operand src, Register dest, BasicBlock destBlock) {
            this.src = src;
            this.dest = dest;
            this.destBlock = destBlock;
            this.removed = false;
        }
    }

    private void scheduleCopies(BasicBlock block, List<Integer> pushed) {
        /* Pass 1 - Initialize data structures */
        /* In this pass we count the number of times a name is used by other phi-nodes */
        List<CopyItem> copySet = new ArrayList<>();
        Map<Integer, Register> map = new HashMap<>();
        BitSet usedByAnother = new BitSet(function.registerPool.numRegisters()*2);
        for (BasicBlock s: block.successors) {
            int j = s.whichPred(block);
            for (Instruction.Phi phi: s.phis()) {
                Register dst = phi.value();
                Operand srcOperand = phi.input(j); // jth operand of phi node
                if (srcOperand instanceof Operand.RegisterOperand srcRegisterOperand) {
                    Register src = srcRegisterOperand.reg;
                    map.put(src.id, src);
                    usedByAnother.set(src.id);
                }
                copySet.add(new CopyItem(srcOperand, dst, s));
                map.put(dst.id, dst);
            }
        }

        /* Pass 2: setup up the worklist of initial copies */
        /* In this pass we build a worklist of names that are not used in other phi nodes */
        List<CopyItem> workList = new ArrayList<>();
        for (CopyItem copyItem: copySet) {
            if (usedByAnother.get(copyItem.dest.id) != true) {
                copyItem.removed = true;
                workList.add(copyItem);
            }
        }
        copySet.removeIf(copyItem -> copyItem.removed);

        /* Pass 3: iterate over the worklist, inserting copies */
        /* Copy operations whose destinations are not used by other copy operations can be scheduled immediately */
        /* Each time we insert a copy operation we add the source of that op to the worklist */
        while (!workList.isEmpty() || !copySet.isEmpty()) {
            while (!workList.isEmpty()) {
                final CopyItem copyItem = workList.remove(0);
                final Operand src = copyItem.src;
                final Register dest = copyItem.dest;
                final BasicBlock destBlock = copyItem.destBlock;
                /* Engineering a Compiler: We can avoid the lost copy
                   problem by checking the liveness of the target name
                   for each copy that we try to insert. When we discover
                   a copy target that is live, we must preserve the live
                   value in a temporary name and rewrite subsequent uses to
                   refer to the temporary name.

                   This captures the cases when the result of a phi
                   in a control successor is live on exit of the current block.
                   This means that it is incorrect to simply insert a copy
                   of the destination in the current block. So we rename
                   the destination to a new temporary, and record the renaming
                   so that the dominator blocks get the new name. Comment adapted
                   from JikesRVM LeaveSSA
                 */
                if (block.liveOut.get(dest.id)) {
                    /* Insert a copy from dest to a new temp t at phi node defining dest */
                    final Register t = addMoveToTempAfterPhi(destBlock, dest);
                    stacks[dest.id].push(t);    // record the temp name
                    pushed.add(dest.id);
                }
                /* Insert a copy operation from map[src] to dest at end of BB */
                if (src instanceof Operand.RegisterOperand srcRegisterOperand) {
                    addMoveAtBBEnd(block, map.get(srcRegisterOperand.reg.id), dest);
                    map.put(srcRegisterOperand.reg.id, dest);
                    /* If src is the name of a dest in copySet add item to worklist */
                    /* see comment on phi cycles below. */
                    CopyItem item = isCycle(copySet, srcRegisterOperand.reg);
                    if (item != null) {
                        workList.add(item);
                    }
                }
                else if (src instanceof Operand.IntConstantOperand ||
                         src instanceof Operand.FloatConstantOperand ||
                         src instanceof Operand.NullConstantOperand) {
                    addMoveAtBBEnd(block, src, dest);
                }
                else throw new IllegalStateException("Unexpected phi source operand: " + src);
            }
            /* Engineering a Compiler: To solve the swap problem
               we can detect cases where phi functions reference the
               targets of other phi functions in the same block. For each
               cycle of references, it must insert a copy to a temporary
               that breaks the cycle. Then we can schedule the copies to
               respect the dependencies implied by the phi functions.

               An empty work list with work remaining in the copy set
               implies a cycle in the dependencies amongst copies. To break
               the cycle copy the destination of an arbitrary member of the
               copy set to a temporary. This destination has therefore been
               saved and can be safely overwritten. So then add the copy to the
               work list. Comment adapted from JikesRVM LeaveSSA.
             */
            if (!copySet.isEmpty()) {
                CopyItem copyItem = copySet.remove(0);
                /* Insert a copy from dst to new temp at the end of Block */
                Register t = addMoveToTempAtBBEnd(block, copyItem.dest);
                map.put(copyItem.dest.id, t);
                workList.add(copyItem);
            }
        }
    }

    private void insertAtEnd(BasicBlock bb, Instruction i) {
        assert bb.instructions.size() > 0;
        // Last instruction is a branch - so new instruction will
        // go before that
        int pos = bb.instructions.size()-1;
        bb.add(pos, i);
    }

    private void insertAfterPhi(BasicBlock bb, Register phiDef, Instruction newInst) {
        assert bb.instructions.size() > 0;
        int insertionPos = -1;
        for (int pos = 0; pos < bb.instructions.size(); pos++) {
            Instruction i = bb.instructions.get(pos);
            if (i instanceof Instruction.Phi phi) {
                if (phi.value().id == phiDef.id) {
                    insertionPos = pos+1;   // After phi
                    break;
                }
            }
        }
        if (insertionPos < 0) {
            throw new IllegalStateException();
        }
        bb.add(insertionPos, newInst);
    }

    /* Insert a copy from dest to new temp at end of BB, and return temp */
    private Register addMoveToTempAtBBEnd(BasicBlock block, Register dest) {
        var temp = function.registerPool.newTempReg(dest.name(), dest.type);
        var inst = new Instruction.Move(new Operand.RegisterOperand(dest), new Operand.RegisterOperand(temp));
        insertAtEnd(block, inst);
        return temp;
    }

    /* If src is the name of a dest in copySet remove the item */
    private CopyItem isCycle(List<CopyItem> copySet, Register src) {
        for (int i = 0; i < copySet.size(); i++) {
            CopyItem copyItem = copySet.get(i);
            if (copyItem.dest.id == src.id) {
                copySet.remove(i);
                return copyItem;
            }
        }
        return null;
    }

    /* Insert a copy from src to dst at end of BB */
    private void addMoveAtBBEnd(BasicBlock block, Register src, Register dest) {
        var inst = new Instruction.Move(new Operand.RegisterOperand(src), new Operand.RegisterOperand(dest));
        insertAtEnd(block, inst);
        // If the copy instruction is followed by a cbr which uses the old var
        // then we need to update the cbr instruction
        // This is not specified in the Briggs paper but t
        var brInst = block.instructions.getLast();
        if (brInst instanceof Instruction.ConditionalBranch cbr) {
            cbr.replaceUse(src,dest);
        }
    }
    /* Insert a copy from constant src (int, float or null) to dst at end of BB */
    private void addMoveAtBBEnd(BasicBlock block, Operand src, Register dest) {
        var inst = new Instruction.Move(src, new Operand.RegisterOperand(dest));
        insertAtEnd(block, inst);
    }
    /* Insert a copy dest to a new temp at phi node defining dest, return temp */
    private Register addMoveToTempAfterPhi(BasicBlock block, Register dest) {
        var temp = function.registerPool.newTempReg(dest.name(), dest.type);
        var inst = new Instruction.Move(new Operand.RegisterOperand(dest), new Operand.RegisterOperand(temp));
        insertAfterPhi(block, dest, inst);
        return temp;
    }

    private void initStack() {
        stacks = new NameStack[function.registerPool.numRegisters()];
        for (int i = 0; i < stacks.length; i++)
            stacks[i] = new NameStack();
    }

    static class NameStack {
        List<Register> stack = new ArrayList<>();
        void push(Register r) { stack.add(r); }
        Register top() { return stack.getLast(); }
        void pop() { stack.removeLast(); }
        boolean isEmpty() { return stack.isEmpty(); }
    }
}
