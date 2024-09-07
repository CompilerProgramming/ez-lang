package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Token;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple AST definition.
 */
public abstract class AST {

    protected AST() {
    }
    public abstract void accept(ASTVisitor visitor);

    public abstract StringBuilder toStr(StringBuilder sb);
    @Override
    public String toString() {
        return toStr(new StringBuilder()).toString();
    }

    public static class Program extends AST {
        public final List<Decl> decls = new ArrayList<>();
        public Scope scope;

        @Override
        public StringBuilder toStr(StringBuilder sb) {
            for (Decl decl : decls) {
                decl.toStr(sb);
            }
            return sb;
        }
        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            for (AST.Decl d : decls) {
                d.accept(visitor);
            }
            visitor.visit(this, false);
        }
    }

    public static abstract class Decl extends AST {
    }

    public enum VarType
    {
        STRUCT_FIELD,
        FUNCTION_PARAMETER,
        VARIABLE
    }

    public static class VarDecl extends Decl {
        public final String name;
        public final VarType varType;
        public final TypeExpr typeExpr;
        public Symbol symbol;
        public VarDecl(final String name, VarType varType, final TypeExpr typeExpr) {
            this.name = name;
            this.varType = varType;
            this.typeExpr = typeExpr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("var ").append(name).append(": ");
            return typeExpr.toStr(sb);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            typeExpr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class StructDecl extends Decl {
        public final String name;
        public final VarDecl[] fields;
        public Scope scope;
        public Symbol symbol;
        public StructDecl(final String name, final VarDecl[] fields) {
            this.name = name;
            this.fields = fields;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("struct ").append(name).append("{\n");
            for (VarDecl field : fields) {
                sb.append("  ").append(field).append("\n");
            }
            sb.append("}\n");
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            for (VarDecl field : fields) {
                field.accept(visitor);
            }
            visitor.visit(this, false);
        }
    }

    public static class FuncDecl extends Decl {
        public final String name;
        public final VarDecl[] args;
        public final ReturnTypeExpr returnType;
        public final BlockStmt block;
        public Scope scope;
        public Symbol symbol;

        public FuncDecl(final String name, final VarDecl[] args, final TypeExpr returnType, BlockStmt block) {
            this.name = name;
            this.args = args;
            this.returnType = new ReturnTypeExpr(returnType);
            this.block = block;
        }

        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("func ").append(name).append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                args[i].toStr(sb);
            }
            sb.append(")\n");
            return block.toStr(sb);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            for (VarDecl arg : args) {
                arg.accept(visitor);
            }
            if (returnType != null)
                returnType.accept(visitor);
            block.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public abstract static class Expr extends AST {
        public Type type;
    }

    public abstract static class TypeExpr extends Expr {
        public abstract String name();
    }

    public static class SimpleTypeExpr extends TypeExpr {
        protected final String name;

        public SimpleTypeExpr(String name) {
            this.name = name;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(name);
        }
        @Override
        public String name() { return toStr(new StringBuilder()).toString(); }
        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    public static class NullableSimpleTypeExpr extends SimpleTypeExpr {

        public NullableSimpleTypeExpr(String name) {
            super(name);
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return super.toStr(sb).append("?");
        }
        public String baseTypeName() {
            return super.name;
        }
        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    public static class ArrayTypeExpr extends TypeExpr {
        public final SimpleTypeExpr elementType;

        public ArrayTypeExpr(SimpleTypeExpr elementType) {
            this.elementType = elementType;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("[");
            elementType.toStr(sb);
            return sb.append("]");
        }
        @Override
        public String name() { return toStr(new StringBuilder()).toString(); }
        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            elementType.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class NullableArrayTypeExpr extends ArrayTypeExpr {

        public NullableArrayTypeExpr(SimpleTypeExpr elementType) {
            super(elementType);
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            super.toStr(sb);
            return sb.append("?");
        }
        public String baseTypeName() {
            return super.toStr(new StringBuilder()).toString();
        }
        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            elementType.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class ReturnTypeExpr extends Expr {
        public final TypeExpr returnType;

        public ReturnTypeExpr(TypeExpr returnType) {
            this.returnType = returnType;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            if (returnType != null)
                returnType.accept(visitor);
            visitor.visit(this, false);
        }

        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return returnType != null ? returnType.toStr(sb) : sb;
        }
    }

    public static class NameExpr extends Expr {
        public String name;
        public Symbol symbol;
        public NameExpr(String name) {
            this.name = name;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(name);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    public static class BinaryExpr extends Expr {
        public final Token op;
        public final Expr expr1;
        public final Expr expr2;
        public BinaryExpr(Token op, Expr expr1, Expr expr2) {
            this.op = op;
            this.expr1 = expr1;
            this.expr2 = expr2;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("(");
            expr1.toStr(sb);
            sb.append(op.toString());
            expr2.toStr(sb);
            return sb.append(")");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            expr1.accept(visitor);
            expr2.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class UnaryExpr extends Expr {
        public final Token op;
        public final Expr expr;
        public UnaryExpr(Token op, Expr expr) {
            this.op = op;
            this.expr = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("(").append(op.toString()).append("(");
            expr.toStr(sb);
            return sb.append("))");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            expr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class LiteralExpr extends Expr {
        public final Token value;
        public LiteralExpr(Token value) {
            this.value = value;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append(value.str);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    // Array Index
    public static class ArrayIndexExpr extends Expr {
        public final Expr array;
        public final Expr expr;
        public ArrayIndexExpr(Expr array, Expr expr) {
            this.array = array;
            this.expr = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            array.toStr(sb);
            sb.append("[");
            expr.toStr(sb);
            return sb.append("]");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            array.accept(visitor);
            expr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class FieldExpr extends Expr {
        public final Expr object;
        public final String fieldName;
        public FieldExpr(Expr object, String fieldName) {
            this.object = object;
            this.fieldName = fieldName;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            object.toStr(sb);
            return sb.append(".").append(fieldName);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            object.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class CallExpr extends Expr {
        public final Expr callee;
        public final List<Expr> args;
        public CallExpr(Expr callee, List<Expr> args) {
            this.callee = callee;
            this.args = new ArrayList<>(args);
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            callee.toStr(sb);
            sb.append("(");
            boolean first = true;
            for (AST.Expr expr: args) {
                if (!first)
                    sb.append(",");
                expr.toStr(sb);
                first = false;
            }
            return sb.append(")");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            callee.accept(visitor);
            for (AST.Expr expr: args) {
                expr.accept(visitor);
            }
            visitor.visit(this, false);
        }
    }

    public static class SetFieldExpr extends Expr {
        public final String fieldName;
        public final AST.Expr value;
        public Type objectType;
        public SetFieldExpr(String fieldName, Expr value) {
            this.fieldName = fieldName;
            this.value = value;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            if (fieldName != null) {
                sb.append(fieldName).append("=");
            }
            value.toStr(sb);
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            value.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class NewExpr extends Expr {
        public final TypeExpr typeExpr;
        public final List<Expr> initExprList;
        public NewExpr(TypeExpr typeExpr, List<Expr> initExprList) {
            this.typeExpr = typeExpr;
            this.initExprList = initExprList;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("new ");
            typeExpr.toStr(sb);
            sb.append("{");
            boolean first = true;
            for (Expr expr: initExprList) {
                if (!first)
                    sb.append(", ");
                first = false;
                expr.toStr(sb);
            }
            sb.append("}");
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            typeExpr.accept(visitor);
            for (Expr expr: initExprList) {
                expr.accept(visitor);
            }
            visitor.visit(this, false);
        }
    }

    public abstract static class Stmt extends AST {
    }

    public static class IfElseStmt extends Stmt {
        public final Expr condition;
        public final Stmt ifStmt;
        public final Stmt elseStmt;
        public IfElseStmt(Expr expr, Stmt ifStmt, Stmt elseStmt) {
            this.condition = expr;
            this.ifStmt = ifStmt;
            this.elseStmt = elseStmt;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("if(");
            condition.toStr(sb);
            sb.append(")\n");
            ifStmt.toStr(sb);
            if (elseStmt != null) {
                sb.append("\nelse\n");
                elseStmt.toStr(sb);
            }
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            condition.accept(visitor);
            ifStmt.accept(visitor);
            if (elseStmt != null)
                elseStmt.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class WhileStmt extends Stmt {
        public final Expr condition;
        public Stmt stmt;
        public WhileStmt(Expr expr) {
            this.condition = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("while(");
            condition.toStr(sb);
            sb.append(")\n");
            stmt.toStr(sb);
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            condition.accept(visitor);
            stmt.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class BreakStmt extends Stmt {
        public final WhileStmt whileStmt;
        public BreakStmt(WhileStmt whileStmt) {
            this.whileStmt = whileStmt;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append("break");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    public static class ContinueStmt extends Stmt {
        public final WhileStmt whileStmt;
        public ContinueStmt(WhileStmt whileStmt) {
            this.whileStmt = whileStmt;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return sb.append("continue");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            visitor.visit(this, false);
        }
    }

    public static class ReturnStmt extends Stmt {
        public final AST.Expr expr;
        public ReturnStmt(AST.Expr expr) {
            this.expr = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("return");
            if (expr != null) {
                sb.append(" ");
                expr.toStr(sb);
            }
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            if (expr != null)
                expr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class AssignStmt extends Stmt {
        public final Expr lhs;
        public final Expr rhs;
        public AssignStmt(Expr lhs, Expr rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            lhs.toStr(sb);
            sb.append(" = ");
            rhs.toStr(sb);
            return sb;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            lhs.accept(visitor);
            rhs.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class VarStmt extends Stmt {
        public final String varName;
        public Symbol.VarSymbol symbol;
        public final AST.Expr expr;

        public VarStmt(String symbol, Expr expr) {
            this.varName = symbol;
            this.expr = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("var ").append(varName).append(" = ");
            return expr.toStr(sb);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            expr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(Expr expr) {
            this.expr = expr;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return expr.toStr(sb);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            expr.accept(visitor);
            visitor.visit(this, false);
        }
    }

    public static class VarDeclStmt extends Stmt {
        public final VarDecl varDecl;
        public VarDeclStmt(VarDecl varDec) {
            this.varDecl = varDec;
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            return varDecl.toStr(sb);
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            varDecl.accept(visitor);
            visitor.visit(this, false);
        }
    }


    public static class BlockStmt extends Stmt {
        public final List<Stmt> stmtList = new ArrayList<>();
        public Scope scope;
        public BlockStmt() {
        }
        @Override
        public StringBuilder toStr(StringBuilder sb) {
            sb.append("{\n");
            for (Stmt stmt: stmtList) {
                stmt.toStr(sb);
                sb.append("\n");
            }
            return sb.append("}\n");
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor = visitor.visit(this, true);
            if (visitor == null)
                return;
            for (Stmt stmt: stmtList) {
                stmt.accept(visitor);
            }
            visitor.visit(this, false);
        }
    }
}
