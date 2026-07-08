package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private Token currentToken;
    private AST.WhileStmt currentWhile;

    public AST.Program parse(Lexer lexer) {
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private void nextToken(Lexer lexer) {
        currentToken = lexer.scan();
    }

    private void error(Token t, String errorMessage) {
        throw new CompilerException("Line " + t.lineNumber + ": " + errorMessage + " got " + t.str, t.lineNumber);
    }

    private void matchPunctuation(Lexer lexer, String value) {
        if (currentToken.kind == Token.Kind.PUNCT && isToken(currentToken, value)) {
            nextToken(lexer);
        } else {
            error(currentToken, "Syntax error: expected " + value);
        }
    }

    private boolean testPunctuation(Lexer lexer, String value) {
        if (currentToken.kind == Token.Kind.PUNCT && isToken(currentToken, value)) {
            nextToken(lexer);
            return true;
        }
        return false;
    }

    private void matchIdentifier(Lexer lexer, String identifier) {
        if (currentToken.kind == Token.Kind.IDENT && isToken(currentToken, identifier)) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + identifier);
        }
    }

    private boolean isToken(Token token, String value) {
        return token.str.equals(value);
    }

    private AST.Program parseProgram(Lexer lexer) {
        AST.Program program = new AST.Program(lexer.lineNumber());
        parseDefinitions(lexer, program);
        return program;
    }

    private void parseDefinitions(Lexer lexer, AST.Program program) {
        while (currentToken.kind == Token.Kind.IDENT) {
            if ("func".equals(currentToken.str))
                program.decls.add(parseFunction(lexer));
            else if ("struct".equals(currentToken.str))
                program.decls.add(parseStructDeclaration(lexer));
            else
                error(currentToken, "Syntax error: Expected the keyword 'func' or 'struct' at start of a declaration");
        }
    }

    private AST.FuncDecl parseFunction(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        matchIdentifier(lexer, "func");
        if (currentToken.kind != Token.Kind.IDENT)
            error(currentToken, "Syntax error: Function name expected");
        String functionName = currentToken.str;
        nextToken(lexer);
        matchPunctuation(lexer, "(");
        List<AST.VarDecl> params = new ArrayList<>();
        while (currentToken.kind == Token.Kind.IDENT) {
            AST.VarDecl param = parseVarDeclaration(lexer, false, AST.VarType.FUNCTION_PARAMETER);
            params.add(param);
            if (!testPunctuation(lexer, ",")) break;
        }
        matchPunctuation(lexer, ")");
        AST.TypeExpr returnType = null;
        if (testPunctuation(lexer, "->"))
            returnType = parseTypeExpr(lexer);
        AST.BlockStmt block = parseBlock(lexer);
        return new AST.FuncDecl(functionName, params.toArray(new AST.VarDecl[0]), returnType, block, lineNumber);
    }

    private AST.VarDecl parseVarDeclaration(Lexer lexer, boolean expectVar, AST.VarType varType) {
        int lineNumber = lexer.lineNumber();
        if (expectVar)
            matchIdentifier(lexer, "var");
        if (currentToken.kind != Token.Kind.IDENT)
            error(currentToken, "Syntax error: name expected");
        String identifier = currentToken.str;
        nextToken(lexer);
        matchPunctuation(lexer, ":");
        AST.TypeExpr fieldType = parseTypeExpr(lexer);
        return new AST.VarDecl(identifier, varType, fieldType, lineNumber);
    }

    private AST.ArrayTypeExpr parseArrayTypeExpr(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        matchPunctuation(lexer, "[");
        AST.SimpleTypeExpr elementType = parseSimpleTypeExpr(lexer);
        matchPunctuation(lexer, "]");
        boolean isNullable = false;
        if (testPunctuation(lexer, "?"))
            isNullable = true;

        return isNullable ? new AST.NullableArrayTypeExpr(elementType, lineNumber) : new AST.ArrayTypeExpr(elementType, lineNumber);
    }

    private AST.SimpleTypeExpr parseSimpleTypeExpr(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        String typeName = null;
        if (currentToken.kind == Token.Kind.IDENT)
            typeName = currentToken.str;
        else
            error(currentToken, "Expected a type name");
        nextToken(lexer);
        boolean isNullable = false;
        if (testPunctuation(lexer, "?"))
            isNullable = true;
        return isNullable ? new AST.NullableSimpleTypeExpr(typeName, lineNumber) : new AST.SimpleTypeExpr(typeName, lineNumber);
    }

    private AST.TypeExpr parseTypeExpr(Lexer lexer) {
        if (isToken(currentToken, "["))
            return parseArrayTypeExpr(lexer);
        else
            return parseSimpleTypeExpr(lexer);
    }

    private AST.StructDecl parseStructDeclaration(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        matchIdentifier(lexer, "struct");
        String structName = null;
        if (currentToken.kind == Token.Kind.IDENT)
            structName = currentToken.str;
        else
            error(currentToken, "Expected an identifier after struct keyword");
        nextToken(lexer);
        matchPunctuation(lexer, "{");
        List<AST.VarDecl> fields = new ArrayList<>();
        while (currentToken.kind == Token.Kind.IDENT) {
            AST.VarDecl field = parseVarDeclaration(lexer, true, AST.VarType.STRUCT_FIELD);
            fields.add(field);
            testPunctuation(lexer, ";");
        }
        matchPunctuation(lexer, "}");
        return new AST.StructDecl(structName, fields.toArray(new AST.VarDecl[0]), lineNumber);
    }

    private AST.Stmt parseVarDeclOrStmt(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        matchIdentifier(lexer, "var");
        AST.Stmt stmt = null;
        if (currentToken.kind == Token.Kind.IDENT && lexer.peekChar() == '=') {
            String name = currentToken.str;
            nextToken(lexer);
            matchPunctuation(lexer, "=");
            stmt = new AST.VarStmt(name, parseBool(lexer), lineNumber);
        }
        else {
            stmt = new AST.VarDeclStmt(parseVarDeclaration(lexer, false, AST.VarType.VARIABLE), lineNumber);
        }
        testPunctuation(lexer, ";");
        return stmt;
    }

    private AST.Stmt parseStatement(Lexer lexer) {
        AST.Expr x = null;
        AST.Stmt s1;
        AST.Stmt s2;

        int lineNumber = lexer.lineNumber();
        switch (currentToken.str) {
            case "var" -> {
                return parseVarDeclOrStmt(lexer);
            }
            case "if" -> {
                matchIdentifier(lexer, "if");
                matchPunctuation(lexer, "(");
                x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                s1 = parseStatement(lexer);
                if (!isToken(currentToken, "else")) {
                    return new AST.IfElseStmt(x, s1, null, lineNumber);
                }
                matchIdentifier(lexer, "else");
                s2 = parseStatement(lexer);
                return new AST.IfElseStmt(x, s1, s2, lineNumber);
            }
            case "while" -> {
                matchIdentifier(lexer, "while");
                matchPunctuation(lexer, "(");
                x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                var savedWhile = currentWhile;
                var whileStmt = currentWhile = new AST.WhileStmt(x, lineNumber);
                currentWhile.stmt = parseStatement(lexer);
                currentWhile = savedWhile;
                return whileStmt;
            }
            case "break" -> {
                matchIdentifier(lexer, "break");
                testPunctuation(lexer, ";");
                return new AST.BreakStmt(currentWhile, lineNumber);
            }
            case "continue" -> {
                matchIdentifier(lexer, "continue");
                testPunctuation(lexer, ";");
                return new AST.ContinueStmt(currentWhile, lineNumber);
            }
            case "return" -> {
                matchIdentifier(lexer, "return");
                if (!isToken(currentToken, ";")
                    && !isToken(currentToken, "}"))
                    x = parseBool(lexer);
                testPunctuation(lexer, ";");
                return new AST.ReturnStmt(x, lineNumber);
            }
            case "{" -> {
                return parseBlock(lexer);
            }
            default -> {
                return parseAssign(lexer);
            }
        }
    }

    private AST.BlockStmt parseBlock(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        matchPunctuation(lexer, "{");
        var block = new AST.BlockStmt(lineNumber);
        while (currentToken.kind != Token.Kind.EOZ && !testPunctuation(lexer, "}")) {
            block.stmtList.add(parseStatement(lexer));
        }
        return block;
    }

    // Parse assignment or expression statement
    private AST.Stmt parseAssign(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        AST.Expr lhs = parseBool(lexer);
        AST.Expr rhs = null;
        if (testPunctuation(lexer, "="))
            rhs = parseBool(lexer);
        testPunctuation(lexer, ";");
        if (rhs == null)
            return new AST.ExprStmt(lhs, lineNumber);
        else {
            if (lhs instanceof AST.ArrayLoadExpr arrayLoadExpr) {
                return new AST.ExprStmt(new AST.ArrayStoreExpr(arrayLoadExpr.array, arrayLoadExpr.expr, rhs, lineNumber), lineNumber);
            }
            else if (lhs instanceof AST.GetFieldExpr getFieldExpr) {
                return new AST.ExprStmt(new AST.SetFieldExpr(getFieldExpr.object, getFieldExpr.fieldName, rhs, lineNumber), lineNumber);
            }
            else if (lhs instanceof AST.NameExpr nameExpr) {
                return new AST.AssignStmt(nameExpr, rhs, lineNumber);
            }
            else throw new CompilerException("Expected a name, expr[] or expr.field", lineNumber);
        }
    }

    private AST.Expr parseBool(Lexer lexer) {
        var x = parseAnd(lexer);
        while (isToken(currentToken, "||")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new AST.BinaryExpr(tok, x, parseAnd(lexer), tok.lineNumber);
        }
        return x;
    }

    private AST.Expr parseAnd(Lexer lexer) {
        var x = parseRelational(lexer);
        while (isToken(currentToken, "&&")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new AST.BinaryExpr(tok, x, parseRelational(lexer), tok.lineNumber);
        }
        return x;
    }

    private AST.Expr parseRelational(Lexer lexer) {
        var x = parseAddition(lexer);
        while (isToken(currentToken, "==")
                || isToken(currentToken, "!=")
                || isToken(currentToken, "<=")
                || isToken(currentToken, "<")
                || isToken(currentToken, ">")
                || isToken(currentToken, ">=")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new AST.BinaryExpr(tok, x, parseAddition(lexer), tok.lineNumber);
        }
        return x;
    }

    private AST.Expr parseAddition(Lexer lexer) {
        var x = parseMultiplication(lexer);
        while (isToken(currentToken, "-")
                || isToken(currentToken, "+")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new AST.BinaryExpr(tok, x, parseMultiplication(lexer), tok.lineNumber);
        }
        return x;
    }

    private AST.Expr parseMultiplication(Lexer lexer) {
        var x = parseUnary(lexer);
        while (isToken(currentToken, "*")
                || isToken(currentToken, "%")
                || isToken(currentToken, "/")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new AST.BinaryExpr(tok, x, parseUnary(lexer), tok.lineNumber);
        }
        return x;
    }

    private AST.Expr parseUnary(Lexer lexer) {
        if (isToken(currentToken, "-")
                || isToken(currentToken, "!")) {
            var tok = currentToken;
            nextToken(lexer);
            return new AST.UnaryExpr(tok, parseUnary(lexer), tok.lineNumber);
        } else {
            return parsePostfix(lexer, parsePrimary(lexer));
        }
    }

    private AST.Expr parseNew(Lexer lexer) {
        matchIdentifier(lexer, "new");
        int lineNumber = lexer.lineNumber();
        AST.TypeExpr resultType = parseTypeExpr(lexer);
        var newExpr = new AST.NewExpr(resultType, lineNumber);
        AST.Expr lenExpr = null;
        AST.Expr initValueExpr = null;
        List<AST.Expr> initExpr = new ArrayList<>();
        int index = 0;
        if (testPunctuation(lexer, "{")) {
            while (!isToken(currentToken, "}")) {
                if (currentToken.kind == Token.Kind.IDENT && lexer.peekChar() == '=') {
                    String fieldname = currentToken.str;
                    nextToken(lexer);
                    matchPunctuation(lexer, "=");
                    AST.Expr value = parseBool(lexer);
                    initExpr.add(new AST.InitFieldExpr(newExpr, fieldname, value, lineNumber));
                    if (fieldname.equals("len"))
                        lenExpr = value;
                    else if (fieldname.equals("value"))
                        initValueExpr = value;
                }
                else {
                    var indexLit = Integer.valueOf(index++);
                    var indexExpr = new AST.LiteralExpr(Token.newNum(indexLit,indexLit.toString(),currentToken.lineNumber));
                    initExpr.add(new AST.ArrayInitExpr(newExpr, indexExpr, parseBool(lexer), lineNumber));
                }
                if (isToken(currentToken, ","))
                    nextToken(lexer);
                else break;
            }
        }
        matchPunctuation(lexer, "}");
        if (initExpr.size() > 0 && lenExpr == null) {
            var sizeLit = Integer.valueOf(initExpr.size());
            lenExpr = new AST.LiteralExpr(Token.newNum(sizeLit,sizeLit.toString(),currentToken.lineNumber));
        }
        if (lenExpr != null)
            return new AST.InitExpr(new AST.NewExpr(newExpr.typeExpr, lenExpr, initValueExpr, lineNumber), initExpr, lineNumber);
        return new AST.InitExpr(newExpr, initExpr, lineNumber);
    }

    private AST.Expr parsePrimary(Lexer lexer) {
        int lineNumber = lexer.lineNumber();
        switch (currentToken.kind) {
            case PUNCT -> {
                /* Nested expression */
                matchPunctuation(lexer, "(");
                var x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                return x;
            }
            case NUM -> {
                var x = new AST.LiteralExpr(currentToken);
                nextToken(lexer);
                return x;
            }
            case IDENT -> {
                if (isToken(currentToken, "null")) {
                    var x = new AST.LiteralExpr(currentToken);
                    nextToken(lexer);
                    return x;
                }
                else if (isToken(currentToken, "new")) {
                    return parseNew(lexer);
                }
                else {
                    var x = new AST.NameExpr(currentToken.str, lineNumber);
                    nextToken(lexer);
                    return x;
                }
            }
            default -> {
                error(currentToken, "syntax error, expected nested expr, numeric value or variable");
                return null;
            }
        }
    }

    private AST.Expr parsePostfix(Lexer lexer, AST.Expr primaryExpr) {
        AST.Expr prevExpr = primaryExpr;
        while (isToken(currentToken, "[")
                || isToken(currentToken, "(")
                || isToken(currentToken, ".")) {
            Token tok = currentToken;
            nextToken(lexer);
            switch (tok.str) {
                case "[" -> {
                    AST.Expr expr = parseBool(lexer);
                    prevExpr = new AST.ArrayLoadExpr(prevExpr, expr, tok.lineNumber);
                    matchPunctuation(lexer, "]");
                }
                case "." -> {
                    if (currentToken.kind == Token.Kind.IDENT) {
                        prevExpr = new AST.GetFieldExpr(prevExpr, currentToken.str, currentToken.lineNumber);
                        nextToken(lexer);
                    }
                    else
                        error(currentToken, "Syntax error: Expected name after .");
                }
                case "(" -> {
                    List<AST.Expr> args = new ArrayList<>();
                    while (!isToken(currentToken, ")")) {
                        args.add(parseBool(lexer));
                        if (isToken(currentToken, ","))
                            nextToken(lexer);
                        else break;
                    }
                    matchPunctuation(lexer, ")");
                    prevExpr = new AST.CallExpr(prevExpr, args, currentToken.lineNumber);
                }
                default -> error(currentToken, "Syntax error: expected a postfix operator [ . or C");
            }
        }
        return prevExpr;
    }
}

