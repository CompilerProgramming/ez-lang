package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.LatticeElement;

import java.util.*;

import static com.compilerprogramming.ezlang.types.LatticeElement.*;

/**
 * Implementation of Sparse Conditional Constant Propagation based on descriptions
 * in:
 *
 * <ol>
 *     <li>Constant Propagation with Conditional Branches. Wegman and Zadeck.</li>
 *     <li>Modern Compiler Implementation in C, Andrew Appel, section 19.3</li>
 *     <li>Building an Optimizing Compiler, Bob Morgan, section 8.3</li>
 * </ol>
 */
public class SparseConditionalConstantPropagation {

    /**
     * Contains a lattice for each SSA definition
     */
    ValueLattice valueLattice;
    /**
     * Executable status for each flow edge, initially all edges are
     * marked non-executable except the start block
     */
    Map<FlowEdge, Boolean> flowEdges;
    /**
     * Worklist of ssaedges (the term used by SCCP paper)
     */
    WorkList<Instruction> instructionWorkList;
    /**
     * As edges between basic blocks become executable, we
     * add them the impacted blocks to the worklist for processing.
     */
    WorkList<BasicBlock> flowWorklist;
    /**
     * We don't evaluate a block more than once (except for Phi instructions
     * in the block). So we have to track which blocks have already been
     * evaluated.
     */
    BitSet visited = new BitSet();
    /**
     * Def use chains for each register
     * Called SSAEdge in the original paper.
     */
    Map<Register, SSAEdges.SSADef> ssaEdges;
    CompiledFunction function;

    /** Used to track reachable blocks when the SCCP changes are applied */
    BitSet executableBlocks = new BitSet();

    public SparseConditionalConstantPropagation constantPropagation(CompiledFunction function) {
        init(function);
        while (!flowWorklist.isEmpty() || !instructionWorkList.isEmpty()) {
            while (!instructionWorkList.isEmpty()) {
                Instruction i = instructionWorkList.pop();
                visitInstruction(i);
            }
            while (!flowWorklist.isEmpty()) {
                BasicBlock b = flowWorklist.pop();
                visitBlock(b);
            }
        }
        return this;
    }

    private void visitBlock(BasicBlock b) {
        for (var phi : b.phis()) {
            visitInstruction(phi);
        }
        if (!visited.get(b.bid)) {
            visited.set(b.bid);
            for (var i : b.instructions) {
                visitInstruction(i);
            }
        }
    }

    private void visitInstruction(Instruction instruction) {
        BasicBlock block = instruction.block;
        if (evalInstruction(instruction)) {
            if (instruction instanceof Instruction.ConditionalBranch || instruction instanceof Instruction.Jump) {
                for (BasicBlock s : block.successors) {
                    if (isEdgeExecutable(block, s)) {
                        flowWorklist.push(block);   // Push both this block and successor to worklist?
                        flowWorklist.push(s);
                    }
                }
            }
            else if (instruction.definesVar() || instruction instanceof Instruction.Phi) {
                var def = instruction instanceof Instruction.Phi phi ? phi.value() : instruction.def();
                // Push all uses (instructions) of the def into the worklist
                SSAEdges.SSADef ssaDef = ssaEdges.get(def);
                if (ssaDef != null) {
                    for (Instruction use : ssaDef.useList) {
                        if (visited.get(use.block.bid))
                            // Don't visit the instruction if block hasn't been
                            // visited
                            instructionWorkList.push(use);
                    }
                }
            }
        }
    }

    private void init(CompiledFunction function) {
        this.function = function;
        ssaEdges = SSAEdges.buildDefUseChains(function);
        valueLattice = new ValueLattice();
        flowEdges = new HashMap<>();
        for (BasicBlock block : function.getBlocks()) {
            for (BasicBlock s : block.successors) {
                flowEdges.put(new FlowEdge(block, s), false);
            }
        }
        instructionWorkList = new WorkList<>();
        flowWorklist = new WorkList<>();
        flowWorklist.push(function.entry);
        visited = new BitSet();
    }

