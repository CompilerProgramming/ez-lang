package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lowers short-circuit boolean expressions into ordinary control flow before
 * semantic analysis gives names and temporaries symbols.
 *
 * Some backend such as the SON do not
 * support compiling boolean short-circuit operators
 * so we lower them to if blocks. But this is also
 * done during tests to prove that the lowering works.
 */
public final class ShortCircuitLowerer {
    private final Set<String> usedNames = new HashSet<>();
    private int nextTemp;

    private ShortCircuitLowerer(AST.Program program) {
        collectNames(program);
    }

    public static void lower(AST.Program program) {
        new ShortCircuitLowerer(program).lowerProgram(program);
    }

    private void lowerProgram(AST.Program program) {
        for (AST.Decl decl : program.decls) {
            if (decl instanceof AST.FuncDecl funcDecl) {
                lowerBlockInPlace(funcDecl.block);
            }
        }
    }

    private void collectNames(AST ast) {
        switch (ast) {
            case AST.Program program -> {
                for (AST.Decl decl : program.decls) collectNames(decl);
            }
            case AST.FuncDecl funcDecl -> {
                usedNames.add(funcDecl.name);
                for (AST.VarDecl arg : funcDecl.args) usedNames.add(arg.name);
                collectNames(funcDecl.block);
            }
            case AST.StructDecl structDecl -> {
                usedNames.add(structDecl.name);
                for (AST.VarDecl field : structDecl.fields) usedNames.add(field.name);
            }
            case AST.BlockStmt blockStmt -> {
                for (AST.Stmt stmt : blockStmt.stmtList) collectNames(stmt);
            }
            case AST.VarStmt varStmt -> {
                usedNames.add(varStmt.varName);
                collectNames(varStmt.expr);
            }
            case AST.VarDeclStmt varDeclStmt -> usedNames.add(varDeclStmt.varDecl.name);
            case AST.AssignStmt assignStmt -> {
                usedNames.add(assignStmt.nameExpr.name);
                collectNames(assignStmt.rhs);
            }
            case AST.ExprStmt exprStmt -> collectNames(exprStmt.expr);
            case AST.ReturnStmt returnStmt -> {
                if (returnStmt.expr != null) collectNames(returnStmt.expr);
            }
            case AST.IfElseStmt ifElseStmt -> {
                collectNames(ifElseStmt.condition);
                collectNames(ifElseStmt.ifStmt);
                if (ifElseStmt.elseStmt != null) collectNames(ifElseStmt.elseStmt);
            }
            case AST.WhileStmt whileStmt -> {
                collectNames(whileStmt.condition);
                collectNames(whileStmt.stmt);
            }
            case AST.BinaryExpr binaryExpr -> {
                collectNames(binaryExpr.expr1);
                collectNames(binaryExpr.expr2);
            }
            case AST.UnaryExpr unaryExpr -> collectNames(unaryExpr.expr);
            case AST.ArrayLoadExpr arrayLoadExpr -> {
                collectNames(arrayLoadExpr.array);
                collectNames(arrayLoadExpr.expr);
            }
            case AST.ArrayStoreExpr arrayStoreExpr -> {
                collectNames(arrayStoreExpr.array);
                collectNames(arrayStoreExpr.expr);
                collectNames(arrayStoreExpr.value);
            }
            case AST.GetFieldExpr getFieldExpr -> collectNames(getFieldExpr.object);
            case AST.SetFieldExpr setFieldExpr -> {
                collectNames(setFieldExpr.object);
                collectNames(setFieldExpr.value);
            }
            case AST.CallExpr callExpr -> {
                collectNames(callExpr.callee);
                for (AST.Expr arg : callExpr.args) collectNames(arg);
            }
            case AST.NewExpr newExpr -> {
                if (newExpr.len != null) collectNames(newExpr.len);
                if (newExpr.initValue != null) collectNames(newExpr.initValue);
            }
            case AST.InitExpr initExpr -> {
                collectNames(initExpr.newExpr);
                for (AST.Expr expr : initExpr.initExprList) collectNames(expr);
            }
            case AST.NameExpr nameExpr -> usedNames.add(nameExpr.name);
            default -> {
            }
        }
    }

