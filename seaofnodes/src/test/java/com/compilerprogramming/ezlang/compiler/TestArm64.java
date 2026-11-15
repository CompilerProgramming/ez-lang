package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.compiler.codegen.CodeGen;
import com.compilerprogramming.ezlang.compiler.node.FunNode;
import com.compilerprogramming.ezlang.compiler.node.Node;
import com.compilerprogramming.ezlang.compiler.node.cpus.arm.arm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

// Runs ARM64 code in an emulator
public class TestArm64 {

    // Compile and run a simple program
    public static EvalArm64 build( String dir, String file, String main, int arg, int spills, boolean print ) throws IOException {
        String src = Files.readString(Path.of(dir+"/"+file+".ez"));
        return buildSrc(src, main, arg, spills, print);
    }

    public static EvalArm64 buildSrc( String src, String main, int arg, int spills, boolean print ) throws IOException {
        // Compile and link in memory
        CodeGen code = new CodeGen(src).driver("arm", "SystemV",null);
        if( print ) { code.print_as_hex(); System.out.print(code.asm()); }

        // Allocation quality not degraded when a baseline was supplied
        if( spills >= 0 ) {
            int delta = spills>>3;
            if( delta==0 ) delta = 1;
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        }

        // Image
        byte[] image = new byte[1<<20]; // A megabyte (1024*1024 bytes)
        EvalArm64 ARM = new EvalArm64(image, 1<<16);
        // Code at offset 0
        System.arraycopy(code._encoding.bits(), 0, image, 0, code._encoding.bits().length);
        // Initial incoming int arg
        ARM.regs[arm.X0] = arg;
        // malloc memory starts at 64K and goes up

        // Look up starting PC or zero if not given
        if( main!=null )
            for( Node use : code._start._outputs )
                if( use instanceof FunNode fun && main.equals(fun._name) )
                    ARM._pc = code._encoding._opStart[fun._nid];

        return ARM;
    }

}
