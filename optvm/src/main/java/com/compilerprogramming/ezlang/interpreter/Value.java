package com.compilerprogramming.ezlang.interpreter;

import com.compilerprogramming.ezlang.types.EZType;

import java.util.ArrayList;

public class Value {
    static public class IntegerValue extends Value {
        public IntegerValue(long value) {
            this.value = value;
        }
        public final long value;
    }
    static public class FloatValue extends Value {
        public FloatValue(double value) {
            this.value = value;
        }
        public final double value;
    }
    static public class NullValue extends Value {
        public NullValue() {}
    }
    static public class ArrayValue extends Value {
        public final EZType.EZTypeArray arrayType;
        public final ArrayList<Value> values;
        public ArrayValue(EZType.EZTypeArray arrayType, long len, Value initValue) {
            this.arrayType = arrayType;
            values = new ArrayList<>();
            for (long i = 0; i < len; i++) {
                values.add(initValue);
            }
        }
    }
    static public class StructValue extends Value {
        public final EZType.EZTypeStruct structType;
        public final Value[] fields;
        public StructValue(EZType.EZTypeStruct structType) {
            this.structType = structType;
            this.fields = new Value[structType.numFields()];
        }
    }
}
