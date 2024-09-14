package com.compilerprogramming.ezlang.bytecode;

public abstract class Instruction {

    public boolean isTerminal() {
        return false;
    }
    @Override
    public String toString() {
        return toStr(new StringBuilder()).toString();
    }

    public static class Move extends Instruction {
        public final Operand from;
        public final Operand to;
        public Move(Operand from, Operand to) {
            this.from = from;
            this.to = to;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(to).append(" = ").append(from);
        }
    }

    public static class UnaryInstruction extends Instruction {
        public final String unop;
        public final Operand result;
        public final Operand operand;
        public UnaryInstruction(String unop, Operand result, Operand operand) {
            this.unop = unop;
            this.result = result;
            this.operand = operand;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(result).append(" = ").append(unop).append(operand);
        }
    }

    public static class BinaryInstruction extends Instruction {
        public final String binOp;
        public final Operand result;
        public final Operand left;
        public final Operand right;
        public BinaryInstruction(String binop, Operand result, Operand left, Operand right) {
            this.binOp = binop;
            this.result = result;
            this.left = left;
            this.right = right;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(result).append(" = ").append(left).append(binOp).append(right);
        }
    }

    public static class AStoreAppend extends Instruction {
        public final Operand array;
        public final Operand value;
        public AStoreAppend(Operand array, Operand value) {
            this.array = array;
            this.value = value;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(array).append(".append(").append(value).append(")");
        }
    }

    public static class ConditionalBranch extends Instruction {
        public final Operand condition;
        public final BasicBlock trueBlock;
        public final BasicBlock falseBlock;
        public ConditionalBranch(BasicBlock currentBlock, Operand condition, BasicBlock trueBlock, BasicBlock falseBlock) {
            this.condition = condition;
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
            return sb.append("if ").append(condition).append(" goto L").append(trueBlock.bid).append(" else goto L").append(falseBlock.bid);
        }
    }

    public static class Call extends Instruction {
        public final Operand callee;
        public final Operand[] args;
        public Call(Operand callee, Operand... args) {
            this.callee = callee;
            this.args = args;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("call ").append(callee);
            if (args.length > 0)
                sb.append(" params ");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(args[i]);
            }
            return sb;
        }
    }

    public static class Jump extends Instruction {
        public final BasicBlock jumpTo;
        public Jump(BasicBlock jumpTo) {
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

    public abstract StringBuilder toStr(StringBuilder sb);
}
