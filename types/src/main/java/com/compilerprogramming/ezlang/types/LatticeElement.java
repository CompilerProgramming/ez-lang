package com.compilerprogramming.ezlang.types;

import com.compilerprogramming.ezlang.exceptions.CompilerException;

import java.util.Objects;

/**
 * Shared lattice fact for value/nullability analyses.
 */
public final class LatticeElement {
    public static final byte F_TOP = 0;             // no usable fact yet

    public static final byte F_REF_TOP = 1;         // known reference type, value unknown
    public static final byte F_REF_NOT_NULL = 2;
    public static final byte F_REF_NULL = 3;
    public static final byte F_REF_BOTTOM = 4;      // maybe null

    public static final byte F_INT_TOP = 5;         // known int type, value unknown
    public static final byte F_INT_ZERO = 6;
    public static final byte F_INT_NONZERO_CONST = 7;
    public static final byte F_INT_NONZERO_VARYING = 8;
    public static final byte F_INT_BOTTOM = 9;      // may be zero or non-zero

    public static final byte F_FLT_TOP = 10;        // known float type, value unknown
    public static final byte F_FLT_CONST = 11;
    public static final byte F_FLT_BOTTOM = 12;     // non-const float

    public static final byte F_BOTTOM = 13;         // impossible / contradiction

    public byte kind;
    public long intValue;
    public double floatValue;

    public LatticeElement(byte kind) {
        this.kind = kind;
    }

    public LatticeElement(byte kind, long value) {
        this.kind = kind;
        this.intValue = value;
    }

    public LatticeElement(long value) {
        kind = F_INT_TOP;
        setIntValue(value);
    }

    public LatticeElement(double value) {
        kind = F_FLT_TOP;
        setFloatValue(value);
    }

    public LatticeElement copy() {
        var copy = new LatticeElement(kind, intValue);
        copy.floatValue = floatValue;
        return copy;
    }

    public void copyFrom(LatticeElement other) {
        kind = other.kind;
        intValue = other.intValue;
        floatValue = other.floatValue;
    }

    public boolean isTrue() {
        return kind == F_INT_NONZERO_CONST || kind == F_INT_NONZERO_VARYING;
    }

    public boolean isFalse() {
        return kind == F_INT_ZERO;
    }

    public boolean isIntegerConstant() {
        return kind == F_INT_ZERO || kind == F_INT_NONZERO_CONST;
    }

    public boolean isFloatConstant() {
        return kind == F_FLT_CONST;
    }

    public boolean isNullConstant() {
        return kind == F_REF_NULL;
    }

    public boolean isDefiniteReference() {
        return kind == F_REF_NULL || kind == F_REF_NOT_NULL;
    }

    public boolean isReplaceableConstant() {
        return isIntegerConstant() || isFloatConstant() || isNullConstant();
    }

    public boolean setIntValue(long value) {
        byte oldKind = kind;
        long oldInt = intValue;
        double oldFloat = floatValue;
        if (kind == F_TOP || kind == F_INT_TOP) {
            intValue = value;
            kind = value == 0 ? F_INT_ZERO : F_INT_NONZERO_CONST;
        }
        else if (kind == F_INT_ZERO && value != 0) {
            kind = F_INT_BOTTOM;
        }
        else if (kind == F_INT_NONZERO_CONST && (value == 0 || value != intValue)) {
            kind = F_INT_BOTTOM;
        }
        else if (!isInteger()) {
            kind = F_BOTTOM;
        }
        return changed(oldKind, oldInt, oldFloat);
    }

    public boolean setFloatValue(double value) {
        byte oldKind = kind;
        long oldInt = intValue;
        double oldFloat = floatValue;
        if (kind == F_TOP || kind == F_FLT_TOP) {
            floatValue = value;
            kind = F_FLT_CONST;
        }
        else if (kind == F_FLT_CONST && Double.compare(value, floatValue) != 0) {
            kind = F_FLT_BOTTOM;
        }
        else if (!isFloat()) {
            kind = F_BOTTOM;
        }
        return changed(oldKind, oldInt, oldFloat);
    }

