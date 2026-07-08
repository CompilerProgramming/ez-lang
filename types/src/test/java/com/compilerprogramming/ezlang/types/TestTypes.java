package com.compilerprogramming.ezlang.types;

import org.junit.Assert;
import org.junit.Test;

public class TestTypes {

    EZType buildStruct1(TypeDictionary typeDictionary) {
        EZType.EZTypeStruct s = new EZType.EZTypeStruct("S1");
        s.addField("a", typeDictionary.INT);
        s.addField("b", typeDictionary.INT);
        s.complete();
        return typeDictionary.intern(s);
    }

    EZType buildStruct2(TypeDictionary typeDictionary) {
        EZType.EZTypeStruct s = new EZType.EZTypeStruct("S2");
        s.addField("a", typeDictionary.INT);
        s.addField("b", typeDictionary.INT);
        s.complete();
        return typeDictionary.intern(s);
    }

    @Test
    public void testTypes() {
        var typeDict = new TypeDictionary();

        var tint = typeDict.INT;
        Assert.assertTrue(tint.isPrimitive());

        var s1 = buildStruct1(typeDict);
        var s2 = buildStruct2(typeDict);

        var s1Type = typeDict.localLookup("S1").type;
        var s2Type = typeDict.localLookup("S2").type;

        Assert.assertTrue(s1.isAssignable(s1Type));
        Assert.assertFalse(tint.isAssignable(s1Type));
        Assert.assertFalse(s1.isAssignable(s2Type));

        Assert.assertTrue(s2.isAssignable(s2Type));
        Assert.assertFalse(s2.isAssignable(s1Type));

        var s1TypeDup = buildStruct1(typeDict);
        Assert.assertSame(s1Type, s1TypeDup);

        var nullType = typeDict.NULL;
        var nullableS1Type = typeDict.merge(s1Type,nullType);
        Assert.assertTrue(nullableS1Type.isAssignable(nullType));
        Assert.assertTrue(nullableS1Type.isAssignable(s1Type));
        Assert.assertFalse(nullableS1Type.isAssignable(s2Type));
    }

    @Test
    public void testFloatTypes() {
        var typeDict = new TypeDictionary();
        Assert.assertTrue(typeDict.FLOAT.isPrimitive());
        Assert.assertTrue(typeDict.FLOAT.isAssignable(typeDict.FLOAT));
        Assert.assertFalse(typeDict.FLOAT.isAssignable(typeDict.INT));
        var floatArray = typeDict.makeArrayType(typeDict.FLOAT, false);
        Assert.assertEquals("[Float]", floatArray.name());
    }
}