    private void lowerBlockInPlace(AST.BlockStmt blockStmt) {
        List<AST.Stmt> lowered = new ArrayList<>();
        for (AST.Stmt stmt : blockStmt.stmtList) {
            lowered.addAll(lowerStmt(stmt));
        }
        blockStmt.stmtList.clear();
        blockStmt.stmtList.addAll(lowered);
    }

    private List<AST.Stmt> lowerStmt(AST.Stmt stmt) {
        List<AST.Stmt> out = new ArrayList<>();
        switch (stmt) {
            case AST.BlockStmt blockStmt -> {
                lowerBlockInPlace(blockStmt);
                out.add(blockStmt);
            }
            case AST.VarStmt varStmt -> {
                LoweredExpr expr = lowerExpr(varStmt.expr);
                out.addAll(expr.prefix);
                out.add(new AST.VarStmt(varStmt.varName, expr.expr, varStmt.lineNumber));
            }
            case AST.AssignStmt assignStmt -> {
                LoweredExpr rhs = lowerExpr(assignStmt.rhs);
                out.addAll(rhs.prefix);
                out.add(new AST.AssignStmt(copyName(assignStmt.nameExpr), rhs.expr, assignStmt.lineNumber));
            }
            case AST.ExprStmt exprStmt -> {
                LoweredExpr expr = lowerExpr(exprStmt.expr);
                out.addAll(expr.prefix);
                out.add(new AST.ExprStmt(expr.expr, exprStmt.lineNumber));
            }
            case AST.ReturnStmt returnStmt -> {
                if (returnStmt.expr == null) {
                    out.add(returnStmt);
                }
                else {
                    LoweredExpr expr = lowerExpr(returnStmt.expr);
                    out.addAll(expr.prefix);
                    out.add(new AST.ReturnStmt(expr.expr, returnStmt.lineNumber));
                }
            }
            case AST.IfElseStmt ifElseStmt -> {
                LoweredExpr condition = lowerExpr(ifElseStmt.condition);
                out.addAll(condition.prefix);
                out.add(new AST.IfElseStmt(
                        condition.expr,
                        lowerAsSingleStmt(ifElseStmt.ifStmt),
                        ifElseStmt.elseStmt == null ? null : lowerAsSingleStmt(ifElseStmt.elseStmt),
                        ifElseStmt.lineNumber));
            }
            case AST.WhileStmt whileStmt -> out.add(lowerWhile(whileStmt));
            default -> out.add(stmt);
        }
        return out;
    }

    private AST.WhileStmt lowerWhile(AST.WhileStmt whileStmt) {
        LoweredExpr condition = lowerExpr(whileStmt.condition);
        AST.Stmt body = lowerAsSingleStmt(whileStmt.stmt);
        if (condition.prefix.isEmpty()) {
            AST.WhileStmt loweredWhile = new AST.WhileStmt(condition.expr, whileStmt.lineNumber);
            loweredWhile.stmt = body;
            return loweredWhile;
        }

        AST.WhileStmt loweredWhile = new AST.WhileStmt(intLiteral(1, whileStmt.lineNumber), whileStmt.lineNumber);
        AST.BlockStmt loopBody = new AST.BlockStmt(whileStmt.lineNumber);
        loopBody.stmtList.addAll(condition.prefix);
        AST.Expr negatedCondition = new AST.UnaryExpr(Token.newPunct("!", whileStmt.lineNumber), condition.expr, whileStmt.lineNumber);
        loopBody.stmtList.add(new AST.IfElseStmt(negatedCondition, new AST.BreakStmt(loweredWhile, whileStmt.lineNumber), null, whileStmt.lineNumber));
        loopBody.stmtList.add(body);
        loweredWhile.stmt = loopBody;
        return loweredWhile;
    }
    private AST.Stmt lowerAsSingleStmt(AST.Stmt stmt) {
        List<AST.Stmt> lowered = lowerStmt(stmt);
        if (lowered.size() == 1) return lowered.get(0);
        AST.BlockStmt block = new AST.BlockStmt(stmt.lineNumber);
        block.stmtList.addAll(lowered);
        return block;
    }

