package com.compilerprogramming.ezlang.types;

/**
 * A symbol is something that has a name and a type.
 */
public abstract class Symbol {

    public final String name;
    public Type type;

    protected Symbol(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public static class TypeSymbol extends Symbol {
        public TypeSymbol(String name, Type type) {
            super(name, type);
        }
    }

    public static class FunctionTypeSymbol extends Symbol {
        public final Object functionDecl;
        public Object code;
        public FunctionTypeSymbol(String name, Type.TypeFunction type, Object functionDecl) {
            super(name, type);
            this.functionDecl = functionDecl;
        }
    }

    public static class VarSymbol extends Symbol {
        // Values assigned by bytecode compiler
        public int reg;
        public VarSymbol(String name, Type type) {
            super(name, type);
        }
    }
}