    public SparseConditionalConstantPropagation apply(EnumSet<Options> options) {
        /*
        The constant propagation algorithm does not change the flow graph - it computes
        information about the flow graph. The compiler now uses this information to improve
        the graph in the following ways:

        * The instructions corresponding to temporaries that evaluate as constants are modified
        to be load constant instructions.

        • An edge that has not become executable is eliminated, and the conditional branching
        instruction representing that edge is modified to be a simpler instruction.
        The phi-nodes at the head of the edge are modified to have one less operand.

        • Blocks that become unreachable are eliminated.

         Bob Morgan. Building an Optimizing Compiler
         */
        if (options.contains(Options.DUMP_SCCP_PREAPPLY)) {
            System.out.println("SCCP analysis\n");
            System.out.println(toString());
        }
        markExecutableBlocks();
        removeBranchesThatAreNotExecutable();
        replaceVarsWithConstants();
        // Unreachable blocks are eliminated as there are no paths to them
        if (options.contains(Options.DUMP_SCCP_POSTAPPLY)) function.dumpIR(false, "Post SCCP\n");
        return this;
    }

    private void markExecutableBlocks() {
        var blocks = function.getBlocks();
        executableBlocks = new BitSet(blocks.size());
        executableBlocks.set(function.entry.bid);
        for (FlowEdge edge: flowEdges.keySet()) {
            if (flowEdges.get(edge)) {
                executableBlocks.set(edge.source.bid);
                executableBlocks.set(edge.target.bid);
            }
        }
    }

    /**
     * Where we know which branch will be executed on a CBR,
     * we replace such a branch with a jump to the known
     * basic block
     */
    private void removeBranchesThatAreNotExecutable() {
        for (var flowEdge : flowEdges.keySet()) {
            if (!flowEdges.get(flowEdge)) {
                if (executableBlocks.get(flowEdge.source.bid) ||
                    executableBlocks.get(flowEdge.target.bid))
                    removeEdge(flowEdge.source, flowEdge.target);
            }
        }
    }

    private void removeEdge(BasicBlock source, BasicBlock target) {
        int j = target.whichPred(source);
        // Replace cbr with jump
        int idx = source.instructions.size()-1;
        Instruction instruction = source.instructions.get(idx);
        if (instruction instanceof Instruction.ConditionalBranch cbr) {
            BasicBlock remainingExecutableBlock = (cbr.falseBlock == target) ? cbr.trueBlock : cbr.falseBlock;
            source.update(idx, new Instruction.Jump(remainingExecutableBlock));
        }
        // Remove phis in target corresponding to the input
        for (var phi: target.phis()) {
            phi.removeInput(j);
        }
        // update cfg
        source.removeSuccessor(target);
    }

    /**
     * Where a definition is known to be a constant,
     * replace all uses with the constant and then delete
     * the defining instruction.
     */
    private void replaceVarsWithConstants() {
        for (var register: valueLattice.getRegisters()) {
            var latticeElement = valueLattice.get(register);
            if (latticeElement.isReplaceableConstant()) {
                var constant = asOperand(latticeElement, register.type);
                var defUseChain = this.ssaEdges.get(register);
                if (defUseChain == null)
                    continue;
                // replace uses with constant
                for (var usingInstruction: defUseChain.useList) {
                    if (executableBlocks.get(usingInstruction.block.bid))
                        usingInstruction.replaceUseWithConstant(register, constant);
                }
                defUseChain.useList.clear();
                var block = defUseChain.instruction.block;
                // delete defining instruction
                block.deleteInstruction(defUseChain.instruction);
                ssaEdges.remove(register);
            }
        }
    }

    // A CFG edge
    static final class FlowEdge {
        BasicBlock source;
        BasicBlock target;

        public FlowEdge(BasicBlock source, BasicBlock target) {
            this.target = target;
            this.source = source;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlowEdge that = (FlowEdge) o;
            return (source.bid == that.source.bid) && (target.bid == that.target.bid);
        }