    private LoweredExpr lowerExpr(AST.Expr expr) {
        if (expr instanceof AST.BinaryExpr binaryExpr && isShortCircuit(binaryExpr)) {
            return lowerShortCircuit(binaryExpr);
        }

        List<AST.Stmt> prefix = new ArrayList<>();
        AST.Expr lowered = switch (expr) {
            case AST.BinaryExpr binaryExpr -> {
                LoweredExpr lhs = lowerExpr(binaryExpr.expr1);
                LoweredExpr rhs = lowerExpr(binaryExpr.expr2);
                prefix.addAll(lhs.prefix);
                prefix.addAll(rhs.prefix);
                yield new AST.BinaryExpr(binaryExpr.op, lhs.expr, rhs.expr, binaryExpr.lineNumber);
            }
            case AST.UnaryExpr unaryExpr -> {
                LoweredExpr inner = lowerExpr(unaryExpr.expr);
                prefix.addAll(inner.prefix);
                yield new AST.UnaryExpr(unaryExpr.op, inner.expr, unaryExpr.lineNumber);
            }
            case AST.ArrayLoadExpr arrayLoadExpr -> {
                LoweredExpr array = lowerExpr(arrayLoadExpr.array);
                LoweredExpr index = lowerExpr(arrayLoadExpr.expr);
                prefix.addAll(array.prefix);
                prefix.addAll(index.prefix);
                yield new AST.ArrayLoadExpr(array.expr, index.expr, arrayLoadExpr.lineNumber);
            }
            case AST.ArrayStoreExpr arrayStoreExpr -> {
                LoweredExpr array = lowerExpr(arrayStoreExpr.array);
                LoweredExpr index = lowerExpr(arrayStoreExpr.expr);
                LoweredExpr value = lowerExpr(arrayStoreExpr.value);
                prefix.addAll(array.prefix);
                prefix.addAll(index.prefix);
                prefix.addAll(value.prefix);
                yield copyArrayStore(arrayStoreExpr, array.expr, index.expr, value.expr);
            }
            case AST.GetFieldExpr getFieldExpr -> {
                LoweredExpr object = lowerExpr(getFieldExpr.object);
                prefix.addAll(object.prefix);
                yield new AST.GetFieldExpr(object.expr, getFieldExpr.fieldName, getFieldExpr.lineNumber);
            }
            case AST.SetFieldExpr setFieldExpr -> {
                LoweredExpr object = lowerExpr(setFieldExpr.object);
                LoweredExpr value = lowerExpr(setFieldExpr.value);
                prefix.addAll(object.prefix);
                prefix.addAll(value.prefix);
                yield copySetField(setFieldExpr, object.expr, value.expr);
            }
            case AST.CallExpr callExpr -> {
                LoweredExpr callee = lowerExpr(callExpr.callee);
                prefix.addAll(callee.prefix);
                List<AST.Expr> args = new ArrayList<>();
                for (AST.Expr arg : callExpr.args) {
                    LoweredExpr loweredArg = lowerExpr(arg);
                    prefix.addAll(loweredArg.prefix);
                    args.add(loweredArg.expr);
                }
                yield new AST.CallExpr(callee.expr, args, callExpr.lineNumber);
            }
            case AST.NewExpr newExpr -> {
                if (newExpr.len == null) yield newExpr;
                LoweredExpr len = lowerExpr(newExpr.len);
                prefix.addAll(len.prefix);
                AST.Expr initValue = null;
                if (newExpr.initValue != null) {
                    LoweredExpr value = lowerExpr(newExpr.initValue);
                    prefix.addAll(value.prefix);
                    initValue = value.expr;
                }
                yield new AST.NewExpr(newExpr.typeExpr, len.expr, initValue, newExpr.lineNumber);
            }
            case AST.InitExpr initExpr -> {
                LoweredExpr newExpr = lowerExpr(initExpr.newExpr);
                prefix.addAll(newExpr.prefix);
                List<AST.Expr> initExprs = new ArrayList<>();
                for (AST.Expr init : initExpr.initExprList) {
                    LoweredExpr loweredInit = lowerExpr(init);
                    prefix.addAll(loweredInit.prefix);
                    initExprs.add(loweredInit.expr);
                }
                yield new AST.InitExpr((AST.NewExpr) newExpr.expr, initExprs, initExpr.lineNumber);
            }
            case AST.NameExpr nameExpr -> copyName(nameExpr);
            default -> expr;
        };
        return new LoweredExpr(prefix, lowered);
    }

