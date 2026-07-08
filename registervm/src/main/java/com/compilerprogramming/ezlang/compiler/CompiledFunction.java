package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.ArrayList;
import java.util.List;

public class CompiledFunction {

    public BasicBlock entry;
    public BasicBlock exit;
    private int BID = 0;
    public BasicBlock currentBlock;
    private BasicBlock currentBreakTarget;
    private BasicBlock currentContinueTarget;
    public int maxLocalReg;
    public int maxStackSize;
    private final TypeDictionary typeDictionary;

    /**
     * We essentially do a form of abstract interpretation as we generate
     * the bytecode instructions. For this purpose we use a virtual operand stack.
     *
     * This is similar to the technique described in
     * Dynamic Optimization through the use of Automatic Runtime Specialization
     * by John Whaley
     */
    private List<Operand> virtualStack = new ArrayList<>();

    public CompiledFunction(Symbol.FunctionTypeSymbol functionSymbol, TypeDictionary typeDictionary) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        setVirtualRegisters(funcDecl.scope);
        this.BID = 0;
        this.entry = this.currentBlock = createBlock();
        this.exit = createBlock();
        this.currentBreakTarget = null;
        this.currentContinueTarget = null;
        this.typeDictionary = typeDictionary;
        compileStatement(funcDecl.block);
        exitBlockIfNeeded();
    }

    public int frameSize() {
        return maxLocalReg+maxStackSize;
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
                varSymbol.regNumber = reg++;
            }
        }
        scope.maxReg = reg;
        if (maxLocalReg < scope.maxReg)
            maxLocalReg = scope.maxReg;
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    private BasicBlock createBlock() {
        return new BasicBlock(BID++);
    }

    private BasicBlock createLoopHead() {
        return new BasicBlock(BID++, true);
    }

    private void compileBlock(AST.BlockStmt block) {
        for (AST.Stmt stmt: block.stmtList) {
            compileStatement(stmt);
        }
    }

    private void compileReturn(AST.ReturnStmt returnStmt) {
        if (returnStmt.expr != null) {
            boolean isIndexed = compileExpr(returnStmt.expr);
            if (isIndexed)
                codeIndexedLoad();
            if (virtualStack.size() == 1)
                code(new Instruction.Ret(pop()));
            else if (virtualStack.size() > 1)
                throw new CompilerException("Virtual stack has more than one item at return", returnStmt.lineNumber);
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
            case AST.VarDeclStmt varDeclStmt -> {}
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
        boolean indexedRhs = compileExpr(assignStmt.rhs);
        if (indexedRhs)
            codeIndexedLoad();
        Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) assignStmt.nameExpr.symbol;
        code(new Instruction.Move(pop(), new Operand.LocalRegisterOperand(varSymbol.regNumber, varSymbol.name)));
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
            throw new CompilerException("No continue target found", continueStmt.lineNumber);
        jumpTo(currentContinueTarget);
    }

    private void compileBreak(AST.BreakStmt breakStmt) {
        if (currentBreakTarget == null)
            throw new CompilerException("No break target found", breakStmt.lineNumber);
        jumpTo(currentBreakTarget);
    }

    private void compileWhile(AST.WhileStmt whileStmt) {
        BasicBlock loopHead = createLoopHead();
        BasicBlock bodyBlock = createBlock();
        BasicBlock exitBlock = createBlock();
        BasicBlock savedBreakTarget = currentBreakTarget;
        BasicBlock savedContinueTarget = currentContinueTarget;
        currentBreakTarget = exitBlock;
        currentContinueTarget = loopHead;
        startBlock(loopHead);
        checkConditionType(whileStmt.condition.type, whileStmt.lineNumber);
        boolean indexed = compileExpr(whileStmt.condition);
        if (indexed)
            codeIndexedLoad();
        code(new Instruction.ConditionalBranch(currentBlock, pop(), bodyBlock, exitBlock));
        assert vstackEmpty();
        startBlock(bodyBlock);
        compileStatement(whileStmt.stmt);
        if (!isBlockTerminated(currentBlock))
            jumpTo(loopHead);
        startBlock(exitBlock);
        currentContinueTarget = savedContinueTarget;
        currentBreakTarget = savedBreakTarget;
    }

    private void checkConditionType(EZType conditionType, int lineNumber) {
        if (!(conditionType instanceof EZType.EZTypeInteger))
            throw new CompilerException("Condition expression must be Int type", lineNumber);
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
        BasicBlock thenBlock = createBlock();
        boolean needElse = ifElseStmt.elseStmt != null;
        BasicBlock elseBlock = needElse ? createBlock() : null;
        BasicBlock exitBlock = createBlock();
        checkConditionType(ifElseStmt.condition.type, ifElseStmt.lineNumber);
        boolean indexed = compileExpr(ifElseStmt.condition);
        if (indexed)
            codeIndexedLoad();
        code(new Instruction.ConditionalBranch(currentBlock, pop(), thenBlock, needElse ? elseBlock : exitBlock));
        assert vstackEmpty();
        startBlock(thenBlock);
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
            code(new Instruction.Move(pop(), new Operand.LocalRegisterOperand(letStmt.symbol.regNumber, letStmt.symbol.name)));
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
            case AST.InitExpr initExpr -> {
                return compileInitExpr(initExpr);
            }
            case AST.ArrayLoadExpr arrayLoadExpr -> {
                return compileArrayIndexExpr(arrayLoadExpr);
            }
            case AST.ArrayStoreExpr arrayStoreExpr -> {
                return compileArrayStoreExpr(arrayStoreExpr);
            }
            case AST.GetFieldExpr getFieldExpr -> {
                return compileFieldExpr(getFieldExpr);
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
        var callee = pop();
        EZType.EZTypeFunction calleeType = null;
        if (callee instanceof Operand.LocalFunctionOperand functionOperand)
            calleeType = functionOperand.functionType;
        else throw new CompilerException("Cannot call a non function type", callExpr.lineNumber);
        var returnStackPos = virtualStack.size();
        List<Operand.RegisterOperand> args = new ArrayList<>();
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
            args.add((Operand.RegisterOperand) arg);
        }
        // Simulate the actions on the stack
        for (int i = 0; i < args.size(); i++)
            pop();
        Operand.TempRegisterOperand ret = null;
        if (callExpr.callee.type instanceof EZType.EZTypeFunction tf &&
                !(tf.returnType instanceof EZType.EZTypeVoid)) {
            ret = createTemp(tf.returnType);
            assert ret.regnum-maxLocalReg == returnStackPos;
        }
        code(new Instruction.Call(returnStackPos, ret, calleeType, args.toArray(new Operand.RegisterOperand[args.size()])));
        return false;
    }

    private EZType.EZTypeStruct getStructType(EZType t, int lineNumber) {
        if (t instanceof EZType.EZTypeStruct typeStruct) {
            return typeStruct;
        }
        else if (t instanceof EZType.EZTypeNullable ptr &&
                ptr.baseType instanceof EZType.EZTypeStruct typeStruct) {
            return typeStruct;
        }
        else
            throw new CompilerException("Unexpected type: " + t, lineNumber);
    }

    private boolean compileFieldExpr(AST.GetFieldExpr fieldExpr) {
        EZType.EZTypeStruct typeStruct = getStructType(fieldExpr.object.type, fieldExpr.lineNumber);
        int fieldIndex = typeStruct.getFieldIndex(fieldExpr.fieldName);
        if (fieldIndex < 0)
            throw new CompilerException("Field " + fieldExpr.fieldName + " not found", fieldExpr.lineNumber);
        boolean indexed = compileExpr(fieldExpr.object);
        if (indexed)
            codeIndexedLoad();
        pushOperand(new Operand.LoadFieldOperand(pop(), fieldExpr.fieldName, fieldIndex));
        return true;
    }

    private boolean compileArrayIndexExpr(AST.ArrayLoadExpr arrayIndexExpr) {
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
        EZType.EZTypeStruct structType = (EZType.EZTypeStruct) setFieldExpr.object.type;
        int fieldIndex = structType.getFieldIndex(setFieldExpr.fieldName);
        if (fieldIndex == -1)
            throw new CompilerException("Field " + setFieldExpr.fieldName + " not found in struct " + structType.name, setFieldExpr.lineNumber);
        if (setFieldExpr instanceof AST.InitFieldExpr)
            pushOperand(top());
        else
            compileExpr(setFieldExpr.object);
        pushOperand(new Operand.LoadFieldOperand(pop(), setFieldExpr.fieldName, fieldIndex));
        boolean indexed = compileExpr(setFieldExpr.value);
        if (indexed)
            codeIndexedLoad();
        codeIndexedStore();
        return false;
    }

    private boolean compileArrayStoreExpr(AST.ArrayStoreExpr arrayStoreExpr) {
        if (arrayStoreExpr instanceof AST.ArrayInitExpr)
            pushOperand(top()); // Array was created by new
        else
            compileExpr(arrayStoreExpr.array);
        boolean indexed = compileExpr(arrayStoreExpr.expr);
        if (indexed)
            codeIndexedLoad();
        Operand index = pop();
        Operand array = pop();
        pushOperand(new Operand.LoadIndexedOperand(array, index));
        indexed = compileExpr(arrayStoreExpr.value);
        if (indexed)
            codeIndexedLoad();
        codeIndexedStore();
        return false;
    }

    private boolean compileNewExpr(AST.NewExpr newExpr) {
        codeNew(newExpr.type,newExpr.len,newExpr.initValue);
        return false;
    }

    private boolean compileInitExpr(AST.InitExpr initExpr) {
        compileExpr(initExpr.newExpr);
        if (initExpr.initExprList != null && !initExpr.initExprList.isEmpty()) {
            for (AST.Expr expr : initExpr.initExprList) {
                compileExpr(expr);
            }
        }
        return false;
    }

    private boolean compileSymbolExpr(AST.NameExpr symbolExpr) {
        if (symbolExpr.type instanceof EZType.EZTypeFunction functionType)
            pushOperand(new Operand.LocalFunctionOperand(functionType));
        else {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            pushLocal(varSymbol.regNumber, varSymbol.name);
        }
        return false;
    }

    private boolean codeBoolean(AST.BinaryExpr binaryExpr) {
        checkConditionType(binaryExpr.expr1.type, binaryExpr.lineNumber);
        checkConditionType(binaryExpr.expr2.type, binaryExpr.lineNumber);
        boolean isAnd = binaryExpr.op.str.equals("&&");
        BasicBlock l1 = createBlock();
        BasicBlock l2 = createBlock();
        BasicBlock l3 = createBlock();
        boolean indexed = compileExpr(binaryExpr.expr1);
        if (indexed)
            codeIndexedLoad();
        if (isAnd) {
            code(new Instruction.ConditionalBranch(currentBlock, pop(), l1, l2));
        } else {
            code(new Instruction.ConditionalBranch(currentBlock, pop(), l2, l1));
        }
        startBlock(l1);
        compileExpr(binaryExpr.expr2);
        var temp = ensureTemp();
        jumpTo(l3);
        startBlock(l2);
        // Below we must write to the same temp
        code(new Instruction.Move(new Operand.IntConstantOperand(isAnd ? 0 : 1, typeDictionary.INT), temp));
        jumpTo(l3);
        startBlock(l3);
        // leave temp on virtual stack
        return false;
    }

    private boolean compileBinaryExpr(AST.BinaryExpr binaryExpr) {
        String opCode = binaryExpr.op.str;
        if (opCode.equals("&&") ||
            opCode.equals("||")) {
            return codeBoolean(binaryExpr);
        }
        boolean indexed = compileExpr(binaryExpr.expr1);
        if (indexed)
            codeIndexedLoad();
        indexed = compileExpr(binaryExpr.expr2);
        if (indexed)
            codeIndexedLoad();
        Operand right = pop();
        Operand left = pop();
        if (left instanceof Operand.NullConstantOperand &&
            right instanceof Operand.NullConstantOperand) {
            long value = 0;
            switch (opCode) {
                case "==": value = 1; break;
                case "!=": value = 0; break;
                default: throw new CompilerException("Invalid binary op", binaryExpr.lineNumber);
            }
            pushIntConstant(value, typeDictionary.INT);
        }
        else if (left instanceof Operand.IntConstantOperand leftconstant &&
                right instanceof Operand.IntConstantOperand rightconstant) {
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
                case ">=": value = leftconstant.value >= rightconstant.value ? 1 : 0; break;
                default: throw new CompilerException("Invalid binary op", binaryExpr.lineNumber);
            }
            pushIntConstant(value, binaryExpr.type);
        }
        else if (left instanceof Operand.FloatConstantOperand leftconstant &&
                 right instanceof Operand.FloatConstantOperand rightconstant) {
            switch (opCode) {
                case "+" -> pushFloatConstant(leftconstant.value + rightconstant.value, binaryExpr.type);
                case "-" -> pushFloatConstant(leftconstant.value - rightconstant.value, binaryExpr.type);
                case "*" -> pushFloatConstant(leftconstant.value * rightconstant.value, binaryExpr.type);
                case "/" -> pushFloatConstant(leftconstant.value / rightconstant.value, binaryExpr.type);
                case "==" -> pushIntConstant(leftconstant.value == rightconstant.value ? 1 : 0, binaryExpr.type);
                case "!=" -> pushIntConstant(leftconstant.value != rightconstant.value ? 1 : 0, binaryExpr.type);
                case "<" -> pushIntConstant(leftconstant.value < rightconstant.value ? 1 : 0, binaryExpr.type);
                case ">" -> pushIntConstant(leftconstant.value > rightconstant.value ? 1 : 0, binaryExpr.type);
                case "<=" -> pushIntConstant(leftconstant.value <= rightconstant.value ? 1 : 0, binaryExpr.type);
                case ">=" -> pushIntConstant(leftconstant.value >= rightconstant.value ? 1 : 0, binaryExpr.type);
                default -> throw new CompilerException("Invalid binary op", binaryExpr.lineNumber);
            }
        }
        else {
            var temp = createTemp(binaryExpr.type);
            code(new Instruction.Binary(opCode, temp, left, right));
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
        if (top instanceof Operand.IntConstantOperand constant) {
            switch (opCode) {
                case "-": pushIntConstant(-constant.value, constant.type); break;
                // Maybe below we should explicitly set Int
                case "!": pushIntConstant(constant.value == 0?1:0, typeDictionary.INT); break;
                default: throw new CompilerException("Invalid unary op", unaryExpr.lineNumber);
            }
        }
        else if (top instanceof Operand.FloatConstantOperand constant) {
            switch (opCode) {
                case "-": pushFloatConstant(-constant.value, constant.type); break;
                default: throw new CompilerException("Invalid unary op", unaryExpr.lineNumber);
            }
        }
        else {
            var temp = createTemp(unaryExpr.type);
            code(new Instruction.Unary(opCode, temp, top));
        }
        return false;
    }

    private boolean compileConstantExpr(AST.LiteralExpr constantExpr) {
        if (constantExpr.type instanceof EZType.EZTypeInteger)
            pushIntConstant(constantExpr.value.num.intValue(), constantExpr.type);
        else if (constantExpr.type instanceof EZType.EZTypeFloat)
            pushFloatConstant(constantExpr.value.num.doubleValue(), constantExpr.type);
        else if (constantExpr.type instanceof EZType.EZTypeNull)
            pushNullConstant(constantExpr.type);
        else throw new CompilerException("Invalid constant type", constantExpr.lineNumber);
        return false;
    }

    private void pushIntConstant(long value, EZType type) {
        pushOperand(new Operand.IntConstantOperand(value, type));
    }

    private void pushFloatConstant(double value, EZType type) {
        pushOperand(new Operand.FloatConstantOperand(value, type));
    }

    private void pushNullConstant(EZType type) {
        pushOperand(new Operand.NullConstantOperand(type));
    }

    private Operand.TempRegisterOperand createTemp(EZType type) {
        var tempRegister = new Operand.TempRegisterOperand(virtualStack.size()+maxLocalReg, type);
        pushOperand(tempRegister);
        if (maxStackSize < virtualStack.size())
            maxStackSize = virtualStack.size();
        return tempRegister;
    }

    EZType typeOfOperand(Operand operand) {
        if (operand instanceof Operand.IntConstantOperand constant)
            return constant.type;
        else if (operand instanceof Operand.FloatConstantOperand constant)
            return constant.type;
        else if (operand instanceof Operand.NullConstantOperand nullConstantOperand)
            return nullConstantOperand.type;
        else if (operand instanceof Operand.RegisterOperand registerOperand)
            return registerOperand.type;
        else throw new CompilerException("Invalid operand");
    }

    private Operand.TempRegisterOperand createTempAndMove(Operand src) {
        EZType type = typeOfOperand(src);
        var temp = createTemp(type);
        code(new Instruction.Move(src, temp));
        return temp;
    }

    private Operand.RegisterOperand ensureTemp() {
        Operand top = top();
        if (top instanceof Operand.IntConstantOperand
                || top instanceof Operand.FloatConstantOperand
                || top instanceof Operand.NullConstantOperand
                || top instanceof Operand.LocalRegisterOperand) {
            return createTempAndMove(pop());
        } else if (top instanceof Operand.IndexedOperand) {
            return codeIndexedLoad();
        } else if (top instanceof Operand.TempRegisterOperand tempRegisterOperand) {
            return tempRegisterOperand;
        } else throw new CompilerException("Cannot convert to temporary register");
    }

    private void pushLocal(int regnum, String varName) {
        pushOperand(new Operand.LocalRegisterOperand(regnum, varName));
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

    private boolean vstackEmpty() {
        return virtualStack.isEmpty();
    }

    private Operand.TempRegisterOperand codeIndexedLoad() {
        Operand indexed = pop();
        var temp = createTemp(indexed.type);
        if (indexed instanceof Operand.LoadIndexedOperand loadIndexedOperand)
            code(new Instruction.ArrayLoad(loadIndexedOperand, temp));
        else if (indexed instanceof Operand.LoadFieldOperand loadFieldOperand)
            code(new Instruction.GetField(loadFieldOperand, temp));
        else
            code(new Instruction.Move(indexed, temp));
        return temp;
    }

    private void codeIndexedStore() {
        Operand value = pop();
        Operand indexed = pop();
        if (indexed instanceof Operand.LoadIndexedOperand loadIndexedOperand)
            code(new Instruction.ArrayStore(value, loadIndexedOperand));
        else if (indexed instanceof Operand.LoadFieldOperand loadFieldOperand)
            code(new Instruction.SetField(value, loadFieldOperand));
        else
            code(new Instruction.Move(value, indexed));
    }

    private void codeNew(EZType type, AST.Expr len, AST.Expr initVal) {
        if (type instanceof EZType.EZTypeArray typeArray)
            codeNewArray(typeArray, len, initVal);
        else if (type instanceof EZType.EZTypeStruct typeStruct) {
            var temp = createTemp(type);
            code(new Instruction.NewStruct(typeStruct, temp));
        }
        else
            throw new CompilerException("Unexpected type: " + type, len.lineNumber);
    }

    private void codeNewArray(EZType.EZTypeArray typeArray, AST.Expr len, AST.Expr initVal) {
        var temp = createTemp(typeArray);
        Operand lenOperand = null;
        Operand initValOperand = null;
        if (len != null) {
            boolean indexed = compileExpr(len);
            if (indexed)
                codeIndexedLoad();
            if (initVal != null) {
                indexed = compileExpr(initVal);
                if (indexed)
                    codeIndexedLoad();
                initValOperand = pop();
            }
            lenOperand = pop();
        }
        Instruction insn;
        if (lenOperand != null) {
            if (initValOperand != null)
                insn = new Instruction.NewArray(typeArray, temp, lenOperand, initValOperand);
            else
                insn = new Instruction.NewArray(typeArray, temp, lenOperand);
        }
        else
            insn = new Instruction.NewArray(typeArray, temp);
        code(insn);
    }
}