        @Override
        public int hashCode() {
            return source.bid + target.bid;
        }

        @Override
        public String toString() {
            return "L"+source.bid+"->L"+target.bid;
        }
    }

    /**
     * This is based on the description of CP_Evaluate(I) in
     * Building an Optimizing Compiler. It evaluates an instruction and
     * if the instruction defines an SSA variable, then it updates the lattice
     * value of that variable. If the lattice changes then this returns true,
     * else false. For branches the change in executable status of an edge is
     * used instead of the lattice value change.
     */
    private boolean evalInstruction(Instruction instruction) {
        BasicBlock block = instruction.block;
        assert block != null;
        boolean changed = false;
        switch (instruction) {
            case Instruction.Ret retInst -> {
                // TODO is this correct?
            }
            case Instruction.Move moveInst -> {
                if (moveInst.to() instanceof Operand.RegisterOperand toReg) {
                    var cell = valueLattice.get(toReg.reg);
                    if (moveInst.from() instanceof Operand.RegisterOperand fromReg) {
                        changed = cell.meet(valueLattice.get(fromReg.reg));
                    } else if (isConstantOperand(moveInst.from())) {
                        changed = cell.meet(factFromOperand(moveInst.from()));
                    } else throw new IllegalStateException();
                } else throw new IllegalStateException();
            }
            case Instruction.Jump jumpInst -> changed = markEdgeExecutable(block, jumpInst.jumpTo);
            case Instruction.ConditionalBranch cbrInst -> {
                LatticeElement condition;
                if (cbrInst.condition() instanceof Operand.RegisterOperand registerOperand)
                    condition = valueLattice.get(registerOperand.reg);
                else if (isConstantOperand(cbrInst.condition()))
                    condition = factFromOperand(cbrInst.condition());
                else
                    throw new IllegalStateException();

                if (condition.isFloat() || condition.isReference())
                    throw new CompilerException("Condition expression must be Int type");
                if (condition.isFalse())
                    changed = markEdgeExecutable(block, cbrInst.falseBlock);
                else if (condition.isTrue())
                    changed = markEdgeExecutable(block, cbrInst.trueBlock);
                else if (condition.kind == F_INT_BOTTOM || condition.kind == F_BOTTOM) {
                    boolean changed0 = markEdgeExecutable(block, cbrInst.trueBlock);
                    boolean changed1 = markEdgeExecutable(block, cbrInst.falseBlock);
                    changed = changed0 || changed1;
                }
            }
            case Instruction.Call callInst -> {
                if (!(callInst.callee.returnType instanceof EZType.EZTypeVoid)) {
                    var cell = valueLattice.get(callInst.returnOperand().reg);
                    changed = cell.meetWithTypeBottom(callInst.returnOperand().reg.type);
                }
            }
            case Instruction.Unary unaryInst -> {
                var cell = valueLattice.get(unaryInst.result().reg);
                LatticeElement input = factFromOperandOrRegister(unaryInst.operand());
                changed = input == null ? cell.meetWithTypeBottom(unaryInst.result().reg.type) : evalUnary(cell, input, unaryInst.unop, unaryInst.result().reg.type);
            }
            case Instruction.Binary binaryInst -> {
                var cell = valueLattice.get(binaryInst.result().reg);
                LatticeElement left = factFromOperandOrRegister(binaryInst.left());
                LatticeElement right = factFromOperandOrRegister(binaryInst.right());
                if (left != null && right != null) {
                    switch (binaryInst.binOp) {
                        case "+", "-", "*", "/", "%" -> changed = evalArith(cell, left, right, binaryInst.binOp, binaryInst.result().reg.type);
                        case "==", "!=", "<", ">", "<=", ">=" -> changed = evalLogical(cell, left, right, binaryInst.binOp, binaryInst.result().reg.type);
                        default -> throw new IllegalStateException();
                    }
                }
                else {
                    changed = cell.meetWithTypeBottom(binaryInst.result().reg.type);
                }
            }
            case Instruction.NewArray newArrayInst -> {
                var cell = valueLattice.get(newArrayInst.destOperand().reg);
                changed = cell.meet(new LatticeElement(F_REF_NOT_NULL));
            }
            case Instruction.NewStruct newStructInst -> {
                var cell = valueLattice.get(newStructInst.destOperand().reg);
                changed = cell.meet(new LatticeElement(F_REF_NOT_NULL));
            }
            case Instruction.ArrayStore arrayStoreInst -> {
            }
            case Instruction.ArrayLoad arrayLoadInst -> {
                var cell = valueLattice.get(arrayLoadInst.destOperand().reg);
                changed = cell.meetWithTypeBottom(arrayLoadType(arrayLoadInst));
            }
            case Instruction.SetField setFieldInst -> {
            }
            case Instruction.GetField getFieldInst -> {
                var cell = valueLattice.get(getFieldInst.destOperand().reg);
                changed = cell.meetWithTypeBottom(fieldLoadType(getFieldInst));
            }
            case Instruction.ArgInstruction argInst -> {
                var cell = valueLattice.get(argInst.def());
                changed = cell.meetWithTypeBottom(argInst.def().type);
            }
            case Instruction.Phi phiInst -> changed = visitPhi(block, phiInst);
            default -> throw new IllegalStateException("Unexpected value: " + instruction);
        }
        return changed;
    }

