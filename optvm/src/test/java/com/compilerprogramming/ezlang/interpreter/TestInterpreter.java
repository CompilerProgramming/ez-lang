package com.compilerprogramming.ezlang.interpreter;

import com.compilerprogramming.ezlang.compiler.Compiler;
import com.compilerprogramming.ezlang.compiler.CompiledFunction;
import com.compilerprogramming.ezlang.compiler.Instruction;
import com.compilerprogramming.ezlang.compiler.Operand;
import com.compilerprogramming.ezlang.exceptions.InterpreterException;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.compiler.Options;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

@RunWith(Parameterized.class)
public class TestInterpreter {

    @Parameterized.Parameter
    public EnumSet<Options> options;

    Value compileAndRun(String src, String mainFunction) {
        return compileAndRun(src, mainFunction, options);
    }
    Value compileAndRun(String src, String mainFunction, EnumSet<Options> optionsIgnored) {
        //options.add(Options.ISSA);
        var compiler = new Compiler();
        var typeDict = compiler.compileSrc(src, options);
        var compiled = compiler.dumpIR(typeDict);
        System.out.println(compiled);
        var interpreter = new Interpreter(typeDict);
        return interpreter.run(mainFunction);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        List<EnumSet<Options>> baseOptions = List.of(
                Options.NONE,
                Options.OPT,
                Options.OPT_B,
                Options.OPT_ISSA,
                Options.OPT_ISSA_B);
        List<Object[]> parameters = new ArrayList<>();
        for (EnumSet<Options> base : baseOptions) {
            parameters.add(new Object[] { EnumSet.copyOf(base) });
            EnumSet<Options> lowered = EnumSet.copyOf(base);
            lowered.add(Options.LOWER_SHORT_CIRCUIT);
            parameters.add(new Object[] { lowered });
        }
        return parameters;
    }


    @Test
    public void testFunction1() {
        String src = """
                func foo()->Int {
                    return 42;
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
            && integerValue.value == 42);
    }

    @Test
    public void testFunction2() {
        String src = """
                func bar()->Int {
                    return 42;
                }
                func foo()->Int {
                    return bar();
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 42);
    }

