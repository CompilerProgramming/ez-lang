package com.compilerprogramming.ezlang.compiler.node.cpus.arm;

import com.compilerprogramming.ezlang.compiler.codegen.*;
import com.compilerprogramming.ezlang.compiler.node.*;
import com.compilerprogramming.ezlang.compiler.node.MachConcreteNode;
import com.compilerprogramming.ezlang.compiler.util.SB;

public class NegARM extends MachConcreteNode implements MachNode {
    NegARM(Node sub) {
        super(sub);
        //_inputs.pop();
    }
    @Override public String op() { return "neg"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap()   { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        // neg rd, rm is sub rd, xzr, rm
        enc.add4(arm.r_reg(arm.OP_SUB, 0, enc.reg(in(1)), 0, 31, enc.reg(this)));
    }
    // General form: "subi  rd = rs1 - imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = -").p(code.reg(in(1)));
    }
}