    public boolean meetWithTypeBottom(EZType type) {
        return meet(factBottomFromType(type));
    }

    public boolean meet(LatticeElement other) {
        byte oldKind = kind;
        long oldInt = intValue;
        double oldFloat = floatValue;

        if (kind == F_TOP) {
            copyFrom(other);
            return changed(oldKind, oldInt, oldFloat);
        }
        if (kind == F_BOTTOM || other.kind == F_TOP)
            return false;
        if (other.kind == F_BOTTOM) {
            kind = F_BOTTOM;
            return changed(oldKind, oldInt, oldFloat);
        }
        if (kind == other.kind) {
            if (kind == F_INT_NONZERO_CONST && intValue != other.intValue)
                kind = F_INT_NONZERO_VARYING;
            else if (kind == F_FLT_CONST && Double.compare(floatValue, other.floatValue) != 0)
                kind = F_FLT_BOTTOM;
            return changed(oldKind, oldInt, oldFloat);
        }
        if (isReference(kind) && isReference(other.kind)) {
            kind = meetReference(other);
            return changed(oldKind, oldInt, oldFloat);
        }
        if (isInteger(kind) && isInteger(other.kind)) {
            meetInteger(other);
            return changed(oldKind, oldInt, oldFloat);
        }
        if (isFloat(kind) && isFloat(other.kind)) {
            meetFloat(other);
            return changed(oldKind, oldInt, oldFloat);
        }
        kind = F_BOTTOM;
        return changed(oldKind, oldInt, oldFloat);
    }

    private boolean changed(byte oldKind, long oldInt, double oldFloat) {
        return kind != oldKind || intValue != oldInt || Double.compare(floatValue, oldFloat) != 0;
    }

    private byte meetReference(LatticeElement other) {
        if (kind == F_REF_TOP) return other.kind;
        if (other.kind == F_REF_TOP) return kind;
        return switch (kind) {
            case F_REF_NULL -> other.kind == F_REF_NULL ? F_REF_NULL : F_REF_BOTTOM;
            case F_REF_NOT_NULL -> other.kind == F_REF_NOT_NULL ? F_REF_NOT_NULL : F_REF_BOTTOM;
            case F_REF_BOTTOM -> F_REF_BOTTOM;
            default -> F_BOTTOM;
        };
    }

    private void meetInteger(LatticeElement other) {
        if (kind == F_INT_TOP) {
            copyFrom(other);
            return;
        }
        if (other.kind == F_INT_TOP)
            return;
        switch (kind) {
            case F_INT_ZERO -> {
                if (other.kind != F_INT_ZERO)
                    kind = F_INT_BOTTOM;
            }
            case F_INT_NONZERO_CONST -> {
                switch (other.kind) {
                    case F_INT_NONZERO_CONST -> {
                        if (intValue != other.intValue)
                            kind = F_INT_NONZERO_VARYING;
                    }
                    case F_INT_NONZERO_VARYING -> kind = F_INT_NONZERO_VARYING;
                    case F_INT_ZERO, F_INT_BOTTOM -> kind = F_INT_BOTTOM;
                }
            }
            case F_INT_NONZERO_VARYING -> {
                if (other.kind == F_INT_ZERO || other.kind == F_INT_BOTTOM)
                    kind = F_INT_BOTTOM;
            }
            case F_INT_BOTTOM -> {
            }
        }
    }

    private void meetFloat(LatticeElement other) {
        if (kind == F_FLT_TOP) {
            copyFrom(other);
            return;
        }
        if (other.kind == F_FLT_TOP)
            return;
        if (kind == F_FLT_CONST &&
                (other.kind == F_FLT_BOTTOM ||
                 (other.kind == F_FLT_CONST && Double.compare(floatValue, other.floatValue) != 0))) {
            kind = F_FLT_BOTTOM;
        }
    }

    public boolean isTop() {
        return kind == F_TOP;
    }

    public boolean isBottom() {
        return kind == F_BOTTOM;
    }

