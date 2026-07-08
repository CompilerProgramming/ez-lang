package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.types.Symbol;
import org.junit.Assert;
import org.junit.Test;

import java.util.BitSet;
import java.util.EnumSet;

public class TestSCCP {

    String compileSrc(String src) {
        var compiler = new Compiler();
        var typeDict = compiler.compileSrc(src);
        StringBuilder sb = new StringBuilder();
        var options = Options.NONE;
        for (Symbol s : typeDict.bindings.values()) {
            if (s instanceof Symbol.FunctionTypeSymbol f) {
                var functionBuilder = (CompiledFunction) f.code();
                new EnterSSA(functionBuilder, options);
                BasicBlock.toStr(sb, functionBuilder.entry, new BitSet(), false);
                //functionBuilder.toDot(sb, false);
                var sccp = new SparseConditionalConstantPropagation().constantPropagation(functionBuilder);
                sb.append(sccp.toString());
                sccp.apply(options);
                sb.append("After SCCP changes:\n");
                functionBuilder.toStr(sb, false);
            }
        }
        return sb.toString();
    }

    @Test
    public void test1() {
        String src = """
func foo()->Int {
    var i = 1
    if (i == 0)
        i = 2
    else
        i = 3
    return i
}            
""";
        String actual = compileSrc(src);
        String expected = """
L0:
    i_0 = 1
    %t1_0 = i_0==0
    if %t1_0 goto L2 else goto L3
L2:
    i_2 = 2
    goto  L4
L4:
    i_3 = phi(i_2, i_1)
    ret i_3
    goto  L1
L1:
L3:
    i_1 = 3
    goto  L4
Flow edges:
L0->L2=NOT Executable
L0->L3=Executable
L4->L1=Executable
L2->L4=NOT Executable
L3->L4=Executable
Lattices:
i_0=1
%t1_0=0
i_1=3
i_3=3
After SCCP changes:
L0:
    goto  L3
L3:
    goto  L4
L4:
    ret 3
    goto  L1
L1:
""";
        Assert.assertEquals(expected, actual);
    }

    // 19.4 in MCIC Appel
    // Expected results are based on fig 19.13 page 456
    @Test
    public void test2() {
        String src = """
func foo()->Int {
    var i = 1
    var j = 1
    var k = 0
    while (k < 100) {
        if (j < 20) {
            j = i
            k = k + 1
        }
        else {
            j = k
            k = k + 2
        }
    }
    return j
}
""";
        String actual = compileSrc(src);
        String expected = """
L0:
    i_0 = 1
    j_0 = 1
    k_0 = 0
    goto  L2
L2:
    k_1 = phi(k_0, k_4)
    j_1 = phi(j_0, j_4)
    %t3_0 = k_1<100
    if %t3_0 goto L3 else goto L4
L3:
    %t4_0 = j_1<20
    if %t4_0 goto L5 else goto L6
L5:
    j_3 = i_0
    %t5_0 = k_1+1
    k_3 = %t5_0
    goto  L7
L7:
    k_4 = phi(k_3, k_2)
    j_4 = phi(j_3, j_2)
    goto  L2
L6:
    j_2 = k_1
    %t6_0 = k_1+2
    k_2 = %t6_0
    goto  L7
L4:
    ret j_1
    goto  L1
L1:
Flow edges:
L0->L2=Executable
L4->L1=Executable
L2->L3=Executable
L2->L4=Executable
L3->L5=Executable
L7->L2=Executable
L3->L6=NOT Executable
L5->L7=Executable
L6->L7=NOT Executable
Lattices:
j_3=1
%t5_0=int*
k_3=int*
k_4=int*
j_4=1
i_0=1
j_0=1
k_0=0
k_1=int*
j_1=1
%t3_0=int*
%t4_0=1
After SCCP changes:
L0:
    goto  L2
L2:
    k_1 = phi(0, k_4)
    %t3_0 = k_1<100
    if %t3_0 goto L3 else goto L4
L3:
    goto  L5
L5:
    %t5_0 = k_1+1
    k_3 = %t5_0
    goto  L7
L7:
    k_4 = phi(k_3)
    goto  L2
L4:
    ret 1
    goto  L1
L1:
""";
        Assert.assertEquals(expected, actual);
    }

    // Exercises float constant propagation: a and b are variables (not
    // front-end folded), so SCCP must propagate the float constants and
    // fold a+b to 3.75, replacing the return operand.
    @Test
    public void testFloatConstantPropagation() {
        String src = """
func foo()->Float {
    var a = 1.25
    var b = 2.5
    return a + b
}
""";
        String actual = compileSrc(src);
        Assert.assertTrue("expected folded float return in:\n" + actual,
                actual.contains("ret 3.75"));
    }

    // Exercises the ref lattice's null-constant comparison folding
    // (evalLogical: null == null -> 1). The comparison is not folded by the
    // front end, so it reaches SCCP as a Binary over two null operands.
    @Test
    public void testNullConstantComparisonFolding() {
        String src = """
func foo()->Int {
    return null == null
}
""";
        String actual = compileSrc(src);
        Assert.assertTrue("expected folded null comparison in:\n" + actual,
                actual.contains("ret 1"));
    }

    // Exercises the ref lattice tracking a freshly allocated struct as
    // not-null (NewStruct -> F_REF_NOT_NULL), which prints as "not-null"
    // in the lattice dump.
    @Test
    public void testNewStructIsNotNull() {
        String src = """
struct Foo
{
    var i: Int
}
func foo()->Int
{
    var f = new Foo{ i = 1 }
    return f.i
}
""";
        String actual = compileSrc(src);
        Assert.assertTrue("expected a not-null lattice entry in:\n" + actual,
                actual.contains("not-null"));
    }

    // Exercises not-null vs null comparison folding in evalLogical: a freshly
    // allocated (not-null) struct compared to null folds the condition to
    // false, so the dead 'return 0' branch is eliminated after SCCP.
    @Test
    public void testNotNullComparedToNullFolds() {
        String src = """
struct Foo
{
    var i: Int
}
func foo()->Int
{
    var f: Foo?
    f = new Foo{ i = 1 }
    if (f == null)
        return 0
    return 1
}
""";
        String actual = compileSrc(src);
        String postSccp = actual.substring(actual.indexOf("After SCCP changes:"));
        Assert.assertFalse("dead 'return 0' branch should be folded away:\n" + actual,
                postSccp.contains("ret 0"));
        Assert.assertTrue("reachable 'return 1' should remain:\n" + actual,
                postSccp.contains("ret 1"));
    }
}