    private boolean visitPhi(BasicBlock block, Instruction.Phi phiInst) {
        LatticeElement oldValue = valueLattice.get(phiInst.value());
        LatticeElement newValue = new LatticeElement(F_TOP);
        for (int j = 0; j < block.predecessors.size(); j++) {
            BasicBlock pred = block.predecessors.get(j);
            // We ignore non-executable edges
            if (isEdgeExecutable(pred, block)) {
                if (phiInst.isRegisterInput(j)) {
                    LatticeElement varValue = valueLattice.get(phiInst.inputAsRegister(j));
                    newValue.meet(varValue);
                }
                else if (isConstantOperand(phiInst.input(j))) {
                    newValue.meet(factFromOperand(phiInst.input(j)));
                }
            }
        }
        return oldValue.meet(newValue);
    }

    private boolean isEdgeExecutable(BasicBlock source, BasicBlock target) {
        return flowEdges.get(new FlowEdge(source, target));
    }
    private boolean markEdgeExecutable(BasicBlock source, BasicBlock target) {
        var edge = new FlowEdge(source, target);
        var oldValue = flowEdges.get(edge);
        assert oldValue != null;
        if (!oldValue) {
            // Mark edge as executable
            flowEdges.put(edge, true);
            return true;
        }
        return false;
    }

    private LatticeElement factFromOperandOrRegister(Operand operand) {
        if (operand instanceof Operand.RegisterOperand registerOperand)
            return valueLattice.get(registerOperand.reg);
        if (isConstantOperand(operand))
            return factFromOperand(operand);
        return null;
    }

    private static boolean isConstantOperand(Operand operand) {
        return operand instanceof Operand.IntConstantOperand ||
                operand instanceof Operand.FloatConstantOperand ||
                operand instanceof Operand.NullConstantOperand;
    }

    private static LatticeElement factFromOperand(Operand operand) {
        if (operand instanceof Operand.IntConstantOperand constantOperand)
            return new LatticeElement(constantOperand.value);
        if (operand instanceof Operand.FloatConstantOperand constantOperand)
            return new LatticeElement(constantOperand.value);
        if (operand instanceof Operand.NullConstantOperand)
            return new LatticeElement(F_REF_NULL);
        throw new IllegalStateException("Unexpected constant operand: " + operand);
    }

