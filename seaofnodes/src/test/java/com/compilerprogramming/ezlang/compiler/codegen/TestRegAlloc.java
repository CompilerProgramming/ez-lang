package com.compilerprogramming.ezlang.compiler.codegen;

import com.compilerprogramming.ezlang.compiler.node.*;
import com.compilerprogramming.ezlang.compiler.node.cpus.x86_64_v2.x86_64_v2;
import com.compilerprogramming.ezlang.compiler.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Register allocator unit tests, ported from upstream Simple's
 * RegAllocTestSupport.  Small hand-scheduled machine graphs isolate register
 * constraints from the optimizer.
 */
public class TestRegAlloc {
    private static final RegMask A = new RegMask(0), B = new RegMask(1);

    private static class Op extends MachConcreteNode {
        final RegMask _def, _use, _kill;
        final boolean _clone;
        Op(RegMask def, RegMask use, RegMask kill, boolean clone, Node... ins) {
            super(ins); _def=def; _use=use; _kill=kill; _clone=clone; _type=TypeInteger.BOT;
        }
        @Override public String op() { return "testOp"; }
        @Override public RegMask outregmap() { return _def; }
        @Override public RegMask regmap(int i) { return _use; }
        @Override public RegMask killmap() { return _kill; }
        @Override public boolean isClone() { return _clone; }
        @Override public Node copy() { return new Op(_def,_use,_kill,_clone,(Node)null); }
        @Override public void encoding(Encoding enc) { throw new AssertionError(); }
    }

    private static SplitNode testSplit(CodeGen code, MachNode def) {
        LRG lrg = new LRG((short)0);
        lrg._machDef = def;
        return code._mach.split("test",(byte)0,lrg);
    }

    private static CodeGen graph() {
        CodeGen code = new CodeGen("");
        code._mach = new x86_64_v2(code);
        code._cfg.add(code._start);
        return code;
    }

    // RegMask.nextReg across the 64-bit word boundary
    @Test
    public void testMasks() {
        for( int reg : new int[]{0,63,64,65,127} ) {
            RegMask mask = new RegMask(reg);
            assertEquals(reg,mask.firstReg());
            assertEquals(1,mask.size());
        }
        assertEquals(-1,new RegMask(0L).firstReg());
        assertFalse(new RegMask(0L).size1());
        RegMask boundary = new RegMask(Long.MIN_VALUE,Long.MIN_VALUE|1);
        assertEquals(64,boundary.nextReg((short)63));
        assertEquals(127,boundary.nextReg((short)64));
        assertEquals(-1,boundary.nextReg((short)127));
    }

    // An instruction that kills a register without producing a value
    @Test
    public void testKillWithoutResult() {
        CodeGen code = graph();
        Op def = new Op(A,null,null,false,code._start);
        new Op(null,null,A,false,code._start); // Clobbers A, produces no value.
        new Op(null,A,null,false,code._start,def);
        RegAlloc alloc = new RegAlloc(code);
        assertTrue(BuildLRG.run(0,alloc));
        assertFalse("A live value cannot survive a register kill",IFG.build(0,alloc));
    }

    // A use with no register mask (scheduling dependency only)
    @Test
    public void testNullUseMask() {
        CodeGen code = graph();
        Op def = new Op(A,null,null,false,code._start);
        new Op(null,null,null,false,code._start,def); // Scheduling dependency only.
        RegAlloc alloc = new RegAlloc(code);
        assertTrue(BuildLRG.run(0,alloc));
        assertTrue(IFG.build(0,alloc));
    }

    // Re-cloning a fixed-register value cannot satisfy uses in another class
    @Test
    public void testCloneRegisterClass() {
        for( RegMask required : new RegMask[]{B,new RegMask(6L)} ) {
            CodeGen code = graph();
            Op def = new Op(A,null,null,true,code._start);
            Op use = new Op(null,required,null,false,code._start,def);
            Op use2 = new Op(null,required,null,false,code._start,def);
            RegAlloc alloc = new RegAlloc(code);
            alloc.regAlloc();
            assertEquals(0,alloc.regnum(def));
            assertTrue(required.test(alloc.regnum(use.in(1))));
            assertTrue(required.test(alloc.regnum(use2.in(1))));
            assertTrue(use.in(1) instanceof SplitNode);
        }
    }

    // Commuting an op whose only user is a Phi
    @Test
    public void testCommutativePhi() {
        CodeGen code = graph();
        Op a = new Op(A,null,null,false,code._start);
        Op b = new Op(A,null,null,false,code._start);
        Op add = new Op(A,A,null,false,code._start,a,b) {
            @Override public boolean commutes() { return true; }
        };
        RegionNode region = new RegionNode(null,code._start,code._start);
        new PhiNode("sum",TypeInteger.BOT,region,add,a);
        code._cfg.add(region);
        assertTrue(BuildLRG.run(0,new RegAlloc(code)));
    }

    // Copy bypass must notice clobbers between the two copies
    @Test
    public void testCopyClobber() throws Exception {
        for( boolean killed : new boolean[]{false,true} ) {
            CodeGen code = graph();
            RegAlloc alloc = new RegAlloc(code);
            Op def = new Op(A,null,null,false,code._start);
            alloc.newLRG(def)._reg=0;
            SplitNode hi = testSplit(code,def);
            hi.setDef(0,code._start); hi.setDef(1,def);
            alloc.newLRG(hi)._reg=1;
            new Op(null,null,killed ? A : null,false,code._start);
            SplitNode lo = testSplit(code,hi);
            lo.setDef(0,code._start); lo.setDef(1,hi);
            alloc.newLRG(lo)._reg=0;
            Op use = new Op(null,A,null,false,code._start,lo);
            var post = RegAlloc.class.getDeclaredMethod("postColor");
            post.setAccessible(true); post.invoke(alloc);
            assertSame(killed ? lo : def,use.in(1));
        }
        CodeGen pre = graph();
        RegAlloc uncolored = new RegAlloc(pre);
        Op def0 = new Op(A,null,null,false,pre._start);
        uncolored.newLRG(def0)._mask=A;
        Op fixed = new Op(A,null,null,false,pre._start);
        uncolored.newLRG(fixed)._mask=A;
        SplitNode copy = testSplit(pre,def0);
        copy.setDef(0,pre._start); copy.setDef(1,def0);
        assertFalse("A fixed definition clobbers even before coloring",uncolored.sameBlockNoClobber(copy));
    }
}
