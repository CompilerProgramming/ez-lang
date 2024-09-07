package com.compilerprogramming.ezlang.bytecode;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.semantic.SemaAssignTypes;
import com.compilerprogramming.ezlang.semantic.SemaDefineTypes;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Test;

import java.util.BitSet;

public class TestCompiler {

    void compileSrc(String src, String functionName) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        ByteCodeCompiler byteCodeCompiler = new ByteCodeCompiler();
        byteCodeCompiler.compile(typeDict);
        for (Symbol s: typeDict.bindings.values()) {
            if (s instanceof Symbol.FunctionTypeSymbol f) {
                var functionBuilder = (FunctionBuilder) f.code;
                System.out.println(BasicBlock.toStr(new StringBuilder(), functionBuilder.entry, new BitSet()));
            }
        }
    }

    @Test
    public void testFunction1() {
        String src = """
                func foo(n: Int)->Int {
                    return 1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction2() {
        String src = """
                func foo(n: Int)->Int {
                    return -1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction3() {
        String src = """
                func foo(n: Int)->Int {
                    return n;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction4() {
        String src = """
                func foo(n: Int)->Int {
                    return -n;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction5() {
        String src = """
                func foo(n: Int)->Int {
                    return n+1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction6() {
        String src = """
                func foo(n: Int)->Int {
                    return 1+1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction7() {
        String src = """
                func foo(n: Int)->Int {
                    return 1+1-1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction8() {
        String src = """
                func foo(n: Int)->Int {
                    return 2==2;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction9() {
        String src = """
                func foo(n: Int)->Int {
                    return 1!=1;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction10() {
        String src = """
                func foo(n: [Int])->Int {
                    return n[0];
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction11() {
        String src = """
                func foo(n: [Int])->Int {
                    return n[0]+n[1];
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction12() {
        String src = """
                func foo()->[Int] {
                    return new [Int] { 1, 2, 3 };
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction13() {
        String src = """
                func foo(n: Int) -> [Int] {
                    return new [Int] { n };
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction14() {
        String src = """
                func add(x: Int, y: Int) -> Int {
                    return x+y;
                }
                """;
        compileSrc(src, "add");
    }

    @Test
    public void testFunction14a() {
        String src = """
                struct Person
                {
                    var age: Int
                    var children: Int
                }
                func foo(p: Person) -> Person {
                    p.age = 10;
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction14b() {
        String src = """
                struct Person
                {
                    var age: Int
                    var children: Int
                }
                func foo() -> Person {
                    return new Person { age=10, children=0 };
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction14c() {
        String src = """
                func foo(array: [Int]) {
                    array[0] = 1
                    array[1] = 2
                }
                """;
        compileSrc(src, "foo");
    }

    @Test
    public void testFunction15() {
        String src = """
                func min(x: Int, y: Int) -> Int {
                    if (x < y)
                        return x;
                    return y;
                }
                """;
        compileSrc(src, "min");
    }

    @Test
    public void testFunction16() {
        String src = """
                func loop() {
                    while (1)
                        return;
                    return;
                }
                """;
        compileSrc(src, "loop");
    }

    @Test
    public void testFunction17() {
        String src = """
                func loop() {
                    while (1)
                        break;
                    return;
                }
                """;
        compileSrc(src, "loop");
    }

    @Test
    public void testFunction18() {
        String src = """
                func loop(n: Int) {
                    while (n > 0) {
                        n = n - 1;
                    }
                    return;
                }
                """;
        compileSrc(src, "loop");
    }

    @Test
    public void testFunction19() {
        String src = """
                func foo() {}
                func bar() { foo(); }
                """;
        compileSrc(src, "bar");
    }

    @Test
    public void testFunction20() {
        String src = """
                func foo(x: Int, y: Int) {}
                func bar() { foo(1,2); }
                """;
        compileSrc(src, "bar");
    }

    @Test
    public void testFunction21() {
        String src = """
                func foo(x: Int, y: Int)->Int { return x+y; }
                func bar()->Int { var t = foo(1,2); return t+1; }
                """;
        compileSrc(src, "bar");
    }

}
