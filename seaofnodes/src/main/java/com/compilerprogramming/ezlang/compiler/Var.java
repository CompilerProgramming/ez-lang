package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.compiler.type.*;

/**
 *  The tracked fields are now complex enough to deserve a array-of-structs layout
 */
public class Var {

    public final String _name;   // Declared name
    // These fields are not final for forward reference late updates or
    // promotions.
    public int _idx;             // index in containing scope
    private Type _type;          // Declared type
    public boolean _final;       // Final field
    public boolean _fref;        // Forward ref

    public Var(int idx, String name, Type type, boolean xfinal) {
        this(idx,name,type,xfinal,false);
    }
    public Var(int idx, String name, Type type, boolean xfinal, boolean fref) {
        _idx = idx;
        _name = name;
        _type = type;
        _final = xfinal;
        _fref = fref;
    }
    public Type type() {
        assert !_type.isFRef();
        return _type;
    }

    // Forward reference variables (not types) must be BOTTOM and
    // distinct from inferred variables
    public boolean isFRef() { return _fref; }

    public void defFRef(Type type, boolean xfinal) {
        assert isFRef() && xfinal;
        _type = type;
        _final = true;
        _fref = false;
    }

    @Override public String toString() {
        return _type.toString()+(_final ? " ": " !")+_name;
    }
}
