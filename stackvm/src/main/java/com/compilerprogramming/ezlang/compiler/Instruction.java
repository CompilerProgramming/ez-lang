package com.compilerprogramming.ezlang.compiler;


import com.compilerprogramming.ezlang.types.EZType;

public class Instruction {

    public static final int RET = 0;
    public static final int PUSH_I = 1;
    public static final int ADD_I = 2;
    public static final int SUB_I = 3;
    public static final int MUL_I = 4;
    public static final int DIV_I = 5;
    public static final int MOD_I = 6;
    public static final int NEG_I = 7;
    public static final int NOT = 8;
    public static final int LOAD_FUNC = 9;
    public static final int LOAD_VAR = 10;
    public static final int NEW = 11;
    public static final int LOAD_INDEXED = 12;
    public static final int STORE_INDEXED = 13;
    public static final int CALL = 14;
    public static final int STORE = 15;
    public static final int CBR = 16;
    public static final int JUMP = 17;
    public static final int POP = 18;
    public static final int EQ = 19;
    public static final int NE = 20;
    public static final int LT = 21;
    public static final int GT = 22;
    public static final int LE = 23;
    public static final int GE = 24;
    public static final int PUSH_F = 25;
    public static final int ADD_F = 26;
    public static final int SUB_F = 27;
    public static final int MUL_F = 28;
    public static final int DIV_F = 29;
    public static final int NEG_F = 30;

    static final String[] opNames = {
        "ret",
        "pushi",
        "addi",
        "subi",
        "muli",
        "divi",
        "modi",
        "negi",
        "not",
        "loadfunc",
        "load",
        "new",
        "loadindexed",
        "storeindexed",
        "call",
        "store",
        "cbr",
        "jump",
        "pop",
        "eq",
        "neq",
        "lt",
        "gt",
        "le",
        "ge",
        "pushf",
        "addf",
        "subf",
        "mulf",
        "divf",
        "negf"
    };

    public final int opcode;

    protected Instruction(int opcode) {
        this.opcode = opcode;
    }

    public boolean isTerminal() {
        return false;
    }

    public StringBuilder toStr(StringBuilder sb) {
        return sb.append(opNames[opcode]);
    }

    public static class pushIntConstant extends Instruction {
        public final int value;
        public pushIntConstant(int value) {
            super(PUSH_I);
            this.value = value;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(value);
        }
    }

    public static class PushFloatConst extends Instruction {
        public final double value;
        public PushFloatConst(double value) {
            super(PUSH_F);
            this.value = value;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(value);
        }
    }
    public static class BinaryOp extends Instruction {
        public BinaryOp(int opcode) {
            super(opcode);
        }
    }

    public static class UnaryOp extends Instruction {
        public UnaryOp(int opcode) {
            super(opcode);
        }
    }

    public static class LoadFunction extends Instruction {
        public EZType.EZTypeFunction functionType;
        public LoadFunction(EZType.EZTypeFunction typeFunction) {
            super(LOAD_FUNC);
            this.functionType = typeFunction;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(functionType.name);
        }
    }

    public static class LoadVar extends Instruction {
        public final int reg;
        public LoadVar(int reg) {
            super(LOAD_VAR);
            this.reg = reg;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(reg);
        }
    }

    public static class New extends Instruction {
        public final EZType type;
        public New(EZType type) {
            super(NEW);
            this.type = type;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(type.name);
        }
    }

    public static class LoadIndexed extends Instruction {
        public LoadIndexed() {
            super(LOAD_INDEXED);
        }
    }

    public static class StoreIndexed extends Instruction {
        public StoreIndexed() {
            super(STORE_INDEXED);
        }
    }

    public static class Call extends Instruction {
        public final int argc;
        public Call(int argCount) {
            super(CALL);
            this.argc = argCount;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(argc);
        }
    }

    public static class Store extends Instruction {
        public final int reg;
        public Store(int reg) {
            super(STORE);
            this.reg = reg;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" ").append(reg);
        }
    }

    public static class ConditionalBranch extends Instruction {
        public final BasicBlock trueBlock;
        public final BasicBlock falseBlock;
        public ConditionalBranch(BasicBlock currentBlock, BasicBlock trueBlock, BasicBlock falseBlock) {
            super(CBR);
            this.trueBlock = trueBlock;
            this.falseBlock = falseBlock;
            currentBlock.addSuccessor(trueBlock);
            currentBlock.addSuccessor(falseBlock);
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" L").append(trueBlock.bid).append(" L").append(falseBlock.bid);
        }
    }

    public static class Jump extends Instruction {
        public final BasicBlock jumpTo;
        public Jump(BasicBlock jumpTo) {
            super(JUMP);
            this.jumpTo = jumpTo;
        }
        @Override
        public boolean isTerminal() {
            return true;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append(" L").append(jumpTo.bid);
        }
    }

    public static class Pop extends Instruction {
        public Pop() {
            super(POP);
        }
    }
}
