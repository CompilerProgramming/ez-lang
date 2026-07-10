package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.compiler.codegen.CodeGen;
import com.compilerprogramming.ezlang.compiler.node.cpus.riscv.riscv;
import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.parser.ShortCircuitLowerer;
import com.compilerprogramming.ezlang.exceptions.CompilerException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static com.compilerprogramming.ezlang.compiler.Main.PORTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSONTypes {

    String compileSrc(String src) {
        var compiler = new CodeGen(src);
        compiler.parse();
        return null;
    }

    @Test
    public void test1() {
        String src = """
struct T { var i: Int }
""";
        compileSrc(src);
    }

    @Test
    public void test2() {
        String src = """
struct T1 { var i: S }
struct S { var i: Int }
""";
        compileSrc(src);
    }

    @Test
    public void test3() {
        String src = """
struct T2 { var next: T2? }
""";
        compileSrc(src);
    }

    @Test
    public void test4() {
        String src = """
struct T3 { var arr: [Int] }
""";
        compileSrc(src);
    }

    @Test
    public void test5() {
        String src = """
func foo() {}
""";
        compileSrc(src);
    }

    @Test
    public void test6() {
        String src = """
func foo(a: Int) {}
""";
        compileSrc(src);
    }


    @Test
    public void test7() {
        String src = """
func foo(a: Int, b: Int)->Int {}
""";
        compileSrc(src);
    }

    @Test
    public void test8() {
        String src = """
func foo(a: Int, b: Int)->[Int] {}
""";
        compileSrc(src);
    }

    @Test
    public void test9() {
        String src = """
func foo(a: Int, b: Int)->[Int]? {}
""";
        compileSrc(src);
    }

    @Test
    public void test10() {
        String src = """
func foo(a: Int, b: Int)->Int { 
  return a+b;
}
""";
        compileSrc(src);
    }

    @Test
    public void test11() {
        String src = """
func foo()->[Int] { 
  return new [Int]{};
}
""";
        compileSrc(src);
    }

    @Test
    public void test12() {
        String src = """
func foo()->[Int] { 
  return new [Int]{1};
}
""";
        compileSrc(src);
    }

    @Test
    public void test13() {
        String src = """
func foo()->[Int] { 
  return new [Int]{42,84};
}
""";
        compileSrc(src);
    }

    @Test
    public void test14() {
        String src = """
struct T4 { var i: Int }
func foo()->T4 { 
  return new T4{i=1};
}
""";
        compileSrc(src);
    }

    @Test(expected = CompilerException.class)
    public void testArrayOfStructRejected() {
        String src = """
struct Point { var x: Int }
func foo()->Int {
  var points = new [Point] { len = 1, new Point { x = 42 } }
  return points[0].x
}
""";
        compileSrc(src);
    }

    @Test
    public void testArrayOfNullableStructAllowed() {
        String src = """
struct Point { var x: Int }
func foo()->Int {
  var points = new [Point?] { len = 2, null, new Point { x = 42 } }
  return #points
}
""";
        compileSrc(src);
    }

    @Test
    public void testStructLayoutsDoNotLeakAcrossCompilations() {
        compileSrc("""
struct Point { var x: Int }
func foo()->Int {
  var p = new Point { x = 1 }
  return p.x
}
""");
        compileSrc("""
struct Point { var y: Int }
func foo()->Int {
  var p = new Point { y = 2 }
  return p.y
}
""");
    }

    @Ignore("Bug in backend")
    @Test
    public void testArrayLoadNullCheckNarrowsNullableElement() {
        String src = """
struct Point { var x: Int }
func foo(a: Int)->Int {
  var points = new [Point?] { len = 2 }
  points[a] = new Point { x = 42 }
  var p = points[1]
  if (p != null)
    return p.x
  return -1
}
""";
        var compiler = new CodeGen(src);
        compiler.parse().opto().typeCheck();
    }

    @Test
    public void test15() {
        String src = """
struct T5 { var i: Int; var j: Int }
func foo()->T5 { 
  return new T5{i=23, j=32};
}
""";
        compileSrc(src);
    }

    @Test
    public void test16() {
        String src = """
func foo()->Int { 
  return new [Int]{42,84}[1];
}
""";
        compileSrc(src);
    }

    @Test
    public void testArrayLengthUnaryOperator() {
        String src = """
func foo()->Int {
  return #new [Int]{42,84};
}
""";
        compileSrc(src);
    }
    @Test
    public void test17() {
        String src = """
struct T5 { var i: Int; var j: Int }
func foo()->Int { 
  return new T5{i=23, j=32}.j;
}
""";
        compileSrc(src);
    }

    @Test
    public void test18() {
        String src = """
func bar()->Int { return 1 }
func foo()->Int { 
  return bar();
}
""";
        compileSrc(src);
    }

    @Test
    public void test19() {
        String src = """
func foo()->Int { 
  var x = 1
  return x
}
""";
        compileSrc(src);
    }

    @Test
    public void test20ShortCircuitReturn() {
        String src = """
func foo(a: Int, b: Int)->Int {
  return a && b
}
""";
        compileSrc(src);
    }

    @Test
    public void test21ShortCircuitVarInitializerAndCallArg() {
        String src = """
func id(a: Int)->Int { return a }
func foo(a: Int, b: Int)->Int {
  var x = id(a || b)
  return x
}
""";
        compileSrc(src);
    }

    @Test
    public void test22ShortCircuitConditions() {
        String src = """
func foo(a: Int, b: Int)->Int {
  var x = 0
  if (a && b) {
    x = 1
  }
  while (x || b) {
    x = 0
  }
  return x
}
""";
        compileSrc(src);
    }

    @Test
    public void test23ShortCircuitCodegen() {
        testAllCPUs("""
                func main()->Int {
                    var a = 0
                    var b = 1
                    return a && b
                }
                """, 0, null);
    }
    @Test
    public void test24ShortCircuitWhileConditionLoweredInsideLoop() {
        String src = """
func foo(a: Int, b: Int)->Int {
  var x = 0
  while (x || b) {
    x = 0
  }
  return x
}
""";
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        ShortCircuitLowerer.lower(program);
        String lowered = program.toString();
        assertTrue(lowered.contains("while(1)"));
        assertTrue(lowered.contains("while(1)\n{\nvar __sc0 = 1"));
    }
    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().loopTree().instSelect(cpu,os).GCM().localSched().regAlloc().encode();
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
        System.out.println(code.asm());
    }

    private static void testAllCPUs( String src, int spills, String stop ) {
        testCPU(src,"x86_64_v2", "SystemV",spills,stop);
        testCPU(src,"riscv"    , "SystemV",spills,stop);
        testCPU(src,"arm"      , "SystemV",spills,stop);
    }

    @Test
    public void test101() {
        testAllCPUs("""
                func main()->Int {
                    return 42
                }
                """, 0, null);
    }

    @Test
    public void testMergeSortUsingRisc5Emulator() throws IOException {
        EvalRisc5 R5 = TestRisc5.build("src/test/cases/mergsort", "sort", "main", 0, 59, false);
        int trap = R5.step(100000);
        assertEquals(0,trap);
        assertEquals(1L, R5.regs[riscv.A0]);
    }

    @Test
    public void testFibUsingRisc5Emulator() throws IOException {
        EvalRisc5 R5 = TestRisc5.build("src/test/cases/fib", "fib", "foo", 0, 19, false);
        int trap = R5.step(100000);
        assertEquals(0,trap);
        assertEquals(89L, R5.regs[riscv.A0]);
    }

    @Test
    public void testSieveUsingRisc5Emulator() throws IOException {
        EvalRisc5 R5 = TestRisc5.build("src/test/cases/sieve", "sieve", "main", 0, 106, false);
        int trap = R5.step(100000);
        assertEquals(0,trap);
        assertEquals(1L, R5.regs[riscv.A0]);
    }
}
