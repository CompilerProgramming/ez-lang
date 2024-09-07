package com.compilerprogramming.ezlang.bytecode;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.Type;

public class FunctionBuilder {

    BasicBlock entry;
    BasicBlock exit;
    int bid = 0;
    BasicBlock currentBlock;
    BasicBlock currentBreakTarget;
    BasicBlock currentContinueTarget;

    public FunctionBuilder(Symbol.FunctionTypeSymbol functionSymbol) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        setVirtualRegisters(funcDecl.scope);
        this.bid = 0;
        this.entry = this.currentBlock = createBlock();
        this.exit = createBlock();
        this.currentBreakTarget = null;
        this.currentContinueTarget = null;
        compileStatement(funcDecl.block);
    }

    private void setVirtualRegisters(Scope scope) {
        int reg = 0;
        if (scope.parent != null)
            reg = scope.parent.maxReg;
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.reg = reg++;
            }
        }
        scope.maxReg = reg;
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    private BasicBlock createBlock() {
        return new BasicBlock(bid++);
    }

    private BasicBlock createLoopHead() {
        return new BasicBlock(bid++, true);
    }

    private void compileBlock(AST.BlockStmt block) {
        for (AST.Stmt stmt: block.stmtList) {
            compileStatement(stmt);
        }
    }

    private void compileReturn(AST.ReturnStmt returnStmt) {
        if (returnStmt.expr != null) {
            boolean indexed = compileExpr(returnStmt.expr);
            if (indexed)
                code(new Instruction.LoadIndexed());
        }
        jumpTo(exit);
    }

    private void code(Instruction instruction) {
        currentBlock.add(instruction);
    }

    private void compileStatement(AST.Stmt statement) {
        switch (statement) {
            case AST.BlockStmt blockStmt -> {
                compileBlock(blockStmt);
            }
            case AST.VarStmt letStmt -> {
                compileLet(letStmt);
            }
            case AST.IfElseStmt ifElseStmt -> {
                compileIf(ifElseStmt);
            }
            case AST.WhileStmt whileStmt -> {
                compileWhile(whileStmt);
            }
            case AST.ContinueStmt continueStmt -> {
                compileContinue(continueStmt);
            }
            case AST.BreakStmt breakStmt -> {
                compileBreak(breakStmt);
            }
            case AST.ReturnStmt returnStmt -> {
                compileReturn(returnStmt);
            }
            case AST.AssignStmt assignStmt -> {
                compileAssign(assignStmt);
            }
            case AST.ExprStmt exprStmt -> {
                compileExprStmt(exprStmt);
            }
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        }
    }

    private void compileAssign(AST.AssignStmt assignStmt) {
        boolean indexedLhs = false;
        if (!(assignStmt.lhs instanceof AST.NameExpr))
            indexedLhs = compileExpr(assignStmt.lhs);
        boolean indexedRhs = compileExpr(assignStmt.rhs);
        if (indexedRhs)
            code(new Instruction.LoadIndexed());
        if (indexedLhs)
            code(new Instruction.StoreIndexed());
        else if (assignStmt.lhs instanceof AST.NameExpr symbolExpr) {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            code(new Instruction.Store(varSymbol.reg));
        }
        else
            throw new CompilerException("Invalid assignment expression: " + assignStmt.lhs);
    }

    private void compileExprStmt(AST.ExprStmt exprStmt) {
        boolean indexed = compileExpr(exprStmt.expr);
        if (indexed)
            code(new Instruction.LoadIndexed());
        code(new Instruction.Pop());
    }

    private void compileContinue(AST.ContinueStmt continueStmt) {
        if (currentContinueTarget == null)
            throw new CompilerException("No continue target found");
        jumpTo(currentContinueTarget);
    }

    private void compileBreak(AST.BreakStmt breakStmt) {
        if (currentBreakTarget == null)
            throw new CompilerException("No break target found");
        jumpTo(currentBreakTarget);
    }

    private void compileWhile(AST.WhileStmt whileStmt) {
        BasicBlock loopBlock = createLoopHead();
        BasicBlock bodyBlock = createBlock();
        BasicBlock exitBlock = createBlock();
        BasicBlock savedBreakTarget = currentBreakTarget;
        BasicBlock savedContinueTarget = currentContinueTarget;
        currentBreakTarget = exitBlock;
        currentContinueTarget = loopBlock;
        startBlock(loopBlock);
        boolean indexed = compileExpr(whileStmt.condition);
        if (indexed)
            code(new Instruction.LoadIndexed());
        code(new Instruction.ConditionalBranch(currentBlock, bodyBlock, exitBlock));
        startBlock(bodyBlock);
        compileStatement(whileStmt.stmt);
        if (!isBlockTerminated(currentBlock))
            jumpTo(loopBlock);
        startBlock(exitBlock);
        currentContinueTarget = savedContinueTarget;
        currentBreakTarget = savedBreakTarget;
    }

    private boolean isBlockTerminated(BasicBlock block) {
        return (block.instructions.size() > 0 &&
                block.instructions.getLast().isTerminal());
    }

    private void jumpTo(BasicBlock block) {
        currentBlock.add(new Instruction.Jump(block));
        currentBlock.addSuccessor(block);
    }

    private void startBlock(BasicBlock block) {
        if (!isBlockTerminated(currentBlock)) {
            jumpTo(block);
        }
        currentBlock = block;
    }

    private void compileIf(AST.IfElseStmt ifElseStmt) {
        BasicBlock ifBlock = createBlock();
        boolean needElse = ifElseStmt.elseStmt != null;
        BasicBlock elseBlock = needElse ? createBlock() : null;
        BasicBlock exitBlock = createBlock();
        boolean indexed = compileExpr(ifElseStmt.condition);
        if (indexed)
            code(new Instruction.LoadIndexed());
        code(new Instruction.ConditionalBranch(currentBlock, ifBlock, needElse ? elseBlock : exitBlock));
        startBlock(ifBlock);
        compileStatement(ifElseStmt.ifStmt);
        if (!isBlockTerminated(currentBlock))
            jumpTo(exitBlock);
        if (elseBlock != null) {
            startBlock(elseBlock);
            compileStatement(ifElseStmt.elseStmt);
            if (!isBlockTerminated(currentBlock))
                jumpTo(exitBlock);
        }
        startBlock(exitBlock);
    }

    private void compileLet(AST.VarStmt letStmt) {
        if (letStmt.expr != null) {
            boolean indexed = compileExpr(letStmt.expr);
            if (indexed)
                code(new Instruction.LoadIndexed());
            code(new Instruction.Store(letStmt.symbol.reg));
        }
    }

    private boolean compileExpr(AST.Expr expr) {
        switch (expr) {
            case AST.LiteralExpr constantExpr -> {
                return compileConstantExpr(constantExpr);
            }
            case AST.BinaryExpr binaryExpr -> {
                return compileBinaryExpr(binaryExpr);
            }
            case AST.UnaryExpr unaryExpr -> {
                return compileUnaryExpr(unaryExpr);
            }
            case AST.NameExpr symbolExpr -> {
                return compileSymbolExpr(symbolExpr);
            }
            case AST.NewExpr newExpr -> {
                return compileNewExpr(newExpr);
            }
            case AST.ArrayIndexExpr arrayIndexExpr -> {
                return compileArrayIndexExpr(arrayIndexExpr);
            }
            case AST.FieldExpr fieldExpr -> {
                return compileFieldExpr(fieldExpr);
            }
            case AST.SetFieldExpr setFieldExpr -> {
                return compileSetFieldExpr(setFieldExpr);
            }
            case AST.CallExpr callExpr -> {
                return compileCallExpr(callExpr);
            }
            default -> throw new IllegalStateException("Unexpected value: " + expr);
        }
    }

    private boolean compileCallExpr(AST.CallExpr callExpr) {
        compileExpr(callExpr.callee);
        for (AST.Expr expr: callExpr.args) {
            boolean indexed = compileExpr(expr);
            if (indexed)
                code(new Instruction.LoadIndexed());
        }
        code(new Instruction.Call(callExpr.args.size()));
        return false;
    }

    private Type.TypeStruct getStructType(Type t) {
        if (t instanceof Type.TypeStruct typeStruct) {
            return typeStruct;
        }
        else if (t instanceof Type.TypeNullable ptr &&
                ptr.baseType instanceof Type.TypeStruct typeStruct) {
            return typeStruct;
        }
        else
            throw new CompilerException("Unexpected type: " + t);
    }

    private boolean compileFieldExpr(AST.FieldExpr fieldExpr) {
        Type.TypeStruct typeStruct = getStructType(fieldExpr.object.type);
        int fieldIndex = typeStruct.getFieldIndex(fieldExpr.fieldName);
        if (fieldIndex < 0)
            throw new CompilerException("Field " + fieldExpr.fieldName + " not found");
        boolean indexed = compileExpr(fieldExpr.object);
        if (indexed)
            code(new Instruction.LoadIndexed());
        code(new Instruction.PushConst(fieldIndex));
        return true;
    }

    private boolean compileArrayIndexExpr(AST.ArrayIndexExpr arrayIndexExpr) {
        compileExpr(arrayIndexExpr.array);
        boolean indexed = compileExpr(arrayIndexExpr.expr);
        if (indexed)
            code(new Instruction.LoadIndexed());
        return true;
    }

    private boolean compileSetFieldExpr(AST.SetFieldExpr setFieldExpr) {
        Type.TypeStruct structType = (Type.TypeStruct) setFieldExpr.objectType;
        int fieldIndex = structType.getFieldIndex(setFieldExpr.fieldName);
        if (fieldIndex == -1)
            throw new CompilerException("Field " + setFieldExpr.fieldName + " not found in struct " + structType.name);
        code(new Instruction.PushConst(fieldIndex));
        boolean indexed = compileExpr(setFieldExpr.value);
        if (indexed)
            code(new Instruction.LoadIndexed());
        code(new Instruction.StoreIndexed());
        return false;
    }

    private boolean compileNewExpr(AST.NewExpr newExpr) {
        code(new Instruction.New(newExpr.type));
        if (newExpr.initExprList != null && !newExpr.initExprList.isEmpty()) {
            if (newExpr.type instanceof Type.TypeArray) {
                for (AST.Expr expr : newExpr.initExprList) {
                    // Maybe have specific AST similar to how we have SetFieldExpr?
                    boolean indexed = compileExpr(expr);
                    if (indexed)
                        code(new Instruction.LoadIndexed());
                    code(new Instruction.StoreAppend());
                }
            }
            else if (newExpr.type instanceof Type.TypeStruct) {
                for (AST.Expr expr : newExpr.initExprList) {
                    compileExpr(expr);
                }
            }
        }
        return false;
    }

    private boolean compileSymbolExpr(AST.NameExpr symbolExpr) {
        if (symbolExpr.type instanceof Type.TypeFunction functionType)
            code(new Instruction.LoadFunction(functionType));
        else {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            code(new Instruction.LoadVar(varSymbol.reg));
        }
        return false;
    }

    private boolean compileBinaryExpr(AST.BinaryExpr binaryExpr) {
        int opCode = 0;
        boolean indexed = compileExpr(binaryExpr.expr1);
        if (indexed)
            code(new Instruction.LoadIndexed());
        indexed = compileExpr(binaryExpr.expr2);
        if (indexed)
            code(new Instruction.LoadIndexed());
        switch (binaryExpr.op.str) {
            case "+" -> opCode = Instruction.ADD_I;
            case "-" -> opCode = Instruction.SUB_I;
            case "*" -> opCode = Instruction.MUL_I;
            case "/" -> opCode = Instruction.DIV_I;
            case "%" -> opCode = Instruction.MOD_I;
            case "==" -> opCode = Instruction.EQ;
            case "!=" -> opCode = Instruction.NE;
            case "<" -> opCode = Instruction.LT;
            case ">" -> opCode = Instruction.GT;
            case "<=" -> opCode = Instruction.LE;
            case ">=" -> opCode = Instruction.GE;
            default -> throw new CompilerException("Invalid binary op");
        }
        code(new Instruction.BinaryOp(opCode));
        return false;
    }

    private boolean compileUnaryExpr(AST.UnaryExpr unaryExpr) {
        int opCode = 0;
        boolean indexed = compileExpr(unaryExpr.expr);
        if (indexed)
            code(new Instruction.LoadIndexed());
        switch (unaryExpr.op.str) {
            case "-" -> opCode = Instruction.NEG_I;
            case "!" -> opCode = Instruction.NOT;
            default -> throw new CompilerException("Invalid binary op");
        }
        code(new Instruction.UnaryOp(opCode));
        return false;
    }

    private boolean compileConstantExpr(AST.LiteralExpr constantExpr) {
        code(new Instruction.PushConst(constantExpr.value.num.intValue()));
        return false;
    }

}
