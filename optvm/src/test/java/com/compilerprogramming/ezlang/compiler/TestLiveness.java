package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestLiveness {

    TypeDictionary compileSrc(String src) {
        var compiler = new Compiler();
        return  compiler.compileSrc(src);
    }

    @Test
    public void test1() {
        String src = """
                func print(n: Int) {}
                func foo() {
                    var i = 1
                    var s = 1;
                    while (1) {
                        if (i == 5)
                            s = 0;
                        s = s + 1
                        i = i + 1
                        if (i < 10)
                            continue;
                        break;
                    }
                    print(s);
                }
                """;
        var typeDict = compileSrc(src);
        var funcSymbol = typeDict.lookup("foo");
        CompiledFunction func = (CompiledFunction) ((Symbol.FunctionTypeSymbol)funcSymbol).code();
        func.livenessAnalysis();
        String output = Compiler.dumpIR(typeDict, true);
        Assert.assertEquals("""
func print(n: Int)
Reg #0 n 0
L0:
    arg n
    goto  L1
L1:
func foo()
Reg #0 i 0
Reg #1 s 1
Reg #2 %t2 2
Reg #3 %t3 3
Reg #4 %t4 4
Reg #5 %t5 5
Reg #6 %t6 6
L0:
    i = 1
    s = 1
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0, 1}
    #LIVEIN  = {}
    #LIVEOUT = {0, 1}
L2:
    if 1 goto L3 else goto L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L3:
    %t2 = i==5
    if %t2 goto L5 else goto L6
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0}
    #VARKILL = {2}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L5:
    s = 0
    goto  L6
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {1}
    #LIVEIN  = {0}
    #LIVEOUT = {0, 1}
L6:
    %t3 = s+1
    s = %t3
    %t4 = i+1
    i = %t4
    %t5 = i<10
    if %t5 goto L7 else goto L8
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0, 1}
    #VARKILL = {0, 1, 3, 4, 5}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L7:
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L8:
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {1}
    #LIVEOUT = {1}
L4:
    %t6 = s
    call print params %t6
    goto  L1
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {1}
    #VARKILL = {6}
    #LIVEIN  = {1}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
""", output);
    }

    @Test
    public void test2() {
        String src = """
                func foo(a: Int, b: Int) {
                    while (b < 10) {
                        if (b < a) {
                            a = a * 7
                            b = a + 1
                        }
                        else {
                            a = b - 1
                        }
                    }
                }
                """;
        var typeDict = compileSrc(src);
        var funcSymbol = typeDict.lookup("foo");
        CompiledFunction func = (CompiledFunction) ((Symbol.FunctionTypeSymbol)funcSymbol).code();
        func.livenessAnalysis();
        String output = Compiler.dumpIR(typeDict, true);
        Assert.assertEquals("""
func foo(a: Int,b: Int)
Reg #0 a 0
Reg #1 b 1
Reg #2 %t2 2
Reg #3 %t3 3
Reg #4 %t4 4
Reg #5 %t5 5
Reg #6 %t6 6
L0:
    arg a
    arg b
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0, 1}
    #LIVEIN  = {}
    #LIVEOUT = {0, 1}
L2:
    %t2 = b<10
    if %t2 goto L3 else goto L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {1}
    #VARKILL = {2}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L3:
    %t3 = b<a
    if %t3 goto L5 else goto L6
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0, 1}
    #VARKILL = {3}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L5:
    %t4 = a*7
    a = %t4
    %t5 = a+1
    b = %t5
    goto  L7
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0}
    #VARKILL = {0, 1, 4, 5}
    #LIVEIN  = {0}
    #LIVEOUT = {0, 1}
L7:
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L6:
    %t6 = b-1
    a = %t6
    goto  L7
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {1}
    #VARKILL = {0, 6}
    #LIVEIN  = {1}
    #LIVEOUT = {0, 1}
L4:
    goto  L1
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
""", output);
    }

    /* page 448 Engineering a Compiler */
    static CompiledFunction buildTest3() {
        TypeDictionary typeDictionary = new TypeDictionary();
        EZType.EZTypeFunction functionType = new EZType.EZTypeFunction("foo");
        functionType.setReturnType(typeDictionary.INT);
        CompiledFunction function = new CompiledFunction(functionType, typeDictionary);
        RegisterPool regPool = function.registerPool;
        Register i = regPool.newReg("i", typeDictionary.INT);
        Register s = regPool.newReg("s", typeDictionary.INT);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(i)));
        BasicBlock b1 = function.createBlock();
        BasicBlock b2 = function.createBlock();
        BasicBlock b3 = function.createBlock();
        BasicBlock b4 = function.createBlock();
        function.jumpTo(b1);
        function.startBlock(b1);
        function.code(new Instruction.ConditionalBranch(
                function.currentBlock,
                new Operand.RegisterOperand(i),
                b2, b3));
        function.currentBlock.addSuccessor(b2);
        function.currentBlock.addSuccessor(b3);
        function.startBlock(b2);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(0, typeDictionary.INT),
                new Operand.RegisterOperand(s)));
        function.jumpTo(b3);
        function.startBlock(b3);
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(s),
                new Operand.RegisterOperand(s),
                new Operand.RegisterOperand(i)));
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(i),
                new Operand.RegisterOperand(i),
                new Operand.IntConstantOperand(1, typeDictionary.INT)));
        function.code(new Instruction.ConditionalBranch(
                function.currentBlock,
                new Operand.RegisterOperand(i),
                b1, b4));
        function.currentBlock.addSuccessor(b1);
        function.currentBlock.addSuccessor(b4);
        function.startBlock(b4);
        function.code(new Instruction.Ret(new Operand.RegisterOperand(s)));
        function.startBlock(function.exit);
        function.isSSA = false;

        System.out.println(function.toStr(new StringBuilder(), true));

        return function;
    }

    @Test
    public void test3() {
        CompiledFunction function = buildTest3();
        function.livenessAnalysis();
        String actual = function.toStr(new StringBuilder(), true).toString();
        Assert.assertEquals("""
func foo()->Int
Reg #0 i 0
Reg #1 s 1
L0:
    i = 1
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0}
    #LIVEIN  = {1}
    #LIVEOUT = {0, 1}
L2:
    if i goto L3 else goto L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0}
    #VARKILL = {}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L3:
    s = 0
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {1}
    #LIVEIN  = {0}
    #LIVEOUT = {0, 1}
L4:
    s = s+i
    i = i+1
    if i goto L2 else goto L5
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0, 1}
    #VARKILL = {0, 1}
    #LIVEIN  = {0, 1}
    #LIVEOUT = {0, 1}
L5:
    ret s
    goto  L1
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {1}
    #VARKILL = {}
    #LIVEIN  = {1}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
""", actual);
    }

    // See not on SSA and liveness in Liveness.
    @Test
    public void testSwapProblem() {
        CompiledFunction function = TestSSATransform.buildSwapTest();
        function.livenessAnalysis();
        String actual = function.toStr(new StringBuilder(), true).toString();
        Assert.assertEquals("""
func foo(p: Int)
Reg #0 p 0
Reg #1 a1 1
Reg #2 a2 2
Reg #3 b1 3
Reg #4 b2 4
L0:
    arg p
    a1 = 42
    b1 = 24
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {1, 3}
    #UEVAR   = {}
    #VARKILL = {0, 1, 3}
    #LIVEIN  = {}
    #LIVEOUT = {0, 1, 3}
L2:
    a2 = phi(a1, b2)
    b2 = phi(b1, a2)
    if p goto L2 else goto L1
    #PHIDEFS = {2, 4}
    #PHIUSES = {}
    #UEVAR   = {0}
    #VARKILL = {}
    #LIVEIN  = {0, 2, 4}
    #LIVEOUT = {0}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
""", actual);
    }

        @Test
    public void testLostCopyProblem() {
        CompiledFunction function = TestSSATransform.buildLostCopyTest();
        function.livenessAnalysis();
        String actual = function.toStr(new StringBuilder(), true).toString();
        Assert.assertEquals("""
func foo(p: Int)->Int
Reg #0 p 0
Reg #1 x1 1
Reg #2 x3 2
Reg #3 x2 3
L0:
    arg p
    x1 = 1
    goto  L2
    #PHIDEFS = {}
    #PHIUSES = {1}
    #UEVAR   = {}
    #VARKILL = {0, 1}
    #LIVEIN  = {}
    #LIVEOUT = {0, 1}
L2:
    x2 = phi(x1, x3)
    x3 = x2+1
    if p goto L2 else goto L1
    #PHIDEFS = {3}
    #PHIUSES = {2}
    #UEVAR   = {0, 3}
    #VARKILL = {2}
    #LIVEIN  = {0, 3}
    #LIVEOUT = {0, 2, 3}
L1:
    ret x2
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {3}
    #VARKILL = {}
    #LIVEIN  = {3}
    #LIVEOUT = {}
""", actual);
    }

    @Test
    public void testSimpleCase() {
        String src = """
                func foo()->Int 
                {
                    return 1 && 2
                }
                """;
        var typeDict = compileSrc(src);
        var funcSymbol = typeDict.lookup("foo");
        CompiledFunction func = (CompiledFunction) ((Symbol.FunctionTypeSymbol) funcSymbol).code();
        func.livenessAnalysis();
        StringBuilder result = new StringBuilder();
        result.append("Pre-SSA\n");
        func.toStr(result, true);
        new EnterSSA(func, Options.NONE);
        func.livenessAnalysis();
        result.append("Post-SSA\n");
        func.toStr(result, true);
        Assert.assertEquals("""
Pre-SSA
func foo()->Int
Reg #0 %t0 0
L0:
    if 1 goto L2 else goto L3
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L2:
    %t0 = 2
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0}
    #LIVEIN  = {}
    #LIVEOUT = {0}
L4:
    ret %t0
    goto  L1
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {0}
    #VARKILL = {}
    #LIVEIN  = {0}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L3:
    %t0 = 0
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0}
    #LIVEIN  = {}
    #LIVEOUT = {0}
Post-SSA
func foo()->Int
Reg #0 %t0 0
Reg #1 %t0_0 0
Reg #2 %t0_1 0
Reg #3 %t0_2 0
L0:
    if 1 goto L2 else goto L3
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L2:
    %t0_1 = 2
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {2}
    #UEVAR   = {}
    #VARKILL = {2}
    #LIVEIN  = {}
    #LIVEOUT = {2}
L4:
    %t0_2 = phi(%t0_1, %t0_0)
    ret %t0_2
    goto  L1
    #PHIDEFS = {3}
    #PHIUSES = {}
    #UEVAR   = {3}
    #VARKILL = {}
    #LIVEIN  = {3}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L3:
    %t0_0 = 0
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {1}
    #UEVAR   = {}
    #VARKILL = {1}
    #LIVEIN  = {}
    #LIVEOUT = {1}
""", result.toString());
    }

    // From discussion
    // https://www.reddit.com/r/Compilers/comments/1qhfbqh/liveness_analysis_correctness/
    static CompiledFunction buildTestReddit() {
        TypeDictionary typeDictionary = new TypeDictionary();
        EZType.EZTypeFunction functionType = new EZType.EZTypeFunction("foo");
        functionType.setReturnType(typeDictionary.INT);
        CompiledFunction function = new CompiledFunction(functionType, typeDictionary);
        RegisterPool regPool = function.registerPool;
        Register v0_0 = regPool.newReg("v0_0", typeDictionary.INT);
        Register v0_1 = regPool.newReg("v0_1", typeDictionary.INT);
        Register v0_2 = regPool.newReg("v0_2", typeDictionary.INT);
        Register v1_0 = regPool.newReg("v1_0", typeDictionary.INT);
        Register v1_1 = regPool.newReg("v1_1", typeDictionary.INT);
        Register v1_2 = regPool.newReg("v1_2", typeDictionary.INT);
        Register v2_0 = regPool.newReg("v2_0", typeDictionary.INT);
        Register v2_1 = regPool.newReg("v2_1", typeDictionary.INT);
        Register v2_2 = regPool.newReg("v2_2", typeDictionary.INT);
        Register v3_0 = regPool.newReg("v3_0", typeDictionary.INT);
        Register v4_0 = regPool.newReg("v4_0", typeDictionary.INT);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(47, typeDictionary.INT),
                new Operand.RegisterOperand(v0_0)));
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(42, typeDictionary.INT),
                new Operand.RegisterOperand(v1_0)));
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(v3_0)));
        BasicBlock b1 = function.createBlock();
        BasicBlock b2 = function.createBlock();
        BasicBlock b3 = function.createBlock();
        BasicBlock b4 = function.createBlock();
        function.code(new Instruction.ConditionalBranch(
                function.currentBlock,
                new Operand.RegisterOperand(v3_0),
                b1, b2));
        function.currentBlock.addSuccessor(b1);
        function.currentBlock.addSuccessor(b2);
        function.startBlock(b1);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(v1_1)));
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(5, typeDictionary.INT),
                new Operand.RegisterOperand(v2_0)));
        function.jumpTo(b3);

        function.startBlock(b2);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(2, typeDictionary.INT),
                new Operand.RegisterOperand(v0_2)));
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(10, typeDictionary.INT),
                new Operand.RegisterOperand(v2_2)));
        function.jumpTo(b3);

        function.startBlock(b3);
        function.code(new Instruction.Phi(v2_1, Arrays.asList(v2_0, v2_2)));
        function.code(new Instruction.Phi(v1_2, Arrays.asList(v1_1, v1_0)));
        function.code(new Instruction.Phi(v0_1, Arrays.asList(v0_0, v0_2)));
        function.code(new Instruction.Binary(
                "-",
                new Operand.RegisterOperand(v4_0),
                new Operand.RegisterOperand(v0_1),
                new Operand.RegisterOperand(v2_1)));

        function.jumpTo(b4);
        function.startBlock(b4);
        function.code(new Instruction.Ret(new Operand.RegisterOperand(v4_0)));
        function.startBlock(function.exit);
        function.isSSA = true;

        System.out.println(function.toStr(new StringBuilder(), true));

        return function;
    }

    @Test
    public void testReddit() {
        CompiledFunction function = buildTestReddit();
        function.livenessAnalysis();
        String actual = function.toStr(new StringBuilder(), true).toString();
        Assert.assertEquals("""
func foo()->Int
Reg #0 v0_0 0
Reg #1 v0_1 1
Reg #2 v0_2 2
Reg #3 v1_0 3
Reg #4 v1_1 4
Reg #5 v1_2 5
Reg #6 v2_0 6
Reg #7 v2_1 7
Reg #8 v2_2 8
Reg #9 v3_0 9
Reg #10 v4_0 10
L0:
    v0_0 = 47
    v1_0 = 42
    v3_0 = 1
    if v3_0 goto L2 else goto L3
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {0, 3, 9}
    #LIVEIN  = {}
    #LIVEOUT = {0, 3}
L2:
    v1_1 = 1
    v2_0 = 5
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {0, 4, 6}
    #UEVAR   = {}
    #VARKILL = {4, 6}
    #LIVEIN  = {0}
    #LIVEOUT = {0, 4, 6}
L4:
    v2_1 = phi(v2_0, v2_2)
    v1_2 = phi(v1_1, v1_0)
    v0_1 = phi(v0_0, v0_2)
    v4_0 = v0_1-v2_1
    goto  L5
    #PHIDEFS = {1, 5, 7}
    #PHIUSES = {}
    #UEVAR   = {1, 7}
    #VARKILL = {10}
    #LIVEIN  = {1, 5, 7}
    #LIVEOUT = {10}
L5:
    ret v4_0
    goto  L1
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {10}
    #VARKILL = {}
    #LIVEIN  = {10}
    #LIVEOUT = {}
L1:
    #PHIDEFS = {}
    #PHIUSES = {}
    #UEVAR   = {}
    #VARKILL = {}
    #LIVEIN  = {}
    #LIVEOUT = {}
L3:
    v0_2 = 2
    v2_2 = 10
    goto  L4
    #PHIDEFS = {}
    #PHIUSES = {2, 3, 8}
    #UEVAR   = {}
    #VARKILL = {2, 8}
    #LIVEIN  = {3}
    #LIVEOUT = {2, 3, 8}
""", actual);
    }

}