    private static Operand asOperand(LatticeElement element, EZType type) {
        if (element.kind == F_INT_ZERO) return new Operand.IntConstantOperand(0, type);
        if (element.kind == F_INT_NONZERO_CONST) return new Operand.IntConstantOperand(element.intValue, type);
        if (element.kind == F_FLT_CONST) return new Operand.FloatConstantOperand(element.floatValue, type);
        if (element.kind == F_REF_NULL) return new Operand.NullConstantOperand(type);
        throw new IllegalStateException("Lattice value is not a constant: " + element);
    }
    private static EZType arrayLoadType(Instruction.ArrayLoad arrayLoadInst) {
        EZType type = aggregateBaseType(operandType(arrayLoadInst.arrayOperand()));
        if (type instanceof EZType.EZTypeArray arrayType)
            return arrayType.getElementType();
        return arrayLoadInst.destOperand().reg.type;
    }

    private static EZType fieldLoadType(Instruction.GetField getFieldInst) {
        EZType type = aggregateBaseType(operandType(getFieldInst.structOperand()));
        if (type instanceof EZType.EZTypeStruct structType) {
            EZType fieldType = structType.getField(getFieldInst.fieldName);
            if (fieldType != null)
                return fieldType;
        }
        return getFieldInst.destOperand().reg.type;
    }

    private static EZType aggregateBaseType(EZType type) {
        if (type instanceof EZType.EZTypeNullable nullable)
            return nullable.baseType;
        return type;
    }

    private static EZType operandType(Operand operand) {
        if (operand instanceof Operand.RegisterOperand registerOperand)
            return registerOperand.reg.type;
        return operand.type;
    }

    private static boolean evalUnary(LatticeElement cell, LatticeElement input, String unOp, EZType resultType) {
        if (input.isIntegerConstant()) {
            long value = input.kind == F_INT_ZERO ? 0 : input.intValue;
            if (unOp.equals("-"))
                return cell.meet(new LatticeElement(-value));
            if (unOp.equals("!"))
                return cell.meet(new LatticeElement(value == 0 ? 1L : 0L));
        }
        if (input.isFloatConstant() && unOp.equals("-"))
            return cell.meet(new LatticeElement(-input.floatValue));
        if (input.kind == F_TOP || input.kind == F_INT_TOP || input.kind == F_FLT_TOP)
            return false;
        return cell.meetWithTypeBottom(resultType);
    }

    private static boolean evalLogical(LatticeElement cell, LatticeElement left, LatticeElement right, String binOp, EZType resultType) {
        if (left.isIntegerConstant() && right.isIntegerConstant()) {
            long leftValue = left.kind == F_INT_ZERO ? 0 : left.intValue;
            long rightValue = right.kind == F_INT_ZERO ? 0 : right.intValue;
            long result = switch (binOp) {
                case "==" -> leftValue == rightValue ? 1 : 0;
                case "!=" -> leftValue != rightValue ? 1 : 0;
                case "<" -> leftValue < rightValue ? 1 : 0;
                case ">" -> leftValue > rightValue ? 1 : 0;
                case "<=" -> leftValue <= rightValue ? 1 : 0;
                case ">=" -> leftValue >= rightValue ? 1 : 0;
                default -> throw new IllegalStateException();
            };
            return cell.meet(new LatticeElement(result));
        }
        if (left.isFloatConstant() && right.isFloatConstant()) {
            double leftValue = left.floatValue;
            double rightValue = right.floatValue;
            long result = switch (binOp) {
                case "==" -> leftValue == rightValue ? 1 : 0;
                case "!=" -> leftValue != rightValue ? 1 : 0;
                case "<" -> leftValue < rightValue ? 1 : 0;
                case ">" -> leftValue > rightValue ? 1 : 0;
                case "<=" -> leftValue <= rightValue ? 1 : 0;
                case ">=" -> leftValue >= rightValue ? 1 : 0;
                default -> throw new IllegalStateException();
            };
            return cell.meet(new LatticeElement(result));
        }
        // Reference equality/inequality where both operands are definite
        // (null or not-null). Two distinct not-null pointers are undecidable,
        // so only fold when at least one side is known null.
        if (left.isDefiniteReference() && right.isDefiniteReference() &&
                (left.isNullConstant() || right.isNullConstant())) {
            boolean equal = left.isNullConstant() && right.isNullConstant();
            long result = switch (binOp) {
                case "==" -> equal ? 1 : 0;
                case "!=" -> equal ? 0 : 1;
                default -> throw new IllegalStateException();
            };
            return cell.meet(new LatticeElement(result));
        }
        if (left.kind == F_TOP || right.kind == F_TOP ||
                left.kind == F_INT_TOP || right.kind == F_INT_TOP ||
                left.kind == F_FLT_TOP || right.kind == F_FLT_TOP ||
                left.kind == F_REF_TOP || right.kind == F_REF_TOP)
            return false;
        return cell.meetWithTypeBottom(resultType);
    }

