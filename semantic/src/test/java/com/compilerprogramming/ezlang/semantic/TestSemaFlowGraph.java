package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestSemaFlowGraph {

    private String test(String src, String symbolName) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        var symbol = typeDict.lookup(symbolName);
        Assert.assertNotNull(symbol);
        if (symbol instanceof Symbol.FunctionTypeSymbol ftsym) {
            var fnDecl = (AST.FuncDecl) ftsym.functionDecl;
            var flowGraph = FlowCFG.build(fnDecl);
            var dot = flowGraph.toDot();
            System.out.println(dot);
            return flowGraph.toString();
        }
        return null;
    }

    @Test
    public void test1() {
        String src = """
    func bar(i: Int) {}
    func foo(v: Int)
    {
        if (v == 0)
            bar(v+1);
        else if (v == 1)
            bar(v-20);
        else 
            bar(v*30);
    }
""";
        var result = test(src, "foo");
        System.out.println(result);
    }

    @Test
    public void test2() {
        String src = """
    func bar(i: Int) {}
    func foo(a: Int, b: Int)
    {
        if (a && b)
            bar(a+b);
        else 
            bar(0);
    }
""";
        var result = test(src, "foo");
        System.out.println(result);
    }

    @Test
    public void test3() {
        String src = """
    func bar(i: Int) {}
    func foo(a: Int, b: Int, c: Int)
    {
        if (a && b || c)
            bar(a+b);
        else 
            bar(0);
    }
""";
        var result = test(src, "foo");
        System.out.println(result);
    }

    @Test
    public void test4() {
        String src = """
    func bar(i: Int) {}
    func foo(a: Int, b: Int, c: Int)
    {
        if (a && b || a && c)
            bar(a+b);
        else 
            bar(0);
    }
""";
        var result = test(src, "foo");
        System.out.println(result);
    }

    @Test
    public void test5() {
        String src = """
    func bar(i: Int) {}
    func foo(a: Int, b: Int)
    {
        while (a && b) {
            bar(a+b);
            b = a;
            a = 0;
        }
    }
""";
        var result = test(src, "foo");
        System.out.println(result);
    }

}
