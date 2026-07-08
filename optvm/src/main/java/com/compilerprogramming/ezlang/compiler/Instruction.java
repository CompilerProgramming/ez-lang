package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.types.EZType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Instruction {

    static final int I_NOOP = 0;
    static final int I_MOVE = 1;
    static final int I_RET  = 2;
    static final int I_UNARY = 3;
    static final int I_BINARY = 4;
    static final int I_BR = 5;
    static final int I_CBR = 6;
    static final int I_ARG = 7;
    static final int I_CALL = 8;
    static final int I_PHI = 9;
    static final int I_NEW_ARRAY = 10;
    static final int I_NEW_STRUCT = 11;
    static final int I_ARRAY_STORE = 12;
    static final int I_ARRAY_LOAD = 13;
    static final int I_FIELD_GET = 14;
    static final int I_FIELD_SET = 15;
    static final int I_PARALLEL_COPY = 16;

    public final int opcode;
    protected Operand.RegisterOperand def;
    protected Operand[] uses;
    public BasicBlock block;

    protected Instruction(int opcode) {
        this.opcode = opcode;
        this.def = null;
        this.uses = new Operand[0];
    }
    protected Instruction(int opcode, Operand.RegisterOperand def, Operand... uses) {
        this.opcode = opcode;
        this.def = def;
        this.uses = new Operand[uses.length];
        System.arraycopy(uses, 0, this.uses, 0, uses.length);
    }

    public boolean isTerminal() { return false; }
    @Override
    public String toString() {
        return toStr(new StringBuilder()).toString();
    }

    /**
     * Does this instruction define a var?
     */
    public boolean definesVar() { return def != null; }
    /**
     * If the instruction defines a var then return the Register else null
     */
    public Register def() { return def != null ? def.reg: null; }
    public void replaceDef(Register newDef) {
        if (def == null) throw new IllegalStateException();
        def = def.copy(newDef);
    }

    /**
     * Get the registers used by this instruction. Non register operands are
     * not included.
     */
    public List<Register> uses() {
        List<Register> useList = null;
        for (int i = 0; i < uses.length; i++) {
            Operand operand = uses[i];
            if (operand != null && operand instanceof Operand.RegisterOperand registerOperand) {
                if (useList == null) useList = new ArrayList<>();
                useList.add(registerOperand.reg);
            }
        }
        if (useList == null) useList = Collections.emptyList();
        return useList;
    }

    /**
     * Replaces existing register uses with new ones. This api is not great
     * as it requires user to supply registers in the same order as returned by
     * the method {@link #uses()}.
     *
     * FIXME replace this with a better api
     */
    public void replaceUses(Register[] newUses) {
        int j = 0;
        for (int i = 0; i < uses.length; i++) {
            Operand use = uses[i];
            if (use != null && use instanceof Operand.RegisterOperand registerOperand) {
                uses[i] = registerOperand.copy(newUses[j++]);
            }
        }
        // Sanity check that we replaced the full set of registers
        if (j != newUses.length)
            throw new CompilerException("Error - supplied registers do not replace all uses");
    }

    /**
     * Replaces all occurrences of source with target in the uses list of the instruction
     */
    public boolean replaceUse(Register source, Register target) {
        boolean replaced = false;
        for (int i = 0; i < uses.length; i++) {
            Operand operand = uses[i];
            if (operand != null && operand instanceof Operand.RegisterOperand registerOperand && registerOperand.reg.id == source.id) {
                uses[i] = registerOperand.copy(target);
                replaced = true;
            }
        }
        return replaced;
    }

    /**
     * Replaces all uses of given register with the constant operand
     */
    public void replaceUseWithConstant(Register register, Operand constantOperand) {
        for (int i = 0; i < uses.length; i++) {
            Operand operand = uses[i];
            if (operand != null && operand instanceof Operand.RegisterOperand registerOperand && registerOperand.reg.id == register.id) {
                uses[i] = constantOperand;
            }
        }
    }

    public static class Move extends Instruction {
        public Move(Operand from, Operand to) {
            super(I_MOVE, (Operand.RegisterOperand) to, from);
        }
        public Operand from() { return uses[0]; }
        public Operand.RegisterOperand to() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(to()).append(" = ").append(from());
        }
    }

    public static class NewArray extends Instruction {
        public final EZType.EZTypeArray type;
        public NewArray(EZType.EZTypeArray type, Operand.RegisterOperand destOperand) {
            super(I_NEW_ARRAY, destOperand);
            this.type = type;
        }
        public NewArray(EZType.EZTypeArray type, Operand.RegisterOperand destOperand, Operand len) {
            super(I_NEW_ARRAY, destOperand, len);
            this.type = type;
        }
        public NewArray(EZType.EZTypeArray type, Operand.RegisterOperand destOperand, Operand len, Operand initValue) {
            super(I_NEW_ARRAY, destOperand, len, initValue);
            this.type = type;
        }
        public Operand len() { return uses.length > 0 ? uses[0] : null; }
        public Operand initValue() { return uses.length > 1 ? uses[1] : null; }
        public Operand.RegisterOperand destOperand() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append(def)
                    .append(" = ")
                    .append("New(")
                    .append(type);
            if (len() != null)
                sb.append(", len=").append(len());
            if (initValue() != null)
                sb.append(", initValue=").append(initValue());
            return sb.append(")");
        }
    }

    public static class NewStruct extends Instruction {
        public final EZType.EZTypeStruct type;
        public NewStruct(EZType.EZTypeStruct type, Operand.RegisterOperand destOperand) {
            super(I_NEW_STRUCT, destOperand);
            this.type = type;
        }
        public Operand.RegisterOperand destOperand() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(def)
                    .append(" = ")
                    .append("New(")
                    .append(type)
                    .append(")");
        }
    }

    public static class ArrayLoad extends Instruction {
        public ArrayLoad(Operand.LoadIndexedOperand from, Operand.RegisterOperand to) {
            super(I_ARRAY_LOAD, to, from.arrayOperand, from.indexOperand);
        }
        public Operand arrayOperand() { return uses[0]; }
        public Operand indexOperand() { return uses[1]; }
        public Operand.RegisterOperand destOperand() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(destOperand())
                    .append(" = ")
                    .append(arrayOperand())
                    .append("[")
                    .append(indexOperand())
                    .append("]");
        }
    }

    public static class ArrayStore extends Instruction {
        public ArrayStore(Operand from, Operand.LoadIndexedOperand to) {
            super(I_ARRAY_STORE, (Operand.RegisterOperand) null, to.arrayOperand, to.indexOperand, from);
        }
        public Operand arrayOperand() { return uses[0]; }
        public Operand indexOperand() { return uses[1]; }
        public Operand sourceOperand() { return uses[2]; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb
                    .append(arrayOperand())
                    .append("[")
                    .append(indexOperand())
                    .append("] = ")
                    .append(sourceOperand());
        }
    }

    public static class GetField extends Instruction {
        public final String fieldName;
        public final int fieldIndex;
        public GetField(Operand.LoadFieldOperand from, Operand.RegisterOperand to) {
            super(I_FIELD_GET, to, from.structOperand);
            this.fieldName = from.fieldName;
            this.fieldIndex = from.fieldIndex;
        }
        public Operand structOperand() { return uses[0]; }
        public Operand.RegisterOperand destOperand() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(def)
                    .append(" = ")
                    .append(uses[0])
                    .append(".")
                    .append(fieldName);
        }
    }

    public static class SetField extends Instruction {
        public final String fieldName;
        public final int fieldIndex;
        public SetField(Operand from,Operand.LoadFieldOperand to) {
            super(I_FIELD_SET, (Operand.RegisterOperand) null, to.structOperand, from);
            this.fieldName = to.fieldName;
            this.fieldIndex = to.fieldIndex;
        }
        public Operand structOperand() { return uses[0]; }
        public Operand sourceOperand() { return uses[1]; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb
                    .append(structOperand())
                    .append(".")
                    .append(fieldName)
                    .append(" = ")
                    .append(sourceOperand());
        }
    }
    public static class Ret extends Instruction {
        public Ret(Operand value) {
            super(I_RET, (Operand.RegisterOperand) null, value);
        }
        public Operand value() { return uses[0]; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("ret");
            if (uses[0] != null)
                sb.append(" ").append(value());
            return sb;
        }
    }
    public static class Unary extends Instruction {
        public final String unop;
        public Unary(String unop, Operand.RegisterOperand result, Operand operand) {
            super(I_UNARY, result, operand);
            this.unop = unop;
        }
        public Operand.RegisterOperand result() { return def; }
        public Operand operand() { return uses[0]; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(result()).append(" = ").append(unop).append(operand());
        }
    }

    public static class Binary extends Instruction {
        public final String binOp;
        public Binary(String binop, Operand.RegisterOperand result, Operand left, Operand right) {
            super(I_BINARY, result, left, right);
            this.binOp = binop;
        }
        public Operand.RegisterOperand result() { return def; }
        public Operand left() { return uses[0]; }
        public Operand right() { return uses[1]; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(def).append(" = ").append(uses[0]).append(binOp).append(uses[1]);
        }
    }

    public static class ConditionalBranch extends Instruction {
        public final BasicBlock trueBlock;
        public final BasicBlock falseBlock;
        public ConditionalBranch(BasicBlock currentBlock, Operand condition, BasicBlock trueBlock, BasicBlock falseBlock) {
            super(I_CBR, (Operand.RegisterOperand) null, condition);
            this.trueBlock = trueBlock;
            this.falseBlock = falseBlock;
        }
        public Operand condition() { return uses[0]; }
        @Override
        public boolean isTerminal() {
            return true;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append("if ").append(condition()).append(" goto L").append(trueBlock.bid).append(" else goto L").append(falseBlock.bid);
        }
    }

    public static class Call extends Instruction {
        public final EZType.EZTypeFunction callee;
        public final int newbase;
        public Call(int newbase, Operand.RegisterOperand returnOperand, EZType.EZTypeFunction callee, Operand.RegisterOperand... args) {
            super(I_CALL, returnOperand, args);
            this.callee = callee;
            this.newbase = newbase;
        }
        public Operand.RegisterOperand returnOperand() { return def; }
        public Operand[] args() { return uses; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            if (def != null) {
                sb.append(def).append(" = ");
            }
            sb.append("call ").append(callee);
            if (uses.length > 0)
                sb.append(" params ");
            for (int i = 0; i < uses.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(uses[i]);
            }
            return sb;
        }
    }

    public static class Jump extends Instruction {
        public final BasicBlock jumpTo;
        public Jump(BasicBlock jumpTo) {
            super(I_BR);
            this.jumpTo = jumpTo;
        }
        @Override
        public boolean isTerminal() {
            return true;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append("goto ").append(" L").append(jumpTo.bid);
        }
    }

    /**
     * Phi does not generate uses or defs directly, instead
     * they are treated as a special case.
     * To avoid bugs we disable the standard interfaces
     */
    public static class Phi extends Instruction {
        private Register value;
        public Phi(Register value, List<Register> inputs) {
            super(I_PHI);
            this.value = value;
            this.uses = new Operand[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                this.uses[i] = new Operand.RegisterOperand(inputs.get(i));
            }
        }
        public void replaceInput(int i, Register newReg) {
            uses[i] =  new Operand.RegisterOperand(newReg);
        }
        /**
         * This will fail in input was replaced by a constant
         */
        public Register inputAsRegister(int i) {
            return ((Operand.RegisterOperand) uses[i]).reg;
        }
        public Operand input(int i) {
            return uses[i];
        }
        public int numInputs() { return uses.length; }
        public boolean isRegisterInput(int i) {
            return uses[i] instanceof Operand.RegisterOperand;
        }
        public Register[] inputRegisters() {
            return super.uses().toArray(new Register[super.uses().size()]);
        }
        @Override
        public Register def() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void replaceDef(Register newDef) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean definesVar() {
            return false;
        }
        @Override
        public List<Register> uses() {
            return Collections.emptyList();
        }
        @Override
        public void replaceUses(Register[] newUses) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean replaceUse(Register source, Register target) {
            throw new UnsupportedOperationException();
        }
        public Register value() {
            return value;
        }
        public void replaceValue(Register newReg) {
            this.value = newReg;
        }
        public void removeInput(int i) {
            var newUses = new Operand[uses.length - 1];
            System.arraycopy(uses, 0, newUses, 0, i);
            System.arraycopy(uses, i + 1, newUses, i, uses.length - i - 1);
            this.uses = newUses;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append(value().name()).append(" = phi(");
            for (int i = 0; i < uses.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(uses[i].toString());
            }
            sb.append(")");
            return sb;
        }
        public void addInput(Register register) {
            var newUses = new Operand[uses.length + 1];
            System.arraycopy(uses, 0, newUses, 0, uses.length);
            newUses[newUses.length-1] = new Operand.RegisterOperand(register);
            this.uses = newUses;
        }
        public boolean replaceInput(Register oldReg, Register newReg) {
            boolean replaced = false;
            for (int i = 0; i < numInputs(); i++) {
                if (isRegisterInput(i)) {
                    Register in = inputAsRegister(i);
                    if (in.equals(oldReg)) {
                        replaceInput(i, newReg);
                        replaced = true;
                    }
                }
            }
            return replaced;
        }
    }

    public static class ArgInstruction extends Instruction {
        public ArgInstruction(Operand.RegisterOperand arg) {
            super(I_ARG, arg);
        }
        public Operand.RegisterOperand arg() { return def; }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append("arg ").append(arg());
        }
    }

    /**
     * The parallel copy instruction is only used temporarily to exit
     * SSA form. It represents the parallel copy semantics of phi instructions.
     */
    public static class ParallelCopyInstruction extends Instruction {

        List<Operand> sourceOperands = new ArrayList<>();
        List<Operand.RegisterOperand> destOperands = new ArrayList<>();

        protected ParallelCopyInstruction() {
            super(I_PARALLEL_COPY);
        }

        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("(");
            for (int i = 0; i < destOperands.size(); i++) {
                if (i > 0)
                    sb.append(",");
                sb.append(destOperands.get(i));
            }
            sb.append(") = (");
            for (int i = 0; i < sourceOperands.size(); i++) {
                if (i > 0)
                    sb.append(",");
                sb.append(sourceOperands.get(i));
            }
            sb.append(")");
            return sb;
        }

        public void addCopy(Operand sourceOperand, Operand.RegisterOperand destOperand) {
            sourceOperands.add(sourceOperand);
            destOperands.add(destOperand);
        }

        @Override
        public Register def() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void replaceDef(Register newDef) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean definesVar() {
            return false;
        }
        @Override
        public List<Register> uses() {
            return Collections.emptyList();
        }
        @Override
        public void replaceUses(Register[] newUses) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean replaceUse(Register source, Register target) {
            throw new UnsupportedOperationException();
        }
    }

    public abstract StringBuilder toStr(StringBuilder sb);
}