    private static boolean evalArith(LatticeElement cell, LatticeElement left, LatticeElement right, String binOp, EZType resultType) {
        if (left.isIntegerConstant() && right.isIntegerConstant()) {
            long leftValue = left.kind == F_INT_ZERO ? 0 : left.intValue;
            long rightValue = right.kind == F_INT_ZERO ? 0 : right.intValue;
            long result = switch (binOp) {
                case "+" -> leftValue + rightValue;
                case "-" -> leftValue - rightValue;
                case "/" -> {
                    if (rightValue == 0) throw new CompilerException("Division by zero");
                    yield leftValue / rightValue;
                }
                case "*" -> leftValue * rightValue;
                case "%" -> {
                    if (rightValue == 0) throw new CompilerException("Division by zero");
                    yield leftValue % rightValue;
                }
                default -> throw new IllegalStateException();
            };
            return cell.meet(new LatticeElement(result));
        }
        if (left.isFloatConstant() && right.isFloatConstant()) {
            double leftValue = left.floatValue;
            double rightValue = right.floatValue;
            double result = switch (binOp) {
                case "+" -> leftValue + rightValue;
                case "-" -> leftValue - rightValue;
                case "/" -> leftValue / rightValue;
                case "*" -> leftValue * rightValue;
                default -> throw new IllegalStateException();
            };
            return cell.meet(new LatticeElement(result));
        }
        if (binOp.equals("*") &&
                ((left.isIntegerConstant() && left.kind == F_INT_ZERO) ||
                 (right.isIntegerConstant() && right.kind == F_INT_ZERO))) {
            return cell.meet(new LatticeElement(0L));
        }
        if (left.kind == F_TOP || right.kind == F_TOP ||
                left.kind == F_INT_TOP || right.kind == F_INT_TOP ||
                left.kind == F_FLT_TOP || right.kind == F_FLT_TOP)
            return false;
        return cell.meetWithTypeBottom(resultType);
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Flow edges:\n");
        for (var edge : flowEdges.keySet()) {
            if (flowEdges.get(edge)) {
                sb.append(edge).append("=Executable").append("\n");
            }
            else {
                sb.append(edge).append("=NOT Executable").append("\n");
            }
        }
        sb.append("Lattices:\n");
        for (var register: valueLattice.getRegisters()) {
            sb.append(register.name()).append("=").append(valueLattice.get(register)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Maintains a Lattice for each SSA variable - i.e register
     * Initial value of lattice is TOP/Undefined
     */
    static final class ValueLattice {

        private final Map<Register, LatticeElement> valueLattice = new HashMap<>();

        LatticeElement get(Register reg) {
            var cell = valueLattice.get(reg);
            if (cell == null) {
                cell = factTopFromType(reg.type);
                valueLattice.put(reg, cell);
            }
            return cell;
        }
        Set<Register> getRegisters() {
            return valueLattice.keySet();
        }
    }

    static final class WorkList<E> {
        Set<E> members = new HashSet<>();
        List<E> list = new ArrayList<>();

        void push(E element) {
            if (members.add(element)) {
                list.add(element);
            }
        }

        E pop() {
            if (list.isEmpty()) return null;
            var x = list.removeFirst();
            members.remove(x);
            return x;
        }

        boolean isEmpty() {
            return list.isEmpty();
        }
    }
}
