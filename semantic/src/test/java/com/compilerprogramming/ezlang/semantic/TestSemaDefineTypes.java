package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestSemaDefineTypes {

    @Test
    public void test1() {
        Parser parser = new Parser();
        String src = """
struct Tree {
    var left: Tree?
    var right: Tree?
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("Tree");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct Tree{left: Tree?;right: Tree?;}", symbol.type.describe());
    }

    @Test
    public void test2() {
        Parser parser = new Parser();
        String src = """
struct Tree {
    var left: Tree
    var right: Tree
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("Tree");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct Tree{left: Tree;right: Tree;}", symbol.type.describe());
    }

    @Test
    public void test3() {
        Parser parser = new Parser();
        String src = """
struct TreeArray {
    var data: [Tree]
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("TreeArray");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct TreeArray{data: [Tree,Int];}", symbol.type.describe());
    }

    @Test
    public void test4() {
        Parser parser = new Parser();
        String src = """
struct TreeArray {
    var data: [Tree?]
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("TreeArray");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct TreeArray{data: [Tree?,Int];}", symbol.type.describe());
    }

    @Test
    public void test5() {
        Parser parser = new Parser();
        String src = """
struct TreeArray {
    var data: [Tree?]?
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("TreeArray");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct TreeArray{data: [Tree?,Int]?;}", symbol.type.describe());
    }

    @Test
    public void test6() {
        Parser parser = new Parser();
        String src = """
func print(t: Tree) {
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("print");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func print(t: Tree)", symbol.type.describe());
    }

    @Test
    public void test7() {
        Parser parser = new Parser();
        String src = """
func makeTree()->Tree {
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("makeTree");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("func makeTree()->Tree", symbol.type.describe());
    }

    @Test(expected = CompilerException.class)
    public void test8() {
        Parser parser = new Parser();
        String src = """
struct TreeArray {
    var data: [Int?]
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
    }

    @Test
    public void test9() {
        Parser parser = new Parser();
        String src = """
struct TreeArray {
    var data: [Int]
}                """;
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var symbol = typeDict.lookup("TreeArray");
        Assert.assertNotNull(symbol);
        Assert.assertEquals("struct TreeArray{data: [Int,Int];}", symbol.type.describe());
    }

}
