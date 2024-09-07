package com.compilerprogramming.ezlang.parser;

public interface ASTVisitor {
    ASTVisitor visit(AST.Program program, boolean enter);
    ASTVisitor visit(AST.FuncDecl funcDecl, boolean enter);
    ASTVisitor visit(AST.StructDecl structDecl, boolean enter);
    ASTVisitor visit(AST.VarDecl varDecl, boolean enter);
    ASTVisitor visit(AST.BinaryExpr binaryExpr, boolean enter);
    ASTVisitor visit(AST.UnaryExpr unaryExpr, boolean enter);
    ASTVisitor visit(AST.FieldExpr fieldExpr, boolean enter);
    ASTVisitor visit(AST.CallExpr callExpr, boolean enter);
    ASTVisitor visit(AST.SetFieldExpr setFieldExpr, boolean enter);
    ASTVisitor visit(AST.SimpleTypeExpr simpleTypeExpr, boolean enter);
    ASTVisitor visit(AST.NullableSimpleTypeExpr simpleTypeExpr, boolean enter);
    ASTVisitor visit(AST.ArrayTypeExpr arrayTypeExpr, boolean enter);
    ASTVisitor visit(AST.NullableArrayTypeExpr arrayTypeExpr, boolean enter);
    ASTVisitor visit(AST.ReturnTypeExpr returnTypeExpr, boolean enter);
    ASTVisitor visit(AST.LiteralExpr literalExpr, boolean enter);
    ASTVisitor visit(AST.ArrayIndexExpr arrayIndexExpr, boolean enter);
    ASTVisitor visit(AST.NewExpr newExpr, boolean enter);
    ASTVisitor visit(AST.NameExpr nameExpr, boolean enter);
    ASTVisitor visit(AST.BreakStmt breakStmt, boolean enter);
    ASTVisitor visit(AST.ContinueStmt continueStmt, boolean enter);
    ASTVisitor visit(AST.ReturnStmt returnStmt, boolean enter);
    ASTVisitor visit(AST.IfElseStmt ifElseStmt, boolean enter);
    ASTVisitor visit(AST.WhileStmt whileStmt, boolean enter);
    ASTVisitor visit(AST.VarStmt varStmt, boolean enter);
    ASTVisitor visit(AST.BlockStmt blockStmt, boolean enter);
    ASTVisitor visit(AST.VarDeclStmt varDeclStmt, boolean enter);
    ASTVisitor visit(AST.ExprStmt exprStmt, boolean enter);
    ASTVisitor visit(AST.AssignStmt assignStmt, boolean enter);
}
