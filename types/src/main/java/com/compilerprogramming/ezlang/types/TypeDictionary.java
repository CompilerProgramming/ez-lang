package com.compilerprogramming.ezlang.types;

import com.compilerprogramming.ezlang.exceptions.CompilerException;

public final class TypeDictionary extends Scope {
    public final EZType.EZTypeUnknown UNKNOWN;
    public final EZType.EZTypeInteger INT;
    public final EZType.EZTypeFloat FLOAT;
    public final EZType.EZTypeNull NULL;
    public final EZType.EZTypeVoid VOID;

    public TypeDictionary() {
        super(null);
        INT = (EZType.EZTypeInteger) intern(new EZType.EZTypeInteger());
        FLOAT = (EZType.EZTypeFloat) intern(new EZType.EZTypeFloat());
        UNKNOWN = (EZType.EZTypeUnknown) intern(new EZType.EZTypeUnknown());
        NULL = (EZType.EZTypeNull) intern(new EZType.EZTypeNull());
        VOID = (EZType.EZTypeVoid) intern(new EZType.EZTypeVoid());
    }
    public EZType makeArrayType(EZType elementType, boolean isNullable) {
        switch (elementType) {
            case EZType.EZTypeInteger ti -> {
                var arrayType = intern(new EZType.EZTypeArray(ti));
                return isNullable ? intern(new EZType.EZTypeNullable(arrayType)) : arrayType;
            }
            case EZType.EZTypeFloat tf -> {
                var arrayType = intern(new EZType.EZTypeArray(tf));
                return isNullable ? intern(new EZType.EZTypeNullable(arrayType)) : arrayType;
            }
            case EZType.EZTypeStruct ts -> {
                var arrayType = intern(new EZType.EZTypeArray(ts));
                return isNullable ? intern(new EZType.EZTypeNullable(arrayType)) : arrayType;
            }
            case EZType.EZTypeNullable nullable when nullable.baseType instanceof EZType.EZTypeStruct -> {
                var arrayType = intern(new EZType.EZTypeArray(elementType));
                return isNullable ? intern(new EZType.EZTypeNullable(arrayType)) : arrayType;
            }
            case null, default -> throw new CompilerException("Unsupported array element type: " + elementType,-1);
        }
    }
    public EZType intern(EZType type) {
        Symbol symbol = lookup(type.name());
        if (symbol != null) return symbol.type;
        return install(type.name(), new Symbol.TypeSymbol(type.name(), type)).type;
    }
    public EZType merge(EZType t1, EZType t2) {
        if (t1 instanceof EZType.EZTypeNull && t2 instanceof EZType.EZTypeStruct) {
            return intern(new EZType.EZTypeNullable(t2));
        }
        else if (t2 instanceof EZType.EZTypeNull && t1 instanceof EZType.EZTypeStruct) {
            return intern(new EZType.EZTypeNullable(t1));
        }
        else if (t1 instanceof EZType.EZTypeArray && t2 instanceof EZType.EZTypeNull) {
            return intern(new EZType.EZTypeNullable(t1));
        }
        else if (t2 instanceof EZType.EZTypeArray && t1 instanceof EZType.EZTypeNull) {
            return intern(new EZType.EZTypeNullable(t2));
        }
        else if (t1 instanceof EZType.EZTypeUnknown)
            return t2;
        else if (t2 instanceof EZType.EZTypeUnknown)
            return t1;
        else if (!t1.equals(t2))
            throw new CompilerException("Unsupported merge type: " + t1 + " and " + t2, -1);
        return t1;
    }
}
