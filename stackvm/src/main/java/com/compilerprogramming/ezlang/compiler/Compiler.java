package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
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
                functionType.code = new CompiledFunction(functionSymbol);
            }
        }
    }
    public TypeDictionary compileSrc(String src) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        NullableAnalysis.analyze(typeDict);
        compile(typeDict);
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
