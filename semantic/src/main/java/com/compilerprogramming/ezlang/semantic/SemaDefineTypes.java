package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.ASTVisitor;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

/**
 * The goal of this semantic analysis pass is to define
 * functions and struct types.
 */
public class SemaDefineTypes implements ASTVisitor {
    Scope currentScope;
    AST.StructDecl currentStructDecl;
    AST.FuncDecl currentFuncDecl;
    final TypeDictionary typeDictionary;

    public SemaDefineTypes(TypeDictionary typeDictionary) {
        this.typeDictionary = typeDictionary;
    }

    @Override
    public ASTVisitor enter(AST.Program program) {
        currentScope = typeDictionary;
        program.scope = currentScope;
        return this;
    }
    @Override
    public void exit(AST.Program program) {
        currentScope = currentScope.parent;
    }

    @Override
    public ASTVisitor enter(AST.FuncDecl funcDecl) {
        if (typeDictionary.lookup(funcDecl.name) != null) {
            throw new CompilerException("Symbol " + funcDecl.name + " is already declared", funcDecl.lineNumber);
        }
        // Create function scope, that houses function parameters
        currentScope = new Scope(currentScope, true);
        funcDecl.scope = currentScope;
        // Install a symbol for the function,
        // type is not fully formed at this stage
        // as parameters nad return values must be added
        Symbol funcSymbol = new Symbol.FunctionTypeSymbol(funcDecl.name, new EZType.EZTypeFunction(funcDecl.name), funcDecl);
        typeDictionary.install(funcDecl.name, funcSymbol);
        // Set up the function decl so that when we visit the parameters
        // and return type we know where to add
        funcDecl.symbol = funcSymbol;
        currentFuncDecl = funcDecl;
        return this;
    }
    @Override
    public void exit(AST.FuncDecl funcDecl) {
        currentScope = currentScope.parent;
        currentFuncDecl = null;
    }

    @Override
    public ASTVisitor enter(AST.StructDecl structDecl) {
        Symbol structSymbol = typeDictionary.lookup(structDecl.name);
        if (structSymbol != null) {
            if (structSymbol.type instanceof EZType.EZTypeStruct lookupStructType) {
                if (!lookupStructType.pending)
                    throw new CompilerException("Struct type " + structDecl.name + " is already declared", structDecl.lineNumber);
            }
            else
                throw new CompilerException("Symbol " + structDecl.name + " is already declared", structDecl.lineNumber);
        }
        else {
            EZType.EZTypeStruct structType = new EZType.EZTypeStruct(structDecl.name);
            structSymbol = new Symbol.TypeSymbol(structDecl.name, structType);
            typeDictionary.install(structDecl.name, structSymbol);
        }
        // Struct gets its own scope where the fields live
        currentScope = new Scope(currentScope);
        structDecl.scope = currentScope;
        structDecl.symbol = structSymbol;
        currentStructDecl = structDecl;
        return this;
    }
    @Override
    public void exit(AST.StructDecl structDecl) {
        currentScope = currentScope.parent;
        currentStructDecl = null;
    }

    @Override
    public ASTVisitor enter(AST.VarDecl varDecl) {
        if (varDecl.varType == AST.VarType.STRUCT_FIELD && currentStructDecl != null) {
            if (currentScope.lookup(varDecl.name) != null) {
                throw new CompilerException("Field " + varDecl.name + " is already declared", varDecl.lineNumber);
            }
        }
        else if (varDecl.varType == AST.VarType.FUNCTION_PARAMETER && currentFuncDecl != null) {
            if (currentScope.lookup(varDecl.name) != null) {
                throw new CompilerException("Function parameter " + varDecl.name + " is already declared", varDecl.lineNumber);
            }
        }
        return this;
    }
    @Override
    public void exit(AST.VarDecl varDecl) {
        if (varDecl.varType == AST.VarType.STRUCT_FIELD
                && currentStructDecl != null) {
            EZType.EZTypeStruct type = (EZType.EZTypeStruct) currentStructDecl.symbol.type;
            type.addField(varDecl.name, varDecl.typeExpr.type);
        }
        else if (varDecl.varType == AST.VarType.FUNCTION_PARAMETER
                && currentFuncDecl != null
                && currentScope == currentFuncDecl.scope) {
            if (currentScope.localLookup(varDecl.name) != null)
                throw new CompilerException("Parameter " + varDecl.name + " is already declared", varDecl.lineNumber);
            EZType.EZTypeFunction type = (EZType.EZTypeFunction) currentFuncDecl.symbol.type;
            varDecl.symbol = currentScope.install(varDecl.name, new Symbol.ParameterSymbol(varDecl.name, varDecl.typeExpr.type));
            type.addArg(varDecl.symbol);
        }
        else if (varDecl.varType == AST.VarType.VARIABLE) {
            if (currentScope.localLookup(varDecl.name) != null)
                throw new CompilerException("Variable " + varDecl.name + " is already declared", varDecl.lineNumber);
            varDecl.symbol = currentScope.install(varDecl.name, new Symbol.VarSymbol(varDecl.name, varDecl.typeExpr.type));
        }
    }

