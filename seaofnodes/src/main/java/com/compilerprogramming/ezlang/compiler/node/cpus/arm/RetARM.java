package com.compilerprogramming.ezlang.compiler.node.cpus.arm;

import com.compilerprogramming.ezlang.compiler.codegen.*;
import com.compilerprogramming.ezlang.compiler.node.*;

public class RetARM extends ReturnNode implements MachNode {
    RetARM(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public void encoding( Encoding enc ) {
        int frameAdjust = ((FunARM)fun())._frameAdjust;
        if( frameAdjust > 0 )
            enc.add4(arm.imm_inst(arm.OPI_ADD, frameAdjust, arm.RSP, arm.RSP));
        enc.add4(arm.ret(arm.OP_RET));
    }
}
