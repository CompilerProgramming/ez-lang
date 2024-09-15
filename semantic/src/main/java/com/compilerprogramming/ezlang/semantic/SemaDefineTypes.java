package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.ASTVisitor;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.Type;
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
    public ASTVisitor visit(AST.Program program, boolean enter) {
        if (enter) {
            currentScope = typeDictionary;
            program.scope = currentScope;
        }
        else {
            currentScope = currentScope.parent;
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.FuncDecl funcDecl, boolean enter) {
        if (enter) {
            if (typeDictionary.lookup(funcDecl.name) != null) {
                throw new CompilerException("Symbol " + funcDecl.name + " is already declared");
            }
            // Create function scope, that houses function parameters
            currentScope = new Scope(currentScope);
            funcDecl.scope = currentScope;
            // Install a symbol for the function,
            // type is not fully formed at this stage
            // as parameters nad return values must be added
            Symbol funcSymbol = new Symbol.FunctionTypeSymbol(funcDecl.name, new Type.TypeFunction(funcDecl.name), funcDecl);
            typeDictionary.install(funcDecl.name, funcSymbol);
            // Set up the function decl so that when we visit the parameters
            // and return type we know where to add
            funcDecl.symbol = funcSymbol;
            currentFuncDecl = funcDecl;
        }
        else {
            currentScope = currentScope.parent;
            currentFuncDecl = null;
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.StructDecl structDecl, boolean enter) {
        if (enter) {
            Symbol structSymbol = typeDictionary.lookup(structDecl.name);
            if (structSymbol != null) {
                if (structSymbol.type instanceof Type.TypeStruct lookupStructType) {
                    if (!lookupStructType.pending)
                        throw new CompilerException("Struct type " + structDecl.name + " is already declared");
                }
                else
                    throw new CompilerException("Symbol " + structDecl.name + " is already declared");
            }
            else {
                Type.TypeStruct structType = new Type.TypeStruct(structDecl.name);
                structSymbol = new Symbol.TypeSymbol(structDecl.name, structType);
                typeDictionary.install(structDecl.name, structSymbol);
            }
            // Struct gets its own scope where the fields live
            currentScope = new Scope(currentScope);
            structDecl.scope = currentScope;
            structDecl.symbol = structSymbol;
            currentStructDecl = structDecl;
        }
        else {
            currentScope = currentScope.parent;
            currentStructDecl = null;
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.VarDecl varDecl, boolean enter) {
        if (enter) {
            if (varDecl.varType == AST.VarType.STRUCT_FIELD && currentStructDecl != null) {
                if (currentScope.lookup(varDecl.name) != null) {
                    throw new CompilerException("Field " + varDecl.name + " is already declared");
                }
            }
            else if (varDecl.varType == AST.VarType.FUNCTION_PARAMETER && currentFuncDecl != null) {
                if (currentScope.lookup(varDecl.name) != null) {
                    throw new CompilerException("Function parameter " + varDecl.name + " is already declared");
                }
            }
        }
        else {
            if (varDecl.varType == AST.VarType.STRUCT_FIELD
                    && currentStructDecl != null) {
                Type.TypeStruct type = (Type.TypeStruct) currentStructDecl.symbol.type;
                type.addField(varDecl.name, varDecl.typeExpr.type);
            }
            else if (varDecl.varType == AST.VarType.FUNCTION_PARAMETER
                    && currentFuncDecl != null
                    && currentScope == currentFuncDecl.scope) {
                if (currentScope.localLookup(varDecl.name) != null)
                    throw new CompilerException("Parameter " + varDecl.name + " is already declared");
                Type.TypeFunction type = (Type.TypeFunction) currentFuncDecl.symbol.type;
                varDecl.symbol = currentScope.install(varDecl.name, new Symbol.VarSymbol(varDecl.name, varDecl.typeExpr.type));
                type.addArg(varDecl.symbol);
            }
            else if (varDecl.varType == AST.VarType.VARIABLE) {
                if (currentScope.localLookup(varDecl.name) != null)
                    throw new CompilerException("Variable " + varDecl.name + " is already declared");
                varDecl.symbol = currentScope.install(varDecl.name, new Symbol.VarSymbol(varDecl.name, varDecl.typeExpr.type));
            }
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.BinaryExpr binaryExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.UnaryExpr unaryExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.FieldExpr fieldExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.CallExpr callExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.SetFieldExpr setFieldExpr, boolean enter) {
        return this;
    }

    Type getNullableSimpleType(AST.NullableSimpleTypeExpr simpleTypeExpr) {
        String baseTypeName = simpleTypeExpr.baseTypeName();
        Symbol typeSymbol = typeDictionary.lookup(baseTypeName);
        Type baseType;
        if (typeSymbol == null)
            baseType = typeDictionary.intern(new Type.TypeStruct(baseTypeName));
        else
            baseType = typeSymbol.type;
        if (baseType.isPrimitive())
            throw new CompilerException("Cannot make nullable instance of primitive type");
        return typeDictionary.intern(new Type.TypeNullable(baseType));
    }

    Type getSimpleType(AST.SimpleTypeExpr simpleTypeExpr) {
        if (simpleTypeExpr instanceof AST.NullableSimpleTypeExpr nullableSimpleTypeExpr)
            return getNullableSimpleType(nullableSimpleTypeExpr);
        String typeName = simpleTypeExpr.name();
        Symbol typeSymbol = typeDictionary.lookup(typeName);
        if (typeSymbol == null) {
            return  typeDictionary.intern(new Type.TypeStruct(typeName));
        }
        return typeSymbol.type;
    }

    @Override
    public ASTVisitor visit(AST.SimpleTypeExpr simpleTypeExpr, boolean enter) {
        if (enter && simpleTypeExpr.type == null) {
            simpleTypeExpr.type = getSimpleType(simpleTypeExpr);
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NullableSimpleTypeExpr simpleTypeExpr, boolean enter) {
        if (enter && simpleTypeExpr.type == null) {
            simpleTypeExpr.type = getNullableSimpleType(simpleTypeExpr);
        }
        return this;
    }

    Type getArrayType(AST.ArrayTypeExpr arrayTypeExpr) {
        if (arrayTypeExpr instanceof AST.NullableArrayTypeExpr nullableArrayTypeExpr)
            return getNullableArrayType(nullableArrayTypeExpr);
        var elemTypeExpr = arrayTypeExpr.elementType;
        var elemType = getSimpleType(elemTypeExpr);
        return typeDictionary.makeArrayType(elemType, false);
    }

    Type getNullableArrayType(AST.NullableArrayTypeExpr arrayTypeExpr) {
        var elemTypeExpr = arrayTypeExpr.elementType;
        var elemType = getSimpleType(elemTypeExpr);
        return typeDictionary.makeArrayType(elemType, true);
    }

    @Override
    public ASTVisitor visit(AST.ArrayTypeExpr arrayTypeExpr, boolean enter) {
        if (enter && arrayTypeExpr.type == null) {
            arrayTypeExpr.type = getArrayType(arrayTypeExpr);
        }
        return this;
    }

    public ASTVisitor visit(AST.NullableArrayTypeExpr arrayTypeExpr, boolean enter) {
        if (enter && arrayTypeExpr.type == null) {
            arrayTypeExpr.type = getNullableArrayType(arrayTypeExpr);
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ReturnTypeExpr returnTypeExpr, boolean enter) {
        ASTVisitor visitor = this;
        if (enter) {
            // We override the visitor and visit the return type here because
            // we need to associate the return type to the function's return type
            // The visitor mechanism doesn't allow us to associate values between two steps
            Type.TypeFunction type = (Type.TypeFunction) currentFuncDecl.symbol.type;
            if (returnTypeExpr.returnType != null) {
                returnTypeExpr.returnType.accept(this);
                returnTypeExpr.type = returnTypeExpr.returnType.type;
                type.setReturnType(returnTypeExpr.type);
            }
            else {
                type.setReturnType(typeDictionary.VOID);
            }
            visitor = null;
        }
        return visitor;
    }

    @Override
    public ASTVisitor visit(AST.LiteralExpr literalExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ArrayIndexExpr arrayIndexExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NewExpr newExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NameExpr nameExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.BreakStmt breakStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ContinueStmt continueStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ReturnStmt returnStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.IfElseStmt ifElseStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.WhileStmt whileStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.VarStmt varStmt, boolean enter) {
        if (enter) {
            if (currentScope.localLookup(varStmt.varName) != null)
                throw new CompilerException("Variable " + varStmt.varName + " already declared in current scope");
            varStmt.symbol = (Symbol.VarSymbol) currentScope.install(varStmt.varName, new Symbol.VarSymbol(varStmt.varName, typeDictionary.ANY));
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.BlockStmt blockStmt, boolean enter) {
        if (enter) {
            Scope blockScope = new Scope(currentScope);
            blockStmt.scope = blockScope;
            currentScope = blockScope;
        }
        else {
            currentScope = currentScope.parent;
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.VarDeclStmt varDeclStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ExprStmt exprStmt, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.AssignStmt assignStmt, boolean enter) {
        return this;
    }

    public void analyze(AST.Program program) {
        program.accept(this);
    }
}
