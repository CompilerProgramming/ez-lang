package com.compilerprogramming.ezlang.compiler.node.cpus.riscv;

import com.compilerprogramming.ezlang.compiler.util.SB;
import com.compilerprogramming.ezlang.compiler.codegen.*;
import com.compilerprogramming.ezlang.compiler.node.MachConcreteNode;
import com.compilerprogramming.ezlang.compiler.node.MachNode;
import com.compilerprogramming.ezlang.compiler.node.NotNode;

public class NotRISC extends MachConcreteNode implements MachNode {
    public NotRISC(NotNode not) { super(not); }
    @Override public String op() { return "not"; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap()   { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        // SEQZ is unsigned (sltiu): negative nonzero inputs must also produce false.
        enc.add4(riscv.i_type(riscv.OP_IMM, dst, 3, src, 1));
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
