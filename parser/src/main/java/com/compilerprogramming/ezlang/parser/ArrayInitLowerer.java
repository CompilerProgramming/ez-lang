package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers array creation with an initial element value into an allocation
 * followed by an explicit fill loop.
 *
 * <pre>
 *     new [T]{len=n, value=v, e0, e1}
 * </pre>
 * becomes
 * <pre>
 *     var __ai0 = n                         // len, evaluated once
 *     var __ai1 = v                         // value, evaluated once
 *     var __ai2 = new [T]{len=__ai0, e0, e1}
 *     var __ai3 = 2                         // skip the explicit elements
 *     while (__ai3 < __ai0) { __ai2[__ai3] = __ai1; __ai3 = __ai3 + 1 }
 *     ... __ai2
 * </pre>
 * This matches the interpreters, which fill every element with the value and
 * then apply the explicit element initializers.  Literal len/value are used
 * directly rather than copied into temporaries, and a literal zero or null
 * value needs no loop at all because new arrays are zero filled.
 *
 * The statements are hoisted in front of the statement containing the array
 * creation, so len and value are evaluated before any other part of that
 * statement.  Like {@link ShortCircuitLowerer} this pass runs after semantic
 * analysis, and the program must be bound again afterwards.
 *
 * Used by backends such as SoN that only support zero-filled allocation.
 */
public final class ArrayInitLowerer extends AstLowerer {
    private ArrayInitLowerer(AST.Program program) {
        super(program, "__ai");
    }

    public static void lower(AST.Program program) {
        new ArrayInitLowerer(program).lowerProgram(program);
    }

    @Override
    protected LoweredExpr lowerSpecial(AST.Expr expr) {
        if (expr instanceof AST.InitExpr initExpr && initExpr.newExpr.initValue != null)
            return lowerArrayInit(initExpr);
        return null;
    }

    private LoweredExpr lowerArrayInit(AST.InitExpr initExpr) {
        AST.NewExpr newExpr = initExpr.newExpr;
        int line = initExpr.lineNumber;
        List<AST.Stmt> prefix = new ArrayList<>();

        // Evaluate len, then value, once each
        LoweredExpr len = lowerExpr(newExpr.len);
        prefix.addAll(len.prefix());
        AST.Expr lenExpr = len.expr();
        String lenTmp = null;
        if (!(lenExpr instanceof AST.LiteralExpr)) {
            lenTmp = nextTempName();
            prefix.add(new AST.VarStmt(lenTmp, lenExpr, line));
        }
        LoweredExpr value = lowerExpr(newExpr.initValue);
        prefix.addAll(value.prefix());
        AST.Expr valueExpr = value.expr();
        String valueTmp = null;
        if (!(valueExpr instanceof AST.LiteralExpr)) {
            valueTmp = nextTempName();
            prefix.add(new AST.VarStmt(valueTmp, valueExpr, line));
        }

        // Allocate, applying the explicit element initializers.  The len= and
        // value= entries are dropped: len is carried by the NewExpr, and value
        // is applied by the loop below.
        List<AST.Expr> inits = new ArrayList<>();
        int explicitElements = 0;
        for (AST.Expr init : initExpr.initExprList) {
            if (init instanceof AST.InitFieldExpr) continue;
            if (init instanceof AST.ArrayInitExpr) explicitElements++;
            LoweredExpr lowered = lowerExpr(init);
            prefix.addAll(lowered.prefix());
            inits.add(lowered.expr());
        }
        AST.NewExpr alloc = new AST.NewExpr(newExpr.typeExpr, lenRef(lenTmp, lenExpr, line), null, newExpr.lineNumber);
        String arrayTmp = nextTempName();
        prefix.add(new AST.VarStmt(arrayTmp, new AST.InitExpr(alloc, inits, line), line));

        // Fill the remaining elements, unless the value is the zero fill
        if (!isZeroLiteral(valueExpr)) {
            String idx = nextTempName();
            prefix.add(new AST.VarStmt(idx, intLiteral(explicitElements, line), line));
            AST.WhileStmt loop = new AST.WhileStmt(
                    new AST.BinaryExpr(Token.newPunct("<", line), name(idx, line), lenRef(lenTmp, lenExpr, line), line),
                    line);
            AST.BlockStmt body = new AST.BlockStmt(line);
            AST.Expr fill = valueTmp != null ? name(valueTmp, line) : valueExpr;
            body.stmtList.add(new AST.ExprStmt(new AST.ArrayStoreExpr(name(arrayTmp, line), name(idx, line), fill, line), line));
            body.stmtList.add(new AST.AssignStmt(name(idx, line),
                    new AST.BinaryExpr(Token.newPunct("+", line), name(idx, line), intLiteral(1, line), line),
                    line));
            loop.stmt = body;
            prefix.add(loop);
        }
        return new LoweredExpr(prefix, name(arrayTmp, line));
    }

    private static AST.Expr lenRef(String lenTmp, AST.Expr lenLiteral, int line) {
        return lenTmp != null ? name(lenTmp, line) : lenLiteral;
    }

    private static AST.NameExpr name(String name, int line) {
        return new AST.NameExpr(name, line);
    }

    private static boolean isZeroLiteral(AST.Expr expr) {
        if (!(expr instanceof AST.LiteralExpr literal)) return false;
        Token token = literal.value;
        if (token.kind == Token.Kind.IDENT) return token.str.equals("null");
        return token.num != null && token.num.doubleValue() == 0.0;
    }
}