    public boolean isReference() {
        return isReference(kind);
    }

    public boolean isInteger() {
        return isInteger(kind);
    }

    public boolean isFloat() {
        return isFloat(kind);
    }

    public boolean isNull() {
        return kind == F_REF_NULL;
    }

    public boolean isNotNull() {
        return kind == F_REF_NOT_NULL;
    }

    public boolean isMaybeNull() {
        return kind == F_REF_BOTTOM;
    }

    public boolean isZero() {
        return kind == F_INT_ZERO;
    }

    public boolean isNonZero() {
        return kind == F_INT_NONZERO_CONST || kind == F_INT_NONZERO_VARYING;
    }

    public static boolean isReference(byte kind) {
        return kind >= F_REF_TOP && kind <= F_REF_BOTTOM;
    }

    public static boolean isInteger(byte kind) {
        return kind >= F_INT_TOP && kind <= F_INT_BOTTOM;
    }

    public static boolean isFloat(byte kind) {
        return kind >= F_FLT_TOP && kind <= F_FLT_BOTTOM;
    }

    public static LatticeElement factTopFromType(EZType type) {
        if (type instanceof EZType.EZTypeInteger)
            return new LatticeElement(F_INT_TOP);
        if (type instanceof EZType.EZTypeFloat)
            return new LatticeElement(F_FLT_TOP);
        if (isReferenceType(type))
            return new LatticeElement(F_REF_TOP);
        return new LatticeElement(F_TOP);
    }

    public static LatticeElement factBottomFromType(EZType type) {
        if (type instanceof EZType.EZTypeInteger)
            return new LatticeElement(F_INT_BOTTOM);
        if (type instanceof EZType.EZTypeFloat)
            return new LatticeElement(F_FLT_BOTTOM);
        if (type instanceof EZType.EZTypeNull)
            return new LatticeElement(F_REF_NULL);
        if (isReferenceType(type))
            return new LatticeElement(F_REF_BOTTOM);
        return new LatticeElement(F_BOTTOM);
    }

    public static LatticeElement factFromType(EZType type) {
        if (type != null) {
            if (type instanceof EZType.EZTypeNullable)
                return new LatticeElement(F_REF_BOTTOM);
            if (type instanceof EZType.EZTypeNull)
                return new LatticeElement(F_REF_NULL);
            if (type instanceof EZType.EZTypeArray || type instanceof EZType.EZTypeStruct)
                return new LatticeElement(F_REF_NOT_NULL);
            if (type instanceof EZType.EZTypeInteger)
                return new LatticeElement(F_INT_BOTTOM);
            if (type instanceof EZType.EZTypeFloat)
                return new LatticeElement(F_FLT_BOTTOM);
        }
        return new LatticeElement(F_BOTTOM);
    }

    public static boolean isReferenceType(EZType type) {
        return type instanceof EZType.EZTypeNullable ||
                type instanceof EZType.EZTypeArray ||
                type instanceof EZType.EZTypeStruct ||
                type instanceof EZType.EZTypeNull;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case F_TOP -> "T";
            case F_REF_TOP -> "ref";
            case F_INT_TOP -> "int";
            case F_FLT_TOP -> "flt";
            case F_REF_NOT_NULL -> "not-null";
            case F_REF_NULL -> "null";
            case F_REF_BOTTOM -> "maybe-null";
            case F_INT_BOTTOM -> "int*";
            case F_FLT_BOTTOM -> "flt*";
            case F_INT_ZERO -> "0";
            case F_INT_NONZERO_CONST -> Long.toString(intValue);
            case F_INT_NONZERO_VARYING -> "non-zero";
            case F_FLT_CONST -> Double.toString(floatValue);
            case F_BOTTOM -> "⊥";
            default -> throw new CompilerException("Unknown lattice kind: " + kind);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LatticeElement that = (LatticeElement) o;
        return kind == that.kind && intValue == that.intValue && Double.compare(floatValue, that.floatValue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, intValue, floatValue);
    }
}