    @Test
    public void testFunction3() {
        String src = """
                func negate(n: Int)->Int {
                    return -n;
                }
                func foo()->Int {
                    return negate(42);
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == -42);
    }

    @Test
    public void testFunction4() {
        String src = """
                func foo(x: Int, y: Int)->Int { return x+y; }
                func bar()->Int { var t = foo(1,2); return t+1; }
                """;
        var value = compileAndRun(src, "bar");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 4);
    }

    @Test
    public void testFunction5() {
        String src = """
                func bar()->Int { var t = new [Int] {1,21,3}; return t[1]; }
                """;
        var value = compileAndRun(src, "bar");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 21);
    }

    @Test
    public void testFunction6() {
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
        var value = compileAndRun(src, "bar");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 42);
    }

    @Test
    public void testFunction7() {
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
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 42);
    }

    @Test
    public void testFunction8() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 120);
    }

    @Test
    public void testFunction9() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 89);
    }

    @Test
    public void testFunction10() {
        String src = """
                func foo()->Int {
                    if (1)
                        return 2;
                    return 3;
                }
                """;
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 2);
    }

    // 4.1.2 optimizer evaluation
    // propagating through expressions
    @Test
    public void testFunction11() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 9);
    }

    // 4.2.1 optimizer evaluation
    // extended basic blocks
    @Test
    public void testFunction12() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 12478);
    }

    // 4.2.1 optimizer evaluation
    // extended basic blocks
    @Test
    public void testFunction13() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == -12212);
    }

    // 4.2.2 dominators
    @Test
    public void testFunction14() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 5+21+15);
    }

    // 4.2.2 dominators
    @Test
    public void testFunction15() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 5*15+5*21);
    }

    // 4.2.3 DAGs
    @Test
    public void testFunction16() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 1+10+5+21);
    }

    // 4.2.3 DAGs
    // Doesn't give the outcome
    @Test
    public void testFunction17() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 1+4+210);
    }

    // 4.2.4 Loops
    @Test
    public void testFunction18() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 2+21+2);
    }

    // 4.3 Conditional constants
    @Test
    public void testFunction19() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 213+3);
    }

    // 4.4 Conditional based assertions
    @Test
    public void testFunction20() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 5+(5*21+25/5));
    }


    @Test
    public void testFunction100() {
        String src = """
                struct Test
                {
                    var field: Int
                }
                func foo()->Test?
                {
                    return null;
                }

                """;
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.NullValue);
    }

    @Test
    public void testFunction101() {
        String src = """
                func foo()->Int
                {
                    return null == null;
                }

                """;
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction102() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction103() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction104() {
        String src = """
                func foo()->Int
                {
                    return 1 == 1 && 2 == 2
                }

                """;
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction105() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 0);
    }

    @Test
    public void testFunction106() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction107() {
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
        var value = compileAndRun(src, "foo", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testMergeSort() {
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
        var value = compileAndRun(src, "main", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction108() {
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
        var value = compileAndRun(src, "main", Options.OPT);
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 0);
    }

    @Test
    public void testFunction109() {
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
        var value = compileAndRun(src, "main");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testFunction110() {
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
        var value = compileAndRun(src, "main");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test(expected = InterpreterException.class)
    public void testFloatConstantBranchConditionRejectedByInterpreter() {
        String src = """
                func foo()->Int {
                    if (1)
                        return 1;
                    return 0;
                }
                """;
        var compiler = new Compiler();
        var typeDict = compiler.compileSrc(src, Options.NONE);
        var functionSymbol = (Symbol.FunctionTypeSymbol) typeDict.lookup("foo");
        var function = (CompiledFunction) functionSymbol.code();
        for (int i = 0; i < function.entry.instructions.size(); i++) {
            if (function.entry.instructions.get(i) instanceof Instruction.ConditionalBranch cbr) {
                function.entry.instructions.set(i, new Instruction.ConditionalBranch(
                        function.entry,
                        new Operand.FloatConstantOperand(1.0, typeDict.FLOAT),
                        cbr.trueBlock,
                        cbr.falseBlock));
                break;
            }
        }
        new Interpreter(typeDict).run("foo");
    }
    @Test
    public void testFloatArithmetic() {
        String src = """
                func add(a: Float, b: Float)->Float {
                    return a+b;
                }
                func foo()->Float {
                    return add(1.25,2.5);
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.FloatValue floatValue
                && Math.abs(floatValue.value - 3.75) < 0.000001);
    }

    @Test
    public void testFloatArray() {
        String src = """
                func foo()->Float {
                    var t = new [Float] {1.0,2.25,3.5};
                    t[1] = t[1] + 1.25;
                    return t[1];
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.FloatValue floatValue
                && Math.abs(floatValue.value - 3.5) < 0.000001);
    }

    // A float value flows through an if/else phi, so SSA destruction (the
    // Briggs path under the OPT presets) must copy a float phi operand
    // correctly. choose(0)=2.5, choose(1)=1.5 -> 4.0.
    @Test
    public void testFloatPhiThroughSSADestruction() {
        String src = """
                func choose(c: Int)->Float {
                    var x = 1.5;
                    if (c == 0)
                        x = 2.5;
                    return x;
                }
                func foo()->Float {
                    return choose(0) + choose(1);
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.FloatValue floatValue
                && Math.abs(floatValue.value - 4.0) < 0.000001);
    }

    // A null value flows through an if/else phi, so SSA destruction must copy
    // a null phi operand correctly. choose(0)=null, choose(1)=not-null.
    @Test
    public void testNullPhiThroughSSADestruction() {
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
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 1);
    }

    @Test
    public void testFunction111() {
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
    var nums = new [Float]{33.4, 10.2, 55.1, 71.9, 29.9, 3.5};
    var expected = new [Float]{3.5,10.2,29.9,33.4,55.1,71.9}
    quicksort(nums, 0, 5);
    return eq(nums,expected,6)
}
""";
        var value = compileAndRun(src, "main");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue &&
                integerValue.value == 1);
    }

    @Test
    public void testArrayLengthUnaryOperator() {
        String src = """
                func foo()->Int {
                    var a = new [Int] {1,2,3,4}
                    var b = new [Int] {len=0,value=0}
                    return #a + #b
                }
                """;
        var value = compileAndRun(src, "foo");
        Assert.assertNotNull(value);
        Assert.assertTrue(value instanceof Value.IntegerValue integerValue
                && integerValue.value == 4);
    }
}
