package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.semantic.SemaAssignTypes;
import com.compilerprogramming.ezlang.semantic.SemaDefineTypes;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

import java.util.BitSet;

public class TestCompiler {

    String compileSrc(String src) {
        var compiler = new Compiler();
        var typeDict = compiler.compileSrc(src);
        return compiler.dumpIR(typeDict);
    }

    @Test
    public void testFunction1() {
        String src = """
                func foo(n: Int)->Int {
                    return 1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction2() {
        String src = """
                func foo(n: Int)->Int {
                    return -1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	negi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction3() {
        String src = """
                func foo(n: Int)->Int {
                    return n;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction4() {
        String src = """
                func foo(n: Int)->Int {
                    return -n;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	negi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction5() {
        String src = """
                func foo(n: Int)->Int {
                    return n+1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 1
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction6() {
        String src = """
                func foo(n: Int)->Int {
                    return 1+1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	pushi 1
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction7() {
        String src = """
                func foo(n: Int)->Int {
                    return 1+1-1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	pushi 1
	addi
	pushi 1
	subi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction8() {
        String src = """
                func foo(n: Int)->Int {
                    return 2==2;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 2
	pushi 2
	eq
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction9() {
        String src = """
                func foo(n: Int)->Int {
                    return 1!=1;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	pushi 1
	neq
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction10() {
        String src = """
                func foo(n: [Int])->Int {
                    return n[0];
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 0
	loadindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction11() {
        String src = """
                func foo(n: [Int])->Int {
                    return n[0]+n[1];
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 0
	loadindexed
	load 0
	pushi 1
	loadindexed
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction12() {
        String src = """
                func foo()->[Int] {
                    return new [Int] { 1, 2, 3 };
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	new [Int]
	pushi 0
	pushi 1
	storeindexed
	pushi 1
	pushi 2
	storeindexed
	pushi 2
	pushi 3
	storeindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction13() {
        String src = """
                func foo(n: Int) -> [Int] {
                    return new [Int] { n };
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	new [Int]
	pushi 0
	load 0
	storeindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction14() {
        String src = """
                func add(x: Int, y: Int) -> Int {
                    return x+y;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	load 1
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction15() {
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
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 0
	pushi 10
	storeindexed
	pop
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction16() {
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
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	new Person
	pushi 0
	pushi 10
	storeindexed
	pushi 1
	pushi 0
	storeindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction17() {
        String src = """
                func foo(array: [Int]) {
                    array[0] = 1
                    array[1] = 2
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 0
	pushi 1
	storeindexed
	pop
	load 0
	pushi 1
	pushi 2
	storeindexed
	pop
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction18() {
        String src = """
                func min(x: Int, y: Int) -> Int {
                    if (x < y)
                        return x;
                    return y;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	load 1
	lt
	cbr L2 L3
L2:
	load 0
	jump L1
L1:
L3:
	load 1
	jump L1
""", result);
    }

    @Test
    public void testFunction19() {
        String src = """
                func loop() {
                    while (1)
                        return;
                    return;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	jump L2
L2:
	pushi 1
	cbr L3 L4
L3:
	jump L1
L1:
L4:
	jump L1
""", result);
    }

    @Test
    public void testFunction20() {
        String src = """
                func loop() {
                    while (1)
                        break;
                    return;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	jump L2
L2:
	pushi 1
	cbr L3 L4
L3:
	jump L4
L4:
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction21() {
        String src = """
                func loop(n: Int) {
                    while (n > 0) {
                        n = n - 1;
                    }
                    return;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	jump L2
L2:
	load 0
	pushi 0
	gt
	cbr L3 L4
L3:
	load 0
	pushi 1
	subi
	store 0
	jump L2
L4:
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction22() {
        String src = """
                func foo() {}
                func bar() { foo(); }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	jump L1
L1:
L0:
	loadfunc foo
	call 0
	pop
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction23() {
        String src = """
                func foo(x: Int, y: Int) {}
                func bar() { foo(1,2); }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	jump L1
L1:
L0:
	loadfunc foo
	pushi 1
	pushi 2
	call 2
	pop
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction24() {
        String src = """
                func foo(x: Int, y: Int)->Int { return x+y; }
                func bar()->Int { var t = foo(1,2); return t+1; }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	load 1
	addi
	jump L1
L1:
L0:
	loadfunc foo
	pushi 1
	pushi 2
	call 2
	store 0
	load 0
	pushi 1
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction25() {
        String src = """
                struct Person
                {
                    var age: Int
                    var children: Int
                }
                func foo(p: Person) -> Int {
                    return p.age;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 0
	loadindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction26() {
        String src = """
                struct Person
                {
                    var age: Int
                    var parent: Person
                }
                func foo(p: Person) -> Int {
                    return p.parent.age;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	pushi 1
	loadindexed
	pushi 0
	loadindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction27() {
        String src = """
                struct Person
                {
                    var age: Int
                    var parent: Person
                }
                func foo(p: [Person], i: Int) -> Int {
                    return p[i].parent.age;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	load 1
	loadindexed
	pushi 1
	loadindexed
	pushi 0
	loadindexed
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction28() {
        String src = """
                func foo(x: Int, y: Int)->Int { return x+y; }
                func bar(a: Int)->Int { var t = foo(a,2); return t+1; }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	load 1
	addi
	jump L1
L1:
L0:
	loadfunc foo
	load 0
	pushi 2
	call 2
	store 1
	load 1
	pushi 1
	addi
	jump L1
L1:
""", result);
    }

    @Test
    public void testFunction104() {
        String src = """
                func foo()->Int
                {
                    return 1 == 1 && 2 == 2
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	pushi 1
	pushi 1
	eq
	cbr L2 L3
L2:
	pushi 2
	pushi 2
	eq
	jump L4
L4:
	jump L1
L1:
L3:
	pushi 0
	jump L4
""", result);
    }

    @Test
    public void testArrayLengthUnaryOperator() {
        String src = """
                func foo(a: [Int])->Int {
                    return #a;
                }
                """;
        String result = compileSrc(src);
        Assert.assertEquals("""
L0:
	load 0
	length
	jump L1
L1:
""", result);
    }
}