    private LoweredExpr lowerShortCircuit(AST.BinaryExpr binaryExpr) {
        String tmp = nextTempName();
        List<AST.Stmt> prefix = new ArrayList<>();
        LoweredExpr lhs = lowerExpr(binaryExpr.expr1);
        LoweredExpr rhs = lowerExpr(binaryExpr.expr2);
        prefix.addAll(lhs.prefix);
        prefix.add(new AST.VarStmt(tmp, intLiteral(binaryExpr.op.str.equals("&&") ? 0 : 1, binaryExpr.lineNumber), binaryExpr.lineNumber));

        AST.BlockStmt branch = new AST.BlockStmt(binaryExpr.lineNumber);
        branch.stmtList.addAll(rhs.prefix);
        branch.stmtList.add(new AST.AssignStmt(new AST.NameExpr(tmp, binaryExpr.lineNumber), rhs.expr, binaryExpr.lineNumber));

        AST.Expr condition = lhs.expr;
        if (binaryExpr.op.str.equals("||")) {
            condition = new AST.UnaryExpr(Token.newPunct("!", binaryExpr.lineNumber), condition, binaryExpr.lineNumber);
        }
        prefix.add(new AST.IfElseStmt(condition, branch, null, binaryExpr.lineNumber));
        return new LoweredExpr(prefix, new AST.NameExpr(tmp, binaryExpr.lineNumber));
    }

    private boolean isShortCircuit(AST.BinaryExpr binaryExpr) {
        return binaryExpr.op.str.equals("&&") || binaryExpr.op.str.equals("||");
    }

    private AST.NameExpr copyName(AST.NameExpr nameExpr) {
        return new AST.NameExpr(nameExpr.name, nameExpr.lineNumber);
    }

    private AST.Expr copyArrayStore(AST.ArrayStoreExpr original, AST.Expr array, AST.Expr index, AST.Expr value) {
        if (original instanceof AST.ArrayInitExpr) {
            return new AST.ArrayInitExpr(array, index, value, original.lineNumber);
        }
        return new AST.ArrayStoreExpr(array, index, value, original.lineNumber);
    }

    private AST.Expr copySetField(AST.SetFieldExpr original, AST.Expr object, AST.Expr value) {
        if (original instanceof AST.InitFieldExpr) {
            return new AST.InitFieldExpr(object, original.fieldName, value, original.lineNumber);
        }
        return new AST.SetFieldExpr(object, original.fieldName, value, original.lineNumber);
    }

    private AST.LiteralExpr intLiteral(int value, int lineNumber) {
        return new AST.LiteralExpr(Token.newNum(value, Integer.toString(value), lineNumber));
    }

    private String nextTempName() {
        String name;
        do {
            name = "__sc" + nextTemp++;
        } while (usedNames.contains(name));
        usedNames.add(name);
        return name;
    }

    private record LoweredExpr(List<AST.Stmt> prefix, AST.Expr expr) {
    }
}
