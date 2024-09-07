package com.compilerprogramming.ezlang.bytecode;


import com.compilerprogramming.ezlang.types.Type;

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
    public static final int STORE_APPEND = 13; // array append
    public static final int STORE_INDEXED = 14;
    public static final int CALL = 15;
    public static final int STORE = 16;
    public static final int CBR = 17;
    public static final int JUMP = 18;
    public static final int POP = 19;
    public static final int EQ = 20;
    public static final int NE = 21;
    public static final int LT = 22;
    public static final int GT = 23;
    public static final int LE = 24;
    public static final int GE = 25;

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
        "storeappend", // array append
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
        "ge"
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

    public static class PushConst extends Instruction {
        public final int value;
        public PushConst(int value) {
            super(PUSH_I);
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
        public Type.TypeFunction functionType;
        public LoadFunction(Type.TypeFunction typeFunction) {
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
        public final Type type;
        public New(Type type) {
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

    public static class StoreAppend extends Instruction {
        public StoreAppend() {
            super(STORE_APPEND);
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
