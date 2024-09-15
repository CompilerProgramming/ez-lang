package com.compilerprogramming.ezlang.bytecode;

import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;

public class TypeStateVMCompiler {

    public void compile(TypeDictionary typeDictionary) {
        for (Symbol symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionSymbol) {
                functionSymbol.code = new FunctionBuilder(functionSymbol);
            }
        }
    }
}
