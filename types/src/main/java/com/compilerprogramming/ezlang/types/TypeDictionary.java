package com.compilerprogramming.ezlang.types;

import com.compilerprogramming.ezlang.exceptions.CompilerException;

public class TypeDictionary extends Scope {
    public final Type.TypeAny ANY;
    public final Type.TypeInteger INT;
    public final Type.TypeNull NULL;
    public final Type.TypeVoid VOID;

    public TypeDictionary() {
        super(null);
        INT = (Type.TypeInteger) intern(new Type.TypeInteger());
        ANY = (Type.TypeAny) intern(new Type.TypeAny());
        NULL = (Type.TypeNull) intern(new Type.TypeNull());
        VOID = (Type.TypeVoid) intern(new Type.TypeVoid());
    }
    public Type makeArrayType(Type elementType, boolean isNullable) {
        switch (elementType) {
            case Type.TypeInteger ti -> {
                var arrayType = intern(new Type.TypeArray(ti, INT));
                return isNullable ? intern(new Type.TypeNullable(arrayType)) : arrayType;
            }
            case Type.TypeStruct ts -> {
                var arrayType = intern(new Type.TypeArray(ts, INT));
                return isNullable ? intern(new Type.TypeNullable(arrayType)) : arrayType;
            }
            case Type.TypeNullable nullable when nullable.baseType instanceof Type.TypeStruct -> {
                var arrayType = intern(new Type.TypeArray(elementType, INT));
                return isNullable ? intern(new Type.TypeNullable(arrayType)) : arrayType;
            }
            case null, default -> throw new CompilerException("Unsupported array element type: " + elementType);
        }
    }
    public Type intern(Type type) {
        Symbol symbol = lookup(type.name());
        if (symbol != null) return symbol.type;
        return install(type.name(), new Symbol.TypeSymbol(type.name(), type)).type;
    }
    public Type merge(Type t1, Type t2) {
        if (t1 instanceof Type.TypeNull && t2 instanceof Type.TypeStruct) {
            return intern(new Type.TypeNullable(t2));
        }
        else if (t2 instanceof Type.TypeNull && t1 instanceof Type.TypeStruct) {
            return intern(new Type.TypeNullable(t1));
        }
        else if (t1 instanceof Type.TypeArray && t2 instanceof Type.TypeNull) {
            return intern(new Type.TypeNullable(t1));
        }
        else if (t2 instanceof Type.TypeArray && t1 instanceof Type.TypeNull) {
            return intern(new Type.TypeNullable(t2));
        }
        else if (t1 instanceof Type.TypeAny)
            return t2;
        else if (t2 instanceof Type.TypeAny)
            return t1;
        else if (!t1.equals(t2))
            throw new CompilerException("Unsupported merge type: " + t1 + " and " + t2);
        return t1;
    }
}
