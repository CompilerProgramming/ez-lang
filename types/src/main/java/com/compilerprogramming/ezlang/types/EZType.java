package com.compilerprogramming.ezlang.types;

import com.compilerprogramming.ezlang.exceptions.CompilerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Currently, we support Int, Float, Struct, and Array of Int/Float/Struct.
 * Arrays and Structs are reference types.
 */
public abstract class EZType {

    // Type classes
    static final byte TVOID = 0;
    static final byte TUNKNOWN = 1;
    static final byte TNULL = 2;
    static final byte TINT = 3;      // Int, Bool
    static final byte TFLOAT = 4;    // Float
    static final byte TNULLABLE = 5; // Null, or not null ptr
    static final byte TFUNC = 6;     // Function types
    static final byte TSTRUCT = 7;
    static final byte TARRAY = 8;

    public final byte tclass;    // type class
    public final String name;      // type name, always unique

    protected EZType(byte tclass, String name) {
        this.tclass = tclass;
        this.name = name;
    }

    public boolean isPrimitive() { return false; }
    public String describe() { return toString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EZType type = (EZType) o;
        return tclass == type.tclass && Objects.equals(name, type.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tclass, name);
    }

    @Override
    public String toString() {
        return name;
    }
    public String name() { return name; }

    /**
     * Can we assign a value of other type to a var of this type?
     */
    public boolean isAssignable(EZType other) {
        if (other == null || other instanceof EZTypeVoid || other instanceof EZTypeUnknown)
            return false;
        if (this == other || equals(other)) return true;
        if (this instanceof EZTypeNullable nullable) {
            // if this is Nullable and other is null then okay
            if (other instanceof EZTypeNull)
                return true;
            // if this is Nullable and other is compatible with base type then okay
            return nullable.baseType.isAssignable(other);
        }
        else if (other instanceof EZTypeNullable nullable) {
            // At compile time we allow nullable value to be
            // assigned to base type, but null check must be inserted
            // Optimizer may remove null check
            return isAssignable(nullable.baseType);
        }
        return false;
    }

    /**
     * Represents no type - useful for defining functions
     * that do not return a value
     */
    public static class EZTypeVoid extends EZType {
        public EZTypeVoid() {
            super(TVOID, "$Void");
        }
    }

    public static class EZTypeUnknown extends EZType {
        public EZTypeUnknown() {
            super(TUNKNOWN, "$Unknown");
        }
    }

    public static class EZTypeNull extends EZType {
        public EZTypeNull() { super(TNULL, "$Null"); }
    }

    public static class EZTypeInteger extends EZType {

        public EZTypeInteger() {
            super (TINT, "Int");
        }
        @Override
        public boolean isPrimitive() {
            return true;
        }
    }

    public static class EZTypeFloat extends EZType {
        public EZTypeFloat() {
            super(TFLOAT, "Float");
        }
        @Override
        public boolean isPrimitive() {
            return true;
        }
    }

    public static class EZTypeStruct extends EZType {
        ArrayList<String> fieldNames = new ArrayList<>();
        ArrayList<EZType> fieldTypes = new ArrayList<>();
        public boolean pending = true;

        public EZTypeStruct(String name) {
            super(TSTRUCT, name);
        }
        public void addField(String name, EZType type) {
            // FIXME attach line info to type
            if (!pending)
                throw new CompilerException("Cannot add field to an already defined struct",-1);
            if (fieldNames.contains(name))
                throw new CompilerException("Field " + name + " already exists in struct " + this.name,-1);
            if (type == null)
                throw new CompilerException("Cannot a field with null type",-1);
            fieldNames.add(name);
            fieldTypes.add(type);
        }
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("struct ").append(name()).append("{");
            for (int i = 0; i < fieldNames.size(); i++) {
                String fieldName = fieldNames.get(i);
                EZType fieldType = fieldTypes.get(i);
                sb.append(fieldName).append(": ").append(fieldType.name()).append(";");
            }
            sb.append("}");
            return sb.toString();
        }
        public EZType getField(String name) {
            int index = fieldNames.indexOf(name);
            if (index < 0)
                return null;
            return fieldTypes.get(index);
        }
        public int getFieldIndex(String name) {
            return  fieldNames.indexOf(name);
        }
        public int numFields() { return fieldNames.size(); }
        public String getFieldName(int index) { return fieldNames.get(index); }
        public void complete() { pending = false; }
    }

    public static class EZTypeArray extends EZType {
        EZType elementType;

        public EZTypeArray(EZType baseType) {
            super(TARRAY, "[" + baseType.name() + "]");
            this.elementType = baseType;
            if (baseType instanceof EZTypeArray)
                throw new CompilerException("Array of array type not supported",-1);
        }
        public EZType getElementType() {
            return elementType;
        }
    }

    // This is really a dedicated Union type for T|Null.
    public static class EZTypeNullable extends EZType {
        public final EZType baseType;
        public EZTypeNullable(EZType baseType) {
            super(TNULLABLE, baseType.name()+"?");
            this.baseType = baseType;
        }
    }

    public static class EZTypeFunction extends EZType {
        public final List<Symbol> args = new ArrayList<>();
        public EZType returnType;
        public Object code;
        public EZTypeFunction(String name) {
            super(TFUNC, name);
        }
        public void addArg(Symbol arg) {
            args.add(arg);
        }
        public void setReturnType(EZType returnType) {
            this.returnType = returnType;
        }
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("func ").append(name()).append("(");
            boolean first = true;
            for (Symbol arg: args) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(arg.name).append(": ").append(arg.type.name());
            }
            sb.append(")");
            if (!(returnType instanceof EZTypeVoid)) {
                sb.append("->").append(returnType.name());
            }
            return sb.toString();
        }
    }
}
