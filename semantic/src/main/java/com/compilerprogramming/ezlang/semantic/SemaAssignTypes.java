package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Token;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.ASTVisitor;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Type;
import com.compilerprogramming.ezlang.types.TypeDictionary;

/**
 * The goal of this semantic analysis pass is to define
 * functions and struct types.
 */
public class SemaAssignTypes implements ASTVisitor {
    Scope currentScope;
    AST.StructDecl currentStructDecl;
    AST.FuncDecl currentFuncDecl;
    final TypeDictionary typeDictionary;

    public SemaAssignTypes(TypeDictionary typeDictionary) {
        this.typeDictionary = typeDictionary;
    }

    @Override
    public ASTVisitor visit(AST.Program program, boolean enter) {
        if (enter) {
            currentScope = program.scope;
        }
        else {
            currentScope = currentScope.parent;
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.FuncDecl funcDecl, boolean enter) {
        if (enter) {
            currentScope = funcDecl.scope;
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
            currentScope = structDecl.scope;
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
        return this;
    }

    @Override
    public ASTVisitor visit(AST.BinaryExpr binaryExpr, boolean enter) {
        if (!enter) {
            validType(binaryExpr.expr1.type);
            validType(binaryExpr.expr2.type);
            if (binaryExpr.expr1.type instanceof Type.TypeInteger t1 &&
                binaryExpr.expr2.type instanceof Type.TypeInteger t2) {
                binaryExpr.type = typeDictionary.merge(t1, t2);
            }
            else {
                throw new CompilerException("Binary operator " + binaryExpr.op + " not supported for operands");
            }
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.UnaryExpr unaryExpr, boolean enter) {
        if (enter) {
            return this;
        }
        validType(unaryExpr.expr.type);
        if (unaryExpr.expr.type instanceof Type.TypeInteger ti) {
            unaryExpr.type = unaryExpr.expr.type;
        }
        else {
            throw new CompilerException("Unary operator " + unaryExpr.op + " not supported for operand");
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.FieldExpr fieldExpr, boolean enter) {
        if (enter)
            return this;
        validType(fieldExpr.object.type);
        Type.TypeStruct structType = null;
        if (fieldExpr.object.type instanceof Type.TypeStruct ts) {
            structType = ts;
        }
        else if (fieldExpr.object.type instanceof Type.TypeNullable ptr &&
                ptr.baseType instanceof Type.TypeStruct ts) {
            structType = ts;
        }
        else
            throw new CompilerException("Unexpected struct type " + fieldExpr.object.type);
        var fieldType = structType.getField(fieldExpr.fieldName);
        if (fieldType == null)
            throw new CompilerException("Struct " + structType + " does not have field named " + fieldExpr.fieldName);
        fieldExpr.type = fieldType;
        return this;
    }

    @Override
    public ASTVisitor visit(AST.CallExpr callExpr, boolean enter) {
        if (!enter) {
            validType(callExpr.callee.type);
            if (callExpr.callee.type instanceof Type.TypeFunction f) {
                callExpr.type = f.returnType;
            }
            else
                throw new CompilerException("Call target must be a function");
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.SetFieldExpr setFieldExpr, boolean enter) {
        if (!enter) {
            validType(setFieldExpr.value.type);
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.SimpleTypeExpr simpleTypeExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NullableSimpleTypeExpr simpleTypeExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ArrayTypeExpr arrayTypeExpr, boolean enter) {
        return this;
    }

    public ASTVisitor visit(AST.NullableArrayTypeExpr arrayTypeExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ReturnTypeExpr returnTypeExpr, boolean enter) {
        return this;
    }

    @Override
    public ASTVisitor visit(AST.LiteralExpr literalExpr, boolean enter) {
        if (enter) {
            if (literalExpr.value.kind == Token.Kind.NUM) {
                literalExpr.type = typeDictionary.INT;
            }
            else {
                throw new CompilerException("Unsupported literal " + literalExpr.value);
            }
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.ArrayIndexExpr arrayIndexExpr, boolean enter) {
        if (!enter) {
            validType(arrayIndexExpr.array.type);
            Type.TypeArray arrayType = null;
            if (arrayIndexExpr.array.type instanceof Type.TypeArray ta) {
                arrayType = ta;
            }
            else if (arrayIndexExpr.array.type instanceof Type.TypeNullable ptr &&
                    ptr.baseType instanceof Type.TypeArray ta) {
                arrayType = ta;
            }
            else
                throw new CompilerException("Unexpected array type " + arrayIndexExpr.array.type);
            arrayIndexExpr.type = arrayType.getElementType();
            validType(arrayIndexExpr.type);
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NewExpr newExpr, boolean enter) {
        if (enter)
            return this;
        if (newExpr.typeExpr.type == null)
            throw new CompilerException("Unresolved type in new expression");
        if (newExpr.typeExpr.type instanceof Type.TypeStruct ||
            newExpr.typeExpr.type instanceof Type.TypeArray) {
            newExpr.type = newExpr.typeExpr.type;
            for (AST.Expr expr: newExpr.initExprList) {
                if (expr instanceof AST.SetFieldExpr setFieldExpr) {
                    setFieldExpr.objectType = newExpr.typeExpr.type;
                }
            }
        }
        else
            throw new CompilerException("Unsupported type in new expression");
        return this;
    }

    @Override
    public ASTVisitor visit(AST.NameExpr nameExpr, boolean enter) {
        if (!enter)
            return this;
        var symbol = currentScope.lookup(nameExpr.name);
        if (symbol == null) {
            throw new CompilerException("Unknown symbol " + nameExpr.name);
        }
        validType(symbol.type);
        nameExpr.symbol = symbol;
        nameExpr.type = symbol.type;
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
        if (enter)
            return this;
        if (returnStmt.expr != null)
            validType(returnStmt.expr.type);
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
        if (!enter) {
            validType(varStmt.expr.type);
            var symbol = currentScope.lookup(varStmt.varName);
            symbol.type = typeDictionary.merge(varStmt.expr.type, symbol.type);
        }
        return this;
    }

    @Override
    public ASTVisitor visit(AST.BlockStmt blockStmt, boolean enter) {
        if (enter) {
            currentScope = blockStmt.scope;
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
        if (!enter) {
            validType(assignStmt.lhs.type);
            validType(assignStmt.rhs.type);
            // TODO check assignment
        }
        return this;
    }

    public void analyze(AST.Program program) {
        program.accept(this);
    }

    private void validType(Type t) {
        if (t == null)
            throw new CompilerException("Undefined type");
        if (t == typeDictionary.ANY)
            throw new CompilerException("Undefined type");
    }
}
