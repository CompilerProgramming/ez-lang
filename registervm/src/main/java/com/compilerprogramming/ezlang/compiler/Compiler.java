package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.parser.ShortCircuitLowerer;
import com.compilerprogramming.ezlang.semantic.NullableAnalysis;
import com.compilerprogramming.ezlang.semantic.SemaAssignTypes;
import com.compilerprogramming.ezlang.semantic.SemaDefineTypes;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.BitSet;

public class Compiler {

    private void compile(TypeDictionary typeDictionary) {
        for (Symbol symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionSymbol) {
                EZType.EZTypeFunction functionType = (EZType.EZTypeFunction) functionSymbol.type;
                functionType.code = new CompiledFunction(functionSymbol, typeDictionary);
            }
        }
    }
    public TypeDictionary compileSrc(String src) {
        return compileSrc(src, false);
    }
    public TypeDictionary compileSrc(String src, boolean lowerShortCircuit) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        var typeDict = analyze(program);
        // Some backend such as the SON do not
        // support compiling boolean short-circuit operators
        // so we lower them to if blocks. But this is also
        // done during tests to prove that the lowering works.
        if (lowerShortCircuit) {
            ShortCircuitLowerer.lower(program);
            typeDict = bind(program);
        }
        compile(typeDict);
        return typeDict;
    }

    private TypeDictionary analyze(com.compilerprogramming.ezlang.parser.AST.Program program) {
        var typeDict = bind(program);
        NullableAnalysis.analyze(typeDict);
        return typeDict;
    }

    private TypeDictionary bind(com.compilerprogramming.ezlang.parser.AST.Program program) {
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        return typeDict;
    }
    public String dumpIR(TypeDictionary typeDictionary) {
        StringBuilder sb = new StringBuilder();
        for (Symbol s: typeDictionary.bindings.values()) {
            if (s instanceof Symbol.FunctionTypeSymbol f) {
                var functionBuilder = (CompiledFunction) f.code();
                BasicBlock.toStr(sb, functionBuilder.entry, new BitSet());
            }
        }
        return sb.toString();
    }
}
