package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestSemaAssignTypes {

    @Test
    public void test1() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a+b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test51() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 1+1;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test2() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a-b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test52() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 1-1;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }


    @Test
    public void test3() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a*b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test53() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4*2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test4() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a/b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test54() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4/2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }


    @Test
    public void test5() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a==b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }


    @Test
    public void test55() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4==2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test6() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a!=b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test56() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4!=2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }


    @Test
    public void test7() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a<b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }


    @Test
    public void test57() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4<2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test8() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a<=b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test58() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4<=2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test9() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a>b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test59() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4>2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test10() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a>=b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test60() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4>=2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test11() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a&&b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test61() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4&&2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test12() {
        Parser parser = new Parser();
        String src = """
    func foo(a: Int, b: Int)->Int 
    {
       return a||b;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo(a: Int,b: Int)->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test62() {
        Parser parser = new Parser();
        String src = """
    func foo()->Int 
    {
       return 4||2;
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Int", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

    @Test
    public void test13() {
        Parser parser = new Parser();
        String src = """
    struct Foo
    {
        var bar: [Int]
    }
    func foo()->Foo 
    {
       var f: Foo
       f = new Foo{}
       return f.bar[0]
    }
""";
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("foo");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func foo()->Foo", symbol.type.describe());
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
    }

}
