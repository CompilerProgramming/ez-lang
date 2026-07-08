package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.types.EZType;

public class Operand {

    EZType type;

    public static class IntConstantOperand extends Operand {
        public final long value;
        public IntConstantOperand(long value, EZType type) {
            this.value = value;
            this.type = type;
        }
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static class FloatConstantOperand extends Operand {
        public final double value;
        public FloatConstantOperand(double value, EZType type) {
            this.value = value;
            this.type = type;
        }
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
    public static class NullConstantOperand extends Operand {
        public NullConstantOperand(EZType type) {
            this.type = type;
        }
        @Override
        public String toString() {
            return "null";
        }
    }

    public static abstract class RegisterOperand extends Operand {
        public final int regnum;
        public RegisterOperand(int regnum) {
            this.regnum = regnum;
        }
        public int frameSlot() { return regnum; }
    }

    public static class LocalRegisterOperand extends RegisterOperand {
        public final String varName;
        public LocalRegisterOperand(int regnum, String varName) {
            super(regnum);
            this.varName = varName;
        }
        @Override
        public String toString() {
            return varName;
        }
    }

    public static class LocalFunctionOperand extends Operand {
        public final EZType.EZTypeFunction functionType;
        public LocalFunctionOperand(EZType.EZTypeFunction functionType) {
            this.functionType = functionType;
        }
        @Override
        public String toString() {
            return functionType.toString();
        }
    }

    /**
     * Represents a temp register, maps to a location on the
     * virtual stack. Temps start at offset 0, but this is a relative
     * register number from start of temp area.
     */
    public static class TempRegisterOperand extends RegisterOperand {
        public TempRegisterOperand(int regnum, EZType type) {
            super(regnum);
            this.type = type;
        }
        @Override
        public String toString() {
            return "%t" + regnum;
        }
    }

    public static class IndexedOperand extends Operand {}

    public static class LoadIndexedOperand extends IndexedOperand {
        public final Operand arrayOperand;
        public final Operand indexOperand;
        public LoadIndexedOperand(Operand arrayOperand, Operand indexOperand) {
            this.arrayOperand = arrayOperand;
            this.indexOperand = indexOperand;
            assert !(indexOperand instanceof IndexedOperand) &&
                    !(arrayOperand instanceof IndexedOperand);
        }
        @Override
        public String toString() {
            return arrayOperand + "[" + indexOperand + "]";
        }
    }

    public static class LoadFieldOperand extends IndexedOperand {
        public final Operand structOperand;
        public final int fieldIndex;
        public final String fieldName;
        public LoadFieldOperand(Operand structOperand, String fieldName, int field) {
            this.structOperand = structOperand;
            this.fieldName = fieldName;
            this.fieldIndex = field;
            assert !(structOperand instanceof IndexedOperand);
        }

        @Override
        public String toString() {
            return structOperand + "." + fieldName;
        }
    }
}
