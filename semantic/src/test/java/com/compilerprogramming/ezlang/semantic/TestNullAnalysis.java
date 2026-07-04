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
        NullableAnalysis.analyze(typeDict);
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

    @Test
    public void numericLiteralAndNonConstantArithmetic() {
        String src = """
    func takesInt(arg: Int) {
    }
    func test(a: Int, b: Int) {
        takesInt(1);
        takesInt(a + b);
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableReturnToNonNullType() {
        String src = """
    struct Foo { var bar: Int }
    func test(arg: Foo?)->Foo {
        return arg;
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableFieldDereference() {
        String src = """
    struct Foo { var bar: Int }
    func test(arg: Foo?)->Int {
        return arg.bar;
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableArrayDereference() {
        String src = """
    func test(arg: [Int]?)->Int {
        return arg[0];
    }
""";
        analyze(src, "test");
    }

    @Test
    public void guardedNullableFieldStore() {
        String src = """
    struct Foo { var bar: Int }
    func test(arg: Foo?) {
        if (arg != null) {
            arg.bar = 1;
        }
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableFieldStore() {
        String src = """
    struct Foo { var bar: Int }
    func test(arg: Foo?) {
        arg.bar = 1;
    }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableArrayStore() {
        String src = """
    func test(arg: [Int]?) {
        arg[0] = 1;
    }
""";
        analyze(src, "test");
    }

    @Test
    public void testArrayInit() {
        String src = """
        func test()->[Int] {
            return new [Int] { 1, 2, 3 };
        }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void nullableArrayFillValue() {
        String src = """
        struct Foo {}
        func test(arg: Foo?)->[Foo] {
            return new [Foo] { len=1, value=arg };
        }
""";
        analyze(src, "test");
    }

    @Test
    public void knownNonNullArrayElement() {
        String src = """
        struct Foo { var value: Int }
        func test()->Int {
            var array = new [Foo?] { new Foo { value=1 }, null };
            return array[0].value;
        }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void knownNullArrayElement() {
        String src = """
        struct Foo { var value: Int }
        func test()->Int {
            var array = new [Foo?] { new Foo { value=1 }, null };
            return array[1].value;
        }
""";
        analyze(src, "test");
    }

    @Test(expected = CompilerException.class)
    public void arrayStoreInvalidatesKnownElement() {
        String src = """
        struct Foo { var value: Int }
        func test()->Int {
            var array = new [Foo?] { new Foo { value=1 } };
            array[0] = null;
            return array[0].value;
        }
""";
        analyze(src, "test");
    }
}
