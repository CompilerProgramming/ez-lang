package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers short-circuit boolean expressions into ordinary control flow before
 * semantic analysis gives names and temporaries symbols.
 *
 * Some backend such as the SON do not
 * support compiling boolean short-circuit operators
 * so we lower them to if blocks. But this is also
 * done during tests to prove that the lowering works.
 */
public final class ShortCircuitLowerer extends AstLowerer {
    private ShortCircuitLowerer(AST.Program program) {
        super(program, "__sc");
    }

    public static void lower(AST.Program program) {
        new ShortCircuitLowerer(program).lowerProgram(program);
    }

    @Override
    protected LoweredExpr lowerSpecial(AST.Expr expr) {
        if (expr instanceof AST.BinaryExpr binaryExpr && isShortCircuit(binaryExpr))
            return lowerShortCircuit(binaryExpr);
        return null;
    }

    private LoweredExpr lowerShortCircuit(AST.BinaryExpr binaryExpr) {
        String tmp = nextTempName();
        List<AST.Stmt> prefix = new ArrayList<>();
        LoweredExpr lhs = lowerExpr(binaryExpr.expr1);
        LoweredExpr rhs = lowerExpr(binaryExpr.expr2);
        prefix.addAll(lhs.prefix());
        prefix.add(new AST.VarStmt(tmp, intLiteral(binaryExpr.op.str.equals("&&") ? 0 : 1, binaryExpr.lineNumber), binaryExpr.lineNumber));

        AST.BlockStmt branch = new AST.BlockStmt(binaryExpr.lineNumber);
        branch.stmtList.addAll(rhs.prefix());
        branch.stmtList.add(new AST.AssignStmt(new AST.NameExpr(tmp, binaryExpr.lineNumber), rhs.expr(), binaryExpr.lineNumber));

        AST.Expr condition = lhs.expr();
        if (binaryExpr.op.str.equals("||")) {
            condition = new AST.UnaryExpr(Token.newPunct("!", binaryExpr.lineNumber), condition, binaryExpr.lineNumber);
        }
        prefix.add(new AST.IfElseStmt(condition, branch, null, binaryExpr.lineNumber));
        return new LoweredExpr(prefix, new AST.NameExpr(tmp, binaryExpr.lineNumber));
    }

    private boolean isShortCircuit(AST.BinaryExpr binaryExpr) {
        return binaryExpr.op.str.equals("&&") || binaryExpr.op.str.equals("||");
    }
}
