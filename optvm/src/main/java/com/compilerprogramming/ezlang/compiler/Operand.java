package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;

import java.util.Objects;

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntConstantOperand that = (IntConstantOperand) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FloatConstantOperand that = (FloatConstantOperand) o;
            return Double.compare(value, that.value) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
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

    public static class RegisterOperand extends Operand {
        final Register reg;
        protected RegisterOperand(Register reg) {
            this.reg = reg;
            if (reg == null)
                throw new NullPointerException();
        }
        public int frameSlot() { return reg.frameSlot(); }

        public RegisterOperand copy(Register register) {
           return new RegisterOperand(register);
        }

        @Override
        public String toString() {
            return reg.name();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegisterOperand operand = (RegisterOperand) o;
            return Objects.equals(reg, operand.reg);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(reg);
        }
    }

    public static class LocalRegisterOperand extends RegisterOperand {
        Symbol.VarSymbol variable;
        public LocalRegisterOperand(Register reg, Symbol.VarSymbol variable) {
            super(reg);
            this.variable = variable;
        }
        @Override
        public RegisterOperand copy(Register register) {
            return new LocalRegisterOperand(register, variable);
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
        public TempRegisterOperand(Register reg) {
            super(reg);
        }
        @Override
        public RegisterOperand copy(Register register) {
            return new TempRegisterOperand(register);
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
