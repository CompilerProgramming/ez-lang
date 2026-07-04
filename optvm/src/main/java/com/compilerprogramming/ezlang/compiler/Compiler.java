package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.semantic.NullableAnalysis;
import com.compilerprogramming.ezlang.semantic.SemaAssignTypes;
import com.compilerprogramming.ezlang.semantic.SemaDefineTypes;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.EnumSet;

public class Compiler {

    private void compile(TypeDictionary typeDictionary, EnumSet<Options> options) {
        for (Symbol symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionSymbol) {
                EZType.EZTypeFunction functionType = (EZType.EZTypeFunction) functionSymbol.type;
                var function = new CompiledFunction(functionSymbol, typeDictionary, options);
                if (options.contains(Options.DUMP_INITIAL_IR))
                    function.dumpIR(false, "Initial IR");
                functionType.code = function;
                new Optimizer().optimize(function, options);
            }
        }
    }
    public TypeDictionary compileSrc(String src) {
        return compileSrc(src, EnumSet.noneOf(Options.class));
    }
    public TypeDictionary compileSrc(String src, EnumSet<Options> options) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        NullableAnalysis.analyze(typeDict);
        compile(typeDict, options);
        return typeDict;
    }
    public static String dumpIR(TypeDictionary typeDictionary) {
        return dumpIR(typeDictionary, false);
    }
    public static String dumpIR(TypeDictionary typeDictionary, boolean verbose) {
        StringBuilder sb = new StringBuilder();
        for (Symbol s: typeDictionary.bindings.values()) {
            if (s instanceof Symbol.FunctionTypeSymbol f) {
                var function = (CompiledFunction) f.code();
                function.toStr(sb, verbose);
            }
        }
        return sb.toString();
    }
}
