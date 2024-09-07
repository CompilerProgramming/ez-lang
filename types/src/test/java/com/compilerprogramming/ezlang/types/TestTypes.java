package com.compilerprogramming.ezlang.types;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class TestTypes {

    Type buildStruct1(TypeDictionary typeDictionary) {
        Type.TypeStruct s = new Type.TypeStruct("S1");
        s.addField("a", typeDictionary.INT);
        s.addField("b", typeDictionary.INT);
        s.complete();
        return typeDictionary.intern(s);
    }

    Type buildStruct2(TypeDictionary typeDictionary) {
        Type.TypeStruct s = new Type.TypeStruct("S2");
        s.addField("a", typeDictionary.INT);
        s.addField("b", typeDictionary.INT);
        s.complete();
        return typeDictionary.intern(s);
    }

}
