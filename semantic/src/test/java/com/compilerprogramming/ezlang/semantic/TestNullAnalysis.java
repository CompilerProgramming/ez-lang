package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestNullAnalysis {
    private void analyze(String src, String symbolName) {
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
            new NullableAnalysis(ftsym,typeDict).doAnalysis(flowGraph);
        }
    }

    @Test(expected = CompilerException.class)
    public void test25() {
        String src = """
    struct Foo { var bar: Int }
    func takesFoo(arg: Foo) {
    }
    func test() {
        takesFoo(null);
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test26() {
        String src = """
    struct Foo { var bar: Int }
    func takesOptionalFoo(arg: Foo?) {
    }
    func test() {
        takesOptionalFoo(null);
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test27() {
        String src = """
    struct Foo { var bar: Int }
    func takesFoo(arg: Foo) {
    }
    func test(arg: Foo?) {
        if (arg != null) {
            takesFoo(arg);
        }
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test28() {
        String src = """
    struct Foo {
      var bar: Int
    }
    func takesFoo(arg: Foo) {
    }
    func test(arg: Foo?) {
      if (arg == null) {
        return;
      }
      takesFoo(arg);
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void test29() {
        String src = """
    struct Foo {
      var bar: Int
    }
    func returnsOptionalFoo()->Foo? {
        return null;
    }
    func test() {
        var foo: Foo
        foo = returnsOptionalFoo();
        
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test30() {
        String src = """
    struct Foo {
      var bar: Int
    }
    func returnsFoo()->Foo {
        return new Foo{};
    }
    func test() {
        var foo: Foo
        foo = returnsFoo();
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test31() {
        String src = """
    struct Foo {
      var bar: Int
    }
    func returnsFoo()->Foo {
        return new Foo{};
    }
    func test() {
        var foo: Foo?
        foo = null;
        foo = returnsFoo();
    }
""";
        analyze(src, "test");
    }

    @Test
    public void test32() {
        String src = """
    struct Foo {
      var bar: Int
    }
    func returnsFoo()->Foo? {
        return new Foo{};
    }
    func test() {
        var foo: Foo?
        foo = null;
        foo = returnsFoo();
    }
""";
        analyze(src, "test");
    }
}