    EZType getNullableSimpleType(AST.NullableSimpleTypeExpr simpleTypeExpr) {
        String baseTypeName = simpleTypeExpr.baseTypeName();
        Symbol typeSymbol = typeDictionary.lookup(baseTypeName);
        EZType baseType;
        if (typeSymbol == null)
            baseType = typeDictionary.intern(new EZType.EZTypeStruct(baseTypeName));
        else
            baseType = typeSymbol.type;
        if (baseType.isPrimitive())
            throw new CompilerException("Cannot make Nullable instance of primitive type", simpleTypeExpr.lineNumber);
        return typeDictionary.intern(new EZType.EZTypeNullable(baseType));
    }

    EZType getSimpleType(AST.SimpleTypeExpr simpleTypeExpr) {
        if (simpleTypeExpr instanceof AST.NullableSimpleTypeExpr nullableSimpleTypeExpr)
            return getNullableSimpleType(nullableSimpleTypeExpr);
        String typeName = simpleTypeExpr.name();
        Symbol typeSymbol = typeDictionary.lookup(typeName);
        if (typeSymbol == null) {
            return  typeDictionary.intern(new EZType.EZTypeStruct(typeName));
        }
        return typeSymbol.type;
    }

    @Override
    public ASTVisitor enter(AST.SimpleTypeExpr simpleTypeExpr) {
        simpleTypeExpr.type = getSimpleType(simpleTypeExpr);
        return this;
    }

    @Override
    public ASTVisitor enter(AST.NullableSimpleTypeExpr simpleTypeExpr) {
        simpleTypeExpr.type = getNullableSimpleType(simpleTypeExpr);
        return this;
    }

    EZType getArrayType(AST.ArrayTypeExpr arrayTypeExpr) {
        if (arrayTypeExpr instanceof AST.NullableArrayTypeExpr nullableArrayTypeExpr)
            return getNullableArrayType(nullableArrayTypeExpr);
        var elemTypeExpr = arrayTypeExpr.elementType;
        var elemType = getSimpleType(elemTypeExpr);
        return typeDictionary.makeArrayType(elemType, false);
    }

    EZType getNullableArrayType(AST.NullableArrayTypeExpr arrayTypeExpr) {
        var elemTypeExpr = arrayTypeExpr.elementType;
        var elemType = getSimpleType(elemTypeExpr);
        return typeDictionary.makeArrayType(elemType, true);
    }

    @Override
    public ASTVisitor enter(AST.ArrayTypeExpr arrayTypeExpr) {
        arrayTypeExpr.type = getArrayType(arrayTypeExpr);
        return this;
    }

    public ASTVisitor enter(AST.NullableArrayTypeExpr arrayTypeExpr) {
        arrayTypeExpr.type = getNullableArrayType(arrayTypeExpr);
        return this;
    }

    @Override
    public ASTVisitor enter(AST.ReturnTypeExpr returnTypeExpr) {
        // We override the visitor and visit the return type here because
        // we need to associate the return type to the function's return type
        // The visitor mechanism doesn't allow us to associate values between two steps
        EZType.EZTypeFunction type = (EZType.EZTypeFunction) currentFuncDecl.symbol.type;
        if (returnTypeExpr.returnType != null) {
            returnTypeExpr.returnType.accept(this);
            returnTypeExpr.type = returnTypeExpr.returnType.type;
            type.setReturnType(returnTypeExpr.type);
        }
        else {
            type.setReturnType(typeDictionary.VOID);
        }
        return null;
    }

    @Override
    public ASTVisitor enter(AST.VarStmt varStmt) {
        if (currentScope.localLookup(varStmt.varName) != null)
            throw new CompilerException("Variable " + varStmt.varName + " already declared in current scope", varStmt.lineNumber);
        varStmt.symbol = (Symbol.VarSymbol) currentScope.install(varStmt.varName, new Symbol.VarSymbol(varStmt.varName, typeDictionary.UNKNOWN));
        return this;
    }

    @Override
    public ASTVisitor enter(AST.BlockStmt blockStmt) {
        Scope blockScope = new Scope(currentScope);
        blockStmt.scope = blockScope;
        currentScope = blockScope;
        return this;
    }
    @Override
    public void exit(AST.BlockStmt blockStmt) {
        currentScope = currentScope.parent;
    }

    public void analyze(AST.Program program) {
        program.accept(this);
    }
}
