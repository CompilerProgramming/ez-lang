package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestSemaFlowGraph {

    private FlowCFG.FlowGraph test(String src, String symbolName) {
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
            return flowGraph;
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

    @Test
    public void test6() {
        String src = """
    func bar(i: Int)->Int { return 0; }
    func foo(a: Int, b: Int)
    {
        while (a && b) {
            bar(a+b + (bar(a) || bar(b)));
            b = a;
            a = 0;
        }
    }
""";
        var result = test(src, "foo");
        var text = result.toString();
        var dot = result.toDot();
        Assert.assertTrue(text.contains("<short-circuit>"));
        Assert.assertTrue(dot.contains("<short-circuit>"));
        Assert.assertFalse(text.contains("(bar(a)||bar(b))"));
        Assert.assertFalse(dot.contains("(bar(a)||bar(b))"));
        System.out.println(result);
    }


    @Test
    public void testLogicalOperatorsNestedInConditionExpressions() {
        String src = """
    func bar(i: Int) {}
    func foo(a: Int, b: Int, c: Int, d: Int)
    {
        if ((a && b) == (c || d))
            bar(1);
    }
""";
        var graph = test(src, "foo");

        var conditions = graph.blocks.stream()
                .map(block -> block.condition)
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();

        Assert.assertEquals(5, conditions.size());
        Assert.assertTrue(conditions.contains("((a&&b)==(c||d))"));
        Assert.assertTrue(conditions.containsAll(
                java.util.List.of("a", "b", "c", "d")));
    }

    @Test
    public void testLogicalOperatorsNestedInStatementExpressions() {
        String src = """
    func bar(i: Int)->Int { return i; }
    func foo(a: Int, b: Int)->Int
    {
        var value = bar(a && b);
        value = a || b;
        return value + (a && b);
    }
""";
        var graph = test(src, "foo");

        long conditionCount = graph.blocks.stream()
                .filter(block -> block.condition != null)
                .count();
        Assert.assertEquals(6, conditionCount);

        long statementCount = graph.blocks.stream()
                .flatMap(block -> block.statements.stream())
                .count();
        Assert.assertEquals(3, statementCount);
    }
}
