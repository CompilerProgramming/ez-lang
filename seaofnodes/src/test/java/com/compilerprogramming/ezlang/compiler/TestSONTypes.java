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
    public void testArrayLengthUnaryOperatorLiteral() {
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

    private static void runRisc5(String src, String entry, long expected) throws IOException {
        EvalRisc5 risc5 = TestRisc5.buildSrc(src, entry, 0, -1, false);
        assertEquals(0, risc5.step(1_000_000));
        assertEquals(expected, risc5.regs[riscv.A0]);
    }
    @Test
    public void testFunction1() throws IOException {
        String src = """
                func foo()->Int {
                    return 42;
                }
                """;
        runRisc5(src, "foo", 42L);
    }

    @Test
    public void testFunction2() throws IOException {
        String src = """
                func bar()->Int {
                    return 42;
                }
                func foo()->Int {
                    return bar();
                }
                """;
        runRisc5(src, "foo", 42L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction3() throws IOException {
        String src = """
                func negate(n: Int)->Int {
                    return -n;
                }
                func foo()->Int {
                    return negate(42);
                }
                """;
        runRisc5(src, "foo", -42L);
    }

    @Test
    public void testFunction4() throws IOException {
        String src = """
                func foo(x: Int, y: Int)->Int { return x+y; }
                func bar()->Int { var t = foo(1,2); return t+1; }
                """;
        runRisc5(src, "bar", 4L);
    }

    @Test
    public void testFunction5() throws IOException {
        String src = """
                func bar()->Int { var t = new [Int] {1,21,3}; return t[1]; }
                """;
        runRisc5(src, "bar", 21L);
    }

    @Test
    public void testFunction6() throws IOException {
        String src = """
                struct Test
                {
                    var field: Int
                }
                func foo()->Test
                {
                    var test = new Test{ field = 42 }
                    return test
                }
                func bar()->Int { return foo().field }
                """;
        runRisc5(src, "bar", 42L);
    }

    @Test
    public void testFunction7() throws IOException {
        String src = """
                func bar(arg: Int)->Int {
                    if (arg)
                        return 42;
                    return 0;
                }
                func foo()->Int {
                    return bar(1);
                }
                """;
        runRisc5(src, "foo", 42L);
    }

    @Test
    public void testFunction8() throws IOException {
        String src = """
                func factorial(num: Int)->Int {
                    var result = 1
                    while (num > 1)
                    {
                      result = result * num
                      num = num - 1
                    }
                    return result
                }
                func foo()->Int {
                    return factorial(5);
                }
                """;
        runRisc5(src, "foo", 120L);
    }

    @Test
    public void testFunction9() throws IOException {
        String src = """
                func fib(n: Int)->Int {
                    var f1=1
                    var f2=1
                    var i=n
                    while( i>1 ){
                        var temp = f1+f2
                        f1=f2
                        f2=temp
                        i=i-1
                    }
                    return f2
                }
                func foo()->Int {
                    return fib(10)
                }
                """;
        runRisc5(src, "foo", 89L);
    }

    @Test
    public void testFunction10() throws IOException {
        String src = """
                func foo()->Int {
                    if (1)
                        return 2;
                    return 3;
                }
                """;
        runRisc5(src, "foo", 2L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction11() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 1
                    var k = 2
                    var m = 4
                    var n = k * m
                    j = n + j
                    data[0] = j
                }
                func foo()->Int {
                    var data = new [Int] {0}
                    bar(data)
                    return data[0]
                }
                """;
        runRisc5(src, "foo", 9L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction12() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 12345
                    if (data[0])
                        data[1] = 1 + j - 1234
                    else
                        data[2] = 123 + j + 10
                }
                func foo()->Int {
                    var data = new [Int] {0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2];
                }
                """;
        runRisc5(src, "foo", 12478L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction13() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 12345
                    if (data[0])
                        data[1] = 1 + j - 1234
                    else
                        data[2] = 123 - j + 10
                }
                func foo()->Int {
                    var data = new [Int] {0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2];
                }
                """;
        runRisc5(src, "foo", -12212L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction14() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 5
                    if (data[0])
                        data[1] = 10
                    else
                        data[2] = 15
                    data[3] = j + 21
                }
                func foo()->Int {
                    var data = new [Int] {0,0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2]+data[3];
                }
                """;
        runRisc5(src, "foo", 5L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction15() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 5
                    if (data[0])
                        data[1] = j * 10
                    else
                        data[2] = j * 15
                    data[3] = j * 21
                }
                func foo()->Int {
                    var data = new [Int] {0,0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2]+data[3];
                }
                """;
        runRisc5(src, "foo", 5L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction16() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j: Int
                    if (data[0]) {
                        j = 5
                        data[1] = 10
                    }
                    else {
                        data[2] = 15
                        j = 5
                    }
                    data[3] = j + 21
                }
                func foo()->Int {
                    var data = new [Int] {1,0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2]+data[3]
                }
                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction17() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j: Int
                    var k: Int
                    if (data[0]) {
                        j = 4
                        k = 6
                        data[1] = j
                    }
                    else {
                        j = 7
                        k = 3
                        data[2] = k
                    }
                    data[3] = (j+k) * 21
                }
                func foo()->Int {
                    var data = new [Int] {1,0,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2]+data[3]
                }
                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction18() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var i: Int
                    var stop = data[0]
                    var j = 21
                    i = 1
                    while ( i < stop ) {
                        j = (j - 20) * 21;
                        i = i + 1
                    }
                    data[1] = j
                    data[2] = i
                }
                func foo()->Int {
                    var data = new [Int] {2,0,0}
                    bar(data)
                    return data[0]+data[1]+data[2];
                }
                """;
        runRisc5(src, "foo", 2L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction19() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = 1
                    if (j) j = 10
                    else j = data[0]
                    data[0] = j * 21 + data[1]
                }
                func foo()->Int {
                    var data = new [Int] {2,3}
                    bar(data)
                    return data[0]+data[1];
                }
                """;
        runRisc5(src, "foo", 213L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction20() throws IOException {
        String src = """
                func bar(data: [Int]) {
                    var j = data[0]
                    if (j == 5)
                        j = j * 21 + 25 / j
                    data[1] = j
                }
                func foo()->Int {
                    var data = new [Int] {5,3}
                    bar(data)
                    return data[0]+data[1];
                }
                """;
        runRisc5(src, "foo", 5L);
    }

    @Test
    public void testFunction101() throws IOException {
        String src = """
                func foo()->Int
                {
                    return null == null;
                }

                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction102() throws IOException {
        String src = """
                struct Foo
                {
                    var next: Foo?
                }
                func foo()->Int
                {
                    var f = new Foo{ next = null }
                    return null == f.next
                }

                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    public void testFunction103() throws IOException {
        String src = """
                struct Foo
                {
                    var i: Int
                }
                func foo()->Int
                {
                    var f = new [Foo?] { new Foo{i = 1}, null }
                    var f0 = f[0]
                    return null == f[1] && f0 != null && 1 == f0.i
                }

                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    public void testFunction104() throws IOException {
        String src = """
                func foo()->Int
                {
                    return 1 == 1 && 2 == 2
                }

                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    public void testFunction105() throws IOException {
        String src = """
                func bar(a: Int, b: Int)->Int
                {
                    return a+1 == b-1 && b / a == 2
                }
                func foo()->Int
                {
                    return bar(3,5)
                }
                """;
        runRisc5(src, "foo", 0L);
    }

    @Test
    public void testFunction106() throws IOException {
        String src = """
                func bar(a: Int, b: Int)->Int
                {
                    return a+1 == b-1 || b / a == 2
                }
                func foo()->Int
                {
                    return bar(3,5)
                }
                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    public void testFunction107() throws IOException {
        String src = """
                func bar(a: [Int])->Int
                {
                    return a[0]+a[2] == a[1]-a[2] || a[1] / a[0] == 2
                }
                func foo()->Int
                {
                    return bar(new [Int] {3,5,1})
                }
                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    public void testMergeSort() throws IOException {
        String src = """
// based on the top-down version from https://en.wikipedia.org/wiki/Merge_sort
// via https://github.com/SeaOfNodes/Simple
func merge_sort(a: [Int], b: [Int], n: Int)
{
    copy_array(a, 0, n, b)
    split_merge(a, 0, n, b)
}

func split_merge(b: [Int], begin: Int, end: Int, a: [Int])
{
    if (end - begin <= 1)
        return;
    var middle = (end + begin) / 2
    split_merge(a, begin, middle, b)
    split_merge(a, middle, end, b)
    merge(b, begin, middle, end, a)
}

func merge(b: [Int], begin: Int, middle: Int, end: Int, a: [Int])
{
    var i = begin
    var j = middle
    var k = begin
    while (k < end) {
        // && and ||
        var cond = 0
        if (i < middle) {
            if (j >= end)          cond = 1;
            else if (a[i] <= a[j]) cond = 1;
        }
        if (cond)
        {
            b[k] = a[i]
            i = i + 1
        }
        else
        {
            b[k] = a[j]
            j = j + 1
        }
        k = k + 1
    }
}

func copy_array(a: [Int], begin: Int, end: Int, b: [Int])
{
    var k = begin
    while (k < end)
    {
        b[k] = a[k]
        k = k + 1
    }
}

func eq(a: [Int], b: [Int], n: Int)->Int
{
    var result = 1
    var i = 0
    while (i < n)
    {
        if (a[i] != b[i])
        {
            result = 0
            break
        }
        i = i + 1
    }
    return result
}

func main()->Int
{
    var a = new [Int]{10,9,8,7,6,5,4,3,2,1}
    var b = new [Int]{ 0,0,0,0,0,0,0,0,0,0}
    var expect = new [Int]{1,2,3,4,5,6,7,8,9,10}
    merge_sort(a, b, 10)
    return eq(a,expect,10)
}
""";
        runRisc5(src, "main", 1L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction108() throws IOException {
        String src = """
                func make(len: Int, val: Int)->[Int]
                {
                    return new [Int]{len=len, value=val}
                }
                func main()->Int
                {
                    var arr = make(3,3);
                    var i = 0
                    while (i < 3) {
                        if (arr[i] != 3)
                            return 1
                        i = i + 1
                    }
                    return 0
                }
                """;
        runRisc5(src, "main", 0L);
    }

    @Test
    public void testFunction109() throws IOException {
        String src = """
func sieve(N: Int)->[Int]
{
    // The main Sieve array
    var ary = new [Int]{len=N,value=0}
    // The primes less than N
    var primes = new [Int]{len=N/2,value=0}
    // Number of primes so far, searching at index p
    var nprimes = 0
    var p=2
    // Find primes while p^2 < N
    while( p*p < N ) {
        // skip marked non-primes
        while( ary[p] ) {
            p = p + 1
        }
        // p is now a prime
        primes[nprimes] = p
        nprimes = nprimes+1
        // Mark out the rest non-primes
        var i = p + p
        while( i < N ) {
            ary[i] = 1
            i = i + p
        }
        p = p + 1
    }

    // Now just collect the remaining primes, no more marking
    while ( p < N ) {
        if( !ary[p] ) {
            primes[nprimes] = p
            nprimes = nprimes + 1
        }
        p = p + 1
    }

    // Copy/shrink the result array
    var rez = new [Int]{len=nprimes,value=0}
    var j = 0
    while( j < nprimes ) {
        rez[j] = primes[j]
        j = j + 1
    }
    return rez
}
func eq(a: [Int], b: [Int], n: Int)->Int
{
    var result = 1
    var i = 0
    while (i < n)
    {
        if (a[i] != b[i])
        {
            result = 0
            break
        }
        i = i + 1
    }
    return result
}

func main()->Int
{
    var rez = sieve(20)
    var expected = new [Int]{2,3,5,7,11,13,17,19}
    return eq(rez,expected,8)
}
""";
        runRisc5(src, "main", 1L);
    }

    @Test
    public void testFunction110() throws IOException {
        String src = """
func swap(arr: [Int], i: Int, j: Int) {
    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
}

func partition(arr: [Int], low: Int, high: Int)->Int {
    var pivot = arr[high];
    var i = low;
    var j = low;
    while (j < high) {
        if (arr[j] < pivot) {
            swap(arr, i, j);
            i = i + 1;
        }
        j = j + 1;
    }
    swap(arr, i, high);
    return i;
}

func quicksort(arr: [Int], low: Int, high: Int) {
    if (low < high) {
        var p = partition(arr, low, high);
        quicksort(arr, low, p - 1);
        quicksort(arr, p + 1, high);
    }
}

func eq(a: [Int], b: [Int], n: Int)->Int
{
    var result = 1
    var i = 0
    while (i < n)
    {
        if (a[i] != b[i])
        {
            result = 0
            break
        }
        i = i + 1
    }
    return result
}

func main()->Int
{
    var nums = new [Int]{33, 10, 55, 71, 29, 3};
    var expected = new [Int]{3,10,29,33,55,71}
    quicksort(nums, 0, 5);
    return eq(nums,expected,6)
}
""";
        runRisc5(src, "main", 1L);
    }

    @Test
    public void testRecursiveTwoCallsWithLiveValues() throws IOException {
        String src = """
func recurse(low: Int, high: Int)->Int {
    if (low < high) {
        var p = low;
        recurse(low, p - 1);
        return recurse(p + 1, high);
    }
    return 1;
}

func main()->Int {
    return recurse(0, 5);
}
""";
        runRisc5(src, "main", 1L);
    }
    @Test
    public void testRecursiveTwoCallsWithArrayArgument() throws IOException {
        String src = """
func recurse(arr: [Int], low: Int, high: Int)->Int {
    if (low < high) {
        var p = low;
        recurse(arr, low, p - 1);
        return recurse(arr, p + 1, high);
    }
    return 1;
}

func main()->Int {
    var nums = new [Int]{33, 10, 55, 71, 29, 3};
    return recurse(nums, 0, 5);
}
""";
        runRisc5(src, "main", 1L);
    }
    @Test
    public void testRecursiveTwoCallsWithComputedPivot() throws IOException {
        String src = """
func choosePivot(arr: [Int], low: Int, high: Int)->Int {
    return low;
}

func recurse(arr: [Int], low: Int, high: Int)->Int {
    if (low < high) {
        var p = choosePivot(arr, low, high);
        recurse(arr, low, p - 1);
        return recurse(arr, p + 1, high);
    }
    return 1;
}

func main()->Int {
    var nums = new [Int]{33, 10, 55, 71, 29, 3};
    return recurse(nums, 0, 5);
}
""";
        runRisc5(src, "main", 1L);
    }
    @Test
    public void testFunction110ReducedPartition() throws IOException {
        String src = """
func swap(arr: [Int], i: Int, j: Int) {
    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
}

func partition(arr: [Int], low: Int, high: Int)->Int {
    var pivot = arr[high];
    var i = low;
    var j = low;
    while (j < high) {
        if (arr[j] < pivot) {
            swap(arr, i, j);
            i = i + 1;
        }
        j = j + 1;
    }
    swap(arr, i, high);
    return i;
}

func main()->Int {
    var nums = new [Int]{33, 10, 55, 71, 29, 3};
    return partition(nums, 0, 4);
}
""";
        runRisc5(src, "main", 1L);
    }
    @Test
    public void testFunction110ReducedRecursive() throws IOException {
        String src = """
func swap(arr: [Int], i: Int, j: Int) {
    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
}

func partition(arr: [Int], low: Int, high: Int)->Int {
    var pivot = arr[high];
    var i = low;
    var j = low;
    while (j < high) {
        if (arr[j] < pivot) {
            swap(arr, i, j);
            i = i + 1;
        }
        j = j + 1;
    }
    swap(arr, i, high);
    return i;
}

func quicksort(arr: [Int], low: Int, high: Int) {
    if (low < high) {
        var p = partition(arr, low, high);
        quicksort(arr, low, p - 1);
        quicksort(arr, p + 1, high);
    }
}

func main()->Int {
    var nums = new [Int]{33, 10, 55, 71, 29, 3};
    quicksort(nums, 0, 5);
    return nums[0] * 100 + nums[5];
}
""";
        runRisc5(src, "main", 371L);
    }
    @Test
    public void testNullPhiThroughSSADestruction() throws IOException {
        String src = """
                struct Foo
                {
                    var i: Int
                }
                func choose(c: Int)->Foo? {
                    var x: Foo?
                    x = new Foo{ i = 7 }
                    if (c == 0)
                        x = null
                    return x
                }
                func foo()->Int {
                    return choose(0) == null && choose(1) != null
                }
                """;
        runRisc5(src, "foo", 1L);
    }

    @Test
    @Ignore("RISC5 backend does not yet compile or execute this interpreter case")
    public void testFunction111() throws IOException {
        String src = """
func swap(arr: [Float], i: Int, j: Int) {
    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
}

func partition(arr: [Float], low: Int, high: Int)->Int {
    var pivot = arr[high];
    var i = low;
    var j = low;
    while (j < high) {
        if (arr[j] < pivot) {
            swap(arr, i, j);
            i = i + 1;
        }
        j = j + 1;
    }
    swap(arr, i, high);
    return i;
}

func quicksort(arr: [Float], low: Int, high: Int) {
    if (low < high) {
        var p = partition(arr, low, high);
        quicksort(arr, low, p - 1);
        quicksort(arr, p + 1, high);
    }
}

func eq(a: [Float], b: [Float], n: Int)->Int
{
    var result = 1
    var i = 0
    while (i < n)
    {
        if (a[i] != b[i])
        {
            result = 0
            break
        }
        i = i + 1
    }
    return result
}

func main()->Int
{
    var nums = new [Float]{33.4, 10.2, 55.1, 71.9, 29.9, 3.5};
    var expected = new [Float]{3.5,10.2,29.9,33.4,55.1,71.9}
    quicksort(nums, 0, 5);
    return eq(nums,expected,6)
}
""";
        runRisc5(src, "main", 1L);
    }

    @Test
    public void testArrayLengthUnaryOperator() throws IOException {
        String src = """
                func foo()->Int {
                    var a = new [Int] {1,2,3,4}
                    var b = new [Int] {len=0,value=0}
                    return #a + #b
                }
                """;
        runRisc5(src, "foo", 4L);
    }

}
