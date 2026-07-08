package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;
import org.junit.Assert;
import org.junit.Test;

public class TestInterferenceGraph {

    private CompiledFunction buildTest1() {
        TypeDictionary typeDictionary = new TypeDictionary();
        EZType.EZTypeFunction functionType = new EZType.EZTypeFunction("foo");
        var argSymbol = new Symbol.ParameterSymbol("a", typeDictionary.INT);
        functionType.addArg(argSymbol);
        functionType.setReturnType(typeDictionary.INT);
        CompiledFunction function = new CompiledFunction(functionType, typeDictionary);
        RegisterPool regPool = function.registerPool;
        Register a = regPool.newReg("a", typeDictionary.INT);
        Register b = regPool.newReg("b", typeDictionary.INT);
        Register c = regPool.newReg("c", typeDictionary.INT);
        Register d = regPool.newReg("d", typeDictionary.INT);
        function.code(new Instruction.ArgInstruction(new Operand.LocalRegisterOperand(a, argSymbol)));
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(a),
                new Operand.RegisterOperand(b),
                new Operand.IntConstantOperand(1, typeDictionary.INT)));
        function.code(new Instruction.Binary(
                "*",
                new Operand.RegisterOperand(c),
                new Operand.RegisterOperand(b),
                new Operand.RegisterOperand(b)));
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(b),
                new Operand.RegisterOperand(c),
                new Operand.IntConstantOperand(1, typeDictionary.INT)));
        function.code(new Instruction.Binary(
                "*",
                new Operand.RegisterOperand(d),
                new Operand.RegisterOperand(b),
                new Operand.RegisterOperand(a)));
        function.code(new Instruction.Ret(new Operand.RegisterOperand(d)));
        function.startBlock(function.exit);
        function.isSSA = false;

        System.out.println(function.toStr(new StringBuilder(), true));

        return function;
    }

    @Test
    public void test1() {
        CompiledFunction function = buildTest1();
        var graph = new InterferenceGraphBuilder().build(function);
        System.out.println(graph.generateDotOutput());
        var edges = graph.getEdges();
        Assert.assertEquals(2, edges.size());
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 1)));
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 2)));
    }

    /*
     Engineering a Compiler, 2nd ed, page 700

     B0
       a = 1
       if a B1 else B2
     B1
       b = 2
       d = b
       goto B3
     B2
       c = 1
       d = c
       goto B3
     B3
       t = a+d

     */
    private CompiledFunction buildTest2() {
        TypeDictionary typeDictionary = new TypeDictionary();
        EZType.EZTypeFunction functionType = new EZType.EZTypeFunction("foo");
        functionType.setReturnType(typeDictionary.VOID);
        CompiledFunction function = new CompiledFunction(functionType, typeDictionary);
        RegisterPool regPool = function.registerPool;
        Register a = regPool.newReg("a", typeDictionary.INT);
        Register b = regPool.newReg("b", typeDictionary.INT);
        Register c = regPool.newReg("c", typeDictionary.INT);
        Register d = regPool.newReg("d", typeDictionary.INT);
        Register t = regPool.newReg("t", typeDictionary.INT);
        BasicBlock b1 = function.createBlock();
        BasicBlock b2 = function.createBlock();
        BasicBlock b3 = function.createBlock();

        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(a)));
        function.code(new Instruction.ConditionalBranch(
                function.currentBlock,
                new Operand.RegisterOperand(a),
                b1, b2));
        function.currentBlock.addSuccessor(b1);
        function.currentBlock.addSuccessor(b2);
        function.startBlock(b1);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(2, typeDictionary.INT),
                new Operand.RegisterOperand(b)));
        function.code(new Instruction.Move(
                new Operand.RegisterOperand(b),
                new Operand.RegisterOperand(d)));
        function.jumpTo(b3);
        function.startBlock(b2);
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(c)));
        function.code(new Instruction.Move(
                new Operand.RegisterOperand(c),
                new Operand.RegisterOperand(d)));
        function.jumpTo(b3);
        function.startBlock(b3);
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(t),
                new Operand.RegisterOperand(a),
                new Operand.RegisterOperand(d)));
        function.startBlock(function.exit);
        function.isSSA = false;

        System.out.println(function.toStr(new StringBuilder(), true));

        return function;
    }

    @Test
    public void test2() {
        CompiledFunction function = buildTest2();
        var graph = new InterferenceGraphBuilder().build(function);
        System.out.println(graph.generateDotOutput());
        var edges = graph.getEdges();
        Assert.assertEquals(3, edges.size());
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 1)));
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 2)));
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 1)));
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 3)));
    }

    @Test
    public void test3() {
        CompiledFunction function = TestLiveness.buildTest3();
        var graph = new InterferenceGraphBuilder().build(function);
        System.out.println(graph.generateDotOutput());
        var edges = graph.getEdges();
        Assert.assertEquals(1, edges.size());
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 1)));
    }

    /* Test move does not interfere with uses */
    public static CompiledFunction buildTest4() {
        TypeDictionary typeDictionary = new TypeDictionary();
        EZType.EZTypeFunction functionType = new EZType.EZTypeFunction("foo");
        functionType.setReturnType(typeDictionary.VOID);
        CompiledFunction function = new CompiledFunction(functionType, typeDictionary);
        RegisterPool regPool = function.registerPool;
        Register a = regPool.newReg("a", typeDictionary.INT);
        Register b = regPool.newReg("b", typeDictionary.INT);
        Register c = regPool.newReg("c", typeDictionary.INT);
        Register t = regPool.newReg("t", typeDictionary.INT);

        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(1, typeDictionary.INT),
                new Operand.RegisterOperand(a)));
        function.code(new Instruction.Move(
                new Operand.IntConstantOperand(2, typeDictionary.INT),
                new Operand.RegisterOperand(b)));
        function.code(new Instruction.Move(
                new Operand.RegisterOperand(b),
                new Operand.RegisterOperand(c)));
        function.code(new Instruction.Binary(
                "+",
                new Operand.RegisterOperand(t),
                new Operand.RegisterOperand(c),
                new Operand.RegisterOperand(a)));
        function.startBlock(function.exit);
        function.isSSA = false;

        System.out.println(function.toStr(new StringBuilder(), true));

        return function;
    }

    /* Test move does not interfere with uses */
    @Test
    public void test4() {
        CompiledFunction function = buildTest4();
        var graph = new InterferenceGraphBuilder().build(function);
        System.out.println(graph.generateDotOutput());
        var edges = graph.getEdges();
        Assert.assertEquals(2, edges.size());
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 1)));
        Assert.assertTrue(edges.contains(new InterferenceGraph.Edge(0, 2)));
    }

    @Test
    public void test5() {
        InterferenceGraph graph = new InterferenceGraph();
        graph.addEdge(1, 2);
        Assert.assertTrue(graph.interfere(1, 2));
        Assert.assertTrue(graph.interfere(2, 1));
        Assert.assertTrue(graph.neighbors(1).contains(2));
        Assert.assertTrue(graph.neighbors(2).contains(1));
    }

    @Test
    public void test6() {
        InterferenceGraph graph = new InterferenceGraph();
        graph.addEdge(1, 2);
        graph.addEdge(1, 3);
        Assert.assertTrue(graph.interfere(1, 2));
        Assert.assertTrue(graph.interfere(2, 1));
        Assert.assertTrue(graph.interfere(1, 3));
        Assert.assertTrue(graph.interfere(3, 1));
        Assert.assertFalse(graph.interfere(2, 3));
        Assert.assertFalse(graph.interfere(3, 2));
        Assert.assertTrue(graph.neighbors(1).contains(2));
        Assert.assertTrue(graph.neighbors(1).contains(3));
        Assert.assertTrue(graph.neighbors(2).contains(1));
        Assert.assertTrue(graph.neighbors(3).contains(1));
        System.out.println(graph.generateDotOutput());
        graph.rename(2, 3);
        System.out.println(graph.generateDotOutput());
        Assert.assertFalse(graph.interfere(1, 2));
        Assert.assertFalse(graph.interfere(2, 1));
        Assert.assertTrue(graph.interfere(1, 3));
        Assert.assertTrue(graph.interfere(3, 1));
        Assert.assertFalse(graph.interfere(2, 3));
        Assert.assertFalse(graph.interfere(3, 2));
        Assert.assertFalse(graph.neighbors(1).contains(2));
        Assert.assertTrue(graph.neighbors(1).contains(3));
        Assert.assertTrue(graph.neighbors(3).contains(1));
    }

    @Test
    public void test7() {
        InterferenceGraph graph = new InterferenceGraph();
        graph.addEdge(1, 2);
        graph.addEdge(1, 3);
        Assert.assertTrue(graph.interfere(1, 2));
        Assert.assertTrue(graph.interfere(2, 1));
        Assert.assertTrue(graph.interfere(1, 3));
        Assert.assertTrue(graph.interfere(3, 1));
        Assert.assertFalse(graph.interfere(2, 3));
        Assert.assertFalse(graph.interfere(3, 2));
        Assert.assertTrue(graph.neighbors(1).contains(2));
        Assert.assertTrue(graph.neighbors(1).contains(3));
        Assert.assertTrue(graph.neighbors(2).contains(1));
        Assert.assertTrue(graph.neighbors(3).contains(1));
        System.out.println(graph.generateDotOutput());
        graph.rename(1, 2);
        System.out.println(graph.generateDotOutput());
        Assert.assertFalse(graph.interfere(1, 2));
        Assert.assertFalse(graph.interfere(2, 1));
        Assert.assertFalse(graph.interfere(1, 3));
        Assert.assertFalse(graph.interfere(3, 1));
        Assert.assertTrue(graph.interfere(2, 3));
        Assert.assertTrue(graph.interfere(3, 2));
        Assert.assertTrue(graph.neighbors(2).contains(3));
        Assert.assertTrue(graph.neighbors(3).contains(2));
    }

}
