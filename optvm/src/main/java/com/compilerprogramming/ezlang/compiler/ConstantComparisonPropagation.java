package com.compilerprogramming.ezlang.compiler;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;

/**
 * The goal of this pass is to detect conditional branching based
 * on comparison with a constant. Example
 *
 * <pre>
 *     if (i == 5)
 *     {
 *         // some use of i
 *     }
 * </pre>
 *
 * SCCP generates a lattice value for i, and if i changes outside the if block, then
 * it is not classed as a constant. This means that inside the if block, we cannot exploit
 * the knowledge that i is a constant locally.
 *
 * To enable this, we need to detect such comparisons and then insert a temp
 * variable which is set to the constant value. The new temp variable is inserted
 * inside the if block only and does not affect the meaning of i outside the if block.
 * After this transformation we can run SCCP again to take advantage of the local
 * knowledge.
 *
 * This technique can be extended to null checks too, but we do not do that yet.
 */
public class ConstantComparisonPropagation {

    private final CompiledFunction function;
    private DominatorTree domTree;
    private Map<Register, SSAEdges.SSADef> ssaDefUse;
    private boolean updated = false;

    public ConstantComparisonPropagation(CompiledFunction function) {
        this.function = function;
    }

    public boolean apply(EnumSet<Options> options) {
        if (options.contains(Options.CCP)) {
            updated = false;
            domTree = new DominatorTree(function.entry);
            ssaDefUse = SSAEdges.buildDefUseChains(function);
            walkBlocks();
        }
        return updated;
    }

    private void walkBlocks() {
        walkBlock(function.entry);
    }

    private void walkBlock(BasicBlock block) {
        propagateConstantsInComparisons(block);
        for (BasicBlock c : block.dominatedChildren) {
            walkBlock(c);
        }
    }

    private void propagateConstantsInComparisons(BasicBlock block) {
        if (block == function.exit) return;
        // Get terminal instruction
        Instruction instruction = block.instructions.getLast();
        if (instruction instanceof Instruction.ConditionalBranch cbr) {
            if (cbr.condition() instanceof Operand.RegisterOperand conditionVar) {
                SSAEdges.SSADef defUse = ssaDefUse.get(conditionVar.reg);
                // If the condition var was result of == with constant value
                if (defUse.instruction.block.bid == block.bid
                        && defUse.instruction instanceof Instruction.Binary binary
                        && binary.binOp.equals("==")) {
                    // Get the constant and the register operands
                    // from the binary
                    Operand.IntConstantOperand constantOp = null;
                    Operand.RegisterOperand registerOp = null;
                    if (binary.left() instanceof Operand.IntConstantOperand leftConstant
                            && binary.right() instanceof Operand.RegisterOperand rightReg) {
                        constantOp = leftConstant;
                        registerOp = rightReg;
                    } else if (binary.right() instanceof Operand.IntConstantOperand rightConstant
                            && binary.left() instanceof Operand.RegisterOperand leftReg) {
                        constantOp = rightConstant;
                        registerOp = leftReg;
                    }
                    if (constantOp != null) {
                        // Since reg is constant in true branch
                        // We can replace all uses of reg with the constant
                        // in blocks dominated by the true block
                        BasicBlock trueBlock = cbr.trueBlock;
                        // I am not sure if this scenario can occur  but
                        // for safety we check that the register we will
                        // replace is not immediately used inside the true block
                        // in a phi, because we intend to add the definition of
                        // the replacement after any phis
                        if (!checkUsedInPhi(trueBlock, registerOp.reg)) {
                            // Create a temp and move constant to it.
                            // We insert the new instruction at the top of the True Block,
                            // where it should dominate all uses of it
                            // We could just replace with a  constant here instead of creating
                            // a temp and a move instruction but that would be less general a solution as it
                            // would not work for other scenarios such as null status which we will
                            // be adding in future
                            var replacementRegister = function.registerPool.newTempReg(registerOp.reg.type);
                            var defInst = new Instruction.Move(constantOp, new Operand.TempRegisterOperand(replacementRegister));
                            insertAtBeginning(trueBlock, defInst);
                            var replacementRegisterUses = SSAEdges.addDef(ssaDefUse, replacementRegister, defInst); // Update SSA Def Use chains, add def for new reg
                            Iterator<Instruction> useIter = ssaDefUse.get(registerOp.reg).useList.iterator();
                            while (useIter.hasNext()) {
                                Instruction use = useIter.next();
                                if (trueBlock.dominates(use.block)) {
                                    use.replaceUse(registerOp.reg, replacementRegister);
                                    // Update SSA Def use chains
                                    useIter.remove(); // No longer a use of old register
                                    replacementRegisterUses.addUse(use); // Use of new temp register
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /* Check if the register is used in a phi instruction within the block */
    private static boolean checkUsedInPhi(BasicBlock block, Register register) {
        for (Instruction instruction : block.instructions) {
            if (instruction instanceof Instruction.Phi phi) {
                for (int i = 0; i < phi.numInputs(); i++) {
                    if (phi.isRegisterInput(i)) {
                        Register phiUse = phi.inputAsRegister(i);
                        if (phiUse.equals(register)) return true;
                    }
                }
            }
            else break;
        }
        return false;
    }

    /* Insert instruction at the start of BB after any phis */
    private static void insertAtBeginning(BasicBlock block, Instruction instruction) {
        int pos = 0; // insertion point
        for (; pos < block.instructions.size(); pos++) {
            if (!(block.instructions.get(pos) instanceof Instruction.Phi))
                break;
        }
        if (pos == block.instructions.size())
            throw new IllegalStateException();
        block.add(pos, instruction);
    }
}
