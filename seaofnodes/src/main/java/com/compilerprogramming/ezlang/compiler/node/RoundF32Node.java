package com.compilerprogramming.ezlang.compiler.node;

import com.compilerprogramming.ezlang.compiler.type.Type;
import com.compilerprogramming.ezlang.compiler.type.TypeFloat;

import java.util.BitSet;

public class RoundF32Node extends Node {
    public RoundF32Node(Node lhs) { super(null, lhs); }

    @Override public String label() { return "RoundF32"; }

    @Override public String glabel() { return "(f32)"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append("((f32)"), visited).append(")");
    }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeFloat i0 && i0.isConstant() )
            return TypeFloat.constant((float)i0.value());
        if( in(1)._type==TypeFloat.F64 ) return TypeFloat.F32;
        return in(1)._type;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Type t1 = lhs._type;

        // RoundF32 of float
        if( t1 instanceof TypeFloat tf && tf._sz==32 )
            return lhs;

        return null;
    }
}
