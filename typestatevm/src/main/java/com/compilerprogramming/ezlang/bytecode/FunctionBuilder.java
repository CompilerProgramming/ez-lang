package com.compilerprogramming.ezlang.bytecode;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.Type;

import java.util.ArrayList;
import java.util.List;

public class FunctionBuilder {

    public BasicBlock entry;
    public BasicBlock exit;
    private int bid = 0;
    private BasicBlock currentBlock;
    private BasicBlock currentBreakTarget;
    private BasicBlock currentContinueTarget;

    /**
     * We essentially do a form of abstract interpretation as we generate
     * the bytecode instructions. For this purpose we use a virtual operand stack.
     *
     * This is similar to the technique described in
     * Dynamic Optimization through the use of Automatic Runtime Specialization
     * by John Whaley
     */
    private List<Operand> virtualStack = new ArrayList<>();

    public FunctionBuilder(Symbol.FunctionTypeSymbol functionSymbol) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        setVirtualRegisters(funcDecl.scope);
        this.bid = 0;
        this.entry = this.currentBlock = createBlock();
        this.exit = createBlock();
        this.currentBreakTarget = null;
        this.currentContinueTarget = null;
        compileStatement(funcDecl.block);
        exitBlockIfNeeded();
    }

    private void exitBlockIfNeeded() {
        if (currentBlock != null &&
                currentBlock != exit) {
            startBlock(exit);
        }
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
            compileExpr(returnStmt.expr);
            if (virtualStack.size() == 1)
                code(new Instruction.Move(pop(), new Operand.ReturnRegisterOperand()));
            else if (virtualStack.size() > 1)
                throw new CompilerException("Virtual stack has more than one item at return");
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
            codeIndexedLoad();
        if (indexedLhs)
            codeIndexedStore();
        else if (assignStmt.lhs instanceof AST.NameExpr symbolExpr) {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            code(new Instruction.Move(pop(), new Operand.LocalRegisterOperand(varSymbol.reg, varSymbol.name)));
        }
        else
            throw new CompilerException("Invalid assignment expression: " + assignStmt.lhs);
    }

    private void compileExprStmt(AST.ExprStmt exprStmt) {
        boolean indexed = compileExpr(exprStmt.expr);
        if (indexed)
            codeIndexedLoad();
        if (!vstackEmpty())
            pop();
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
            codeIndexedLoad();
        code(new Instruction.ConditionalBranch(currentBlock, pop(), bodyBlock, exitBlock));
        assert vstackEmpty();
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
        assert !isBlockTerminated(currentBlock);
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
            codeIndexedLoad();
        code(new Instruction.ConditionalBranch(currentBlock, pop(), ifBlock, needElse ? elseBlock : exitBlock));
        assert vstackEmpty();
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
                codeIndexedLoad();
            code(new Instruction.Move(pop(), new Operand.LocalRegisterOperand(letStmt.symbol.reg, letStmt.symbol.name)));
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
        var callee = top();
        if (!(callee instanceof Operand.TempRegisterOperand) ) {
            var origCallee = pop();
            callee = createTemp(origCallee.type);
            code(new Instruction.Move(origCallee, callee));
        }
        List<Operand> args = new ArrayList<>();
        for (AST.Expr expr: callExpr.args) {
            boolean indexed = compileExpr(expr);
            if (indexed)
                codeIndexedLoad();
            var arg = top();
            if (!(arg instanceof Operand.TempRegisterOperand) ) {
                var origArg = pop();
                arg = createTemp(origArg.type);
                code(new Instruction.Move(origArg, arg));
            }
            args.add(arg);
        }
        code(new Instruction.Call(callee, args.toArray(new Operand[args.size()])));
        // Similute the actions on the stack
        for (int i = 0; i < args.size()+1; i++)
            pop();
        if (callExpr.callee.type instanceof Type.TypeFunction tf &&
                !(tf.returnType instanceof Type.TypeVoid))
            createTemp(tf.returnType);
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
            codeIndexedLoad();
        pushOperand(new Operand.LoadFieldOperand(pop(), fieldExpr.fieldName, fieldIndex));
        return true;
    }

    private boolean compileArrayIndexExpr(AST.ArrayIndexExpr arrayIndexExpr) {
        compileExpr(arrayIndexExpr.array);
        boolean indexed = compileExpr(arrayIndexExpr.expr);
        if (indexed)
            codeIndexedLoad();
        Operand index = pop();
        Operand array = pop();
        pushOperand(new Operand.LoadIndexedOperand(array, index));
        return true;
    }

    private boolean compileSetFieldExpr(AST.SetFieldExpr setFieldExpr) {
        Type.TypeStruct structType = (Type.TypeStruct) setFieldExpr.objectType;
        int fieldIndex = structType.getFieldIndex(setFieldExpr.fieldName);
        if (fieldIndex == -1)
            throw new CompilerException("Field " + setFieldExpr.fieldName + " not found in struct " + structType.name);
        pushOperand(new Operand.LoadFieldOperand(top(), setFieldExpr.fieldName, fieldIndex));
        boolean indexed = compileExpr(setFieldExpr.value);
        if (indexed)
            codeIndexedLoad();
        codeIndexedStore();
        return false;
    }

    private void codeNew(Type type) {
        var temp = createTemp(type);
        code(new Instruction.Move(new Operand.NewTypeOperand(type), temp));
    }

    private void codeStoreAppend() {
        var operand = pop();
        code(new Instruction.AStoreAppend(top(), operand));
    }

    private boolean compileNewExpr(AST.NewExpr newExpr) {
        codeNew(newExpr.type);
        if (newExpr.initExprList != null && !newExpr.initExprList.isEmpty()) {
            if (newExpr.type instanceof Type.TypeArray) {
                for (AST.Expr expr : newExpr.initExprList) {
                    // Maybe have specific AST similar to how we have SetFieldExpr?
                    boolean indexed = compileExpr(expr);
                    if (indexed)
                        codeIndexedLoad();
                    codeStoreAppend();
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
            pushOperand(new Operand.LocalFunctionOperand(functionType));
        else {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            pushLocal(varSymbol.reg, varSymbol.name);
        }
        return false;
    }

    private boolean compileBinaryExpr(AST.BinaryExpr binaryExpr) {
        String opCode = null;
        boolean indexed = compileExpr(binaryExpr.expr1);
        if (indexed)
            codeIndexedLoad();
        indexed = compileExpr(binaryExpr.expr2);
        if (indexed)
            codeIndexedLoad();
        opCode = binaryExpr.op.str;
        Operand right = pop();
        Operand left = pop();
        if (left instanceof Operand.ConstantOperand leftconstant &&
                right instanceof Operand.ConstantOperand rightconstant) {
            long value = 0;
            switch (opCode) {
                case "+": value = leftconstant.value + rightconstant.value; break;
                case "-": value = leftconstant.value - rightconstant.value; break;
                case "*": value = leftconstant.value * rightconstant.value; break;
                case "/": value = leftconstant.value / rightconstant.value; break;
                case "%": value = leftconstant.value % rightconstant.value; break;
                case "==": value = leftconstant.value == rightconstant.value ? 1 : 0; break;
                case "!=": value = leftconstant.value != rightconstant.value ? 1 : 0; break;
                case "<": value = leftconstant.value < rightconstant.value ? 1: 0; break;
                case ">": value = leftconstant.value > rightconstant.value ? 1 : 0; break;
                case "<=": value = leftconstant.value <= rightconstant.value ? 1 : 0; break;
                case ">=": value = leftconstant.value <= rightconstant.value ? 1 : 0; break;
                default: throw new CompilerException("Invalid binary op");
            }
            pushConstant(value, leftconstant.type);
        }
        else {
            var temp = createTemp(binaryExpr.type);
            code(new Instruction.BinaryInstruction(opCode, temp, left, right));
        }
        return false;
    }

    private boolean compileUnaryExpr(AST.UnaryExpr unaryExpr) {
        String opCode;
        boolean indexed = compileExpr(unaryExpr.expr);
        if (indexed)
            codeIndexedLoad();
        opCode = unaryExpr.op.str;
        Operand top = pop();
        if (top instanceof Operand.ConstantOperand constant) {
            switch (opCode) {
                case "-": pushConstant(-constant.value, constant.type); break;
                // Maybe below we should explicitly set Int
                case "!": pushConstant(constant.value == 0?1:0, constant.type); break;
                default: throw new CompilerException("Invalid unary op");
            }
        }
        else {
            var temp = createTemp(unaryExpr.type);
            code(new Instruction.UnaryInstruction(opCode, temp, top));
        }
        return false;
    }

    private boolean compileConstantExpr(AST.LiteralExpr constantExpr) {
        pushConstant(constantExpr.value.num.intValue(), constantExpr.type);
        return false;
    }

    private void pushConstant(long value, Type type) {
        virtualStack.add(new Operand.ConstantOperand(value, type));
    }

    private Operand.TempRegisterOperand createTemp(Type type) {
        var tempRegister = new Operand.TempRegisterOperand(virtualStack.size(), type);
        virtualStack.add(tempRegister);
        return tempRegister;
    }

    private void pushLocal(int regnum, String varName) {
        virtualStack.add(new Operand.LocalRegisterOperand(regnum, varName));
    }

    private void pushOperand(Operand operand) {
        virtualStack.add(operand);
    }

    private Operand pop() {
        return virtualStack.removeLast();
    }

    private Operand top() {
        return virtualStack.getLast();
    }

    private void codeIndexedLoad() {
        Operand indexed = pop();
        var temp = createTemp(indexed.type);
        code(new Instruction.Move(indexed, temp));
    }

    private void codeIndexedStore() {
        Operand value = pop();
        Operand indexed = pop();
        code(new Instruction.Move(value, indexed));
    }

    private boolean vstackEmpty() {
        return virtualStack.isEmpty();
    }
}
