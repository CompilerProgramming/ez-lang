package com.compilerprogramming.ezlang.bytecode;

import com.compilerprogramming.ezlang.types.Type;

public class Operand {

    public static class ConstantOperand extends Operand {
        public final long value;
        public ConstantOperand(long value) {
            this.value = value;
        }
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static class LocalRegisterOperand extends Operand {
        public final int regnum;
        public LocalRegisterOperand(int regnum) {
            this.regnum = regnum;
        }
        @Override
        public String toString() {
            return "Local{" + regnum + '}';
        }
    }

    public static class LocalFunctionOperand extends Operand {
        public final Type.TypeFunction functionType;
        public LocalFunctionOperand(Type.TypeFunction functionType) {
            this.functionType = functionType;
        }
        @Override
        public String toString() {
            return functionType.toString();
        }
    }

    /**
     * Represents the return register, which is the location where
     * the caller will expect to see any return value. The VM must map
     * this to appropriate location.
     */
    public static class ReturnRegisterOperand extends Operand {
        public ReturnRegisterOperand() {}
        @Override
        public String toString() { return "RET"; }
    }

    /**
     * Represents a temp register, maps to a location on the
     * virtual stack. Temps start at offset 0, but this is a relative
     * register number from start of temp area.
     */
    public static class TempRegisterOperand extends Operand {
        public final int regnum;
        public TempRegisterOperand(int regnum) {
            this.regnum = regnum;
        }
        @Override
        public String toString() {
            return "T" + regnum;
        }
    }

    public static class LoadIndexedOperand extends Operand {
        public final Operand arrayOperand;
        public final Operand indexOperand;
        public LoadIndexedOperand(Operand arrayOperand, Operand indexOperand) {
            this.arrayOperand = arrayOperand;
            this.indexOperand = indexOperand;
        }
        @Override
        public String toString() {
            return arrayOperand + "[" + indexOperand + "]";
        }
    }

    public static class LoadFieldOperand extends Operand {
        public final Operand structOperand;
        public final int fieldIndex;
        public final String fieldName;
        public LoadFieldOperand(Operand structOperand, String fieldName, int field) {
            this.structOperand = structOperand;
            this.fieldName = fieldName;
            this.fieldIndex = field;
        }

        @Override
        public String toString() {
            return structOperand + "." + fieldName + "(" + fieldIndex + ")";
        }
    }

    public static class NewTypeOperand extends Operand {
        public final Type type;
        public NewTypeOperand(Type type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "New(" + type + ")";
        }
    }

}
