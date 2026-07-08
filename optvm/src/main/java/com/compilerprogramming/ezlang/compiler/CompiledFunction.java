package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.*;

public class CompiledFunction {

    public BasicBlock entry;
    public BasicBlock exit;
    private int BID = 0;
    public BasicBlock currentBlock;
    private BasicBlock currentBreakTarget;
    private BasicBlock currentContinueTarget;
    public EZType.EZTypeFunction functionType;
    public final RegisterPool registerPool;
    private final TypeDictionary typeDictionary;

    private int frameSlots;

    public boolean isSSA;
    public boolean hasLiveness;
    private final IncrementalSSA issa;

    private StringBuilder dumpTarget;

    /**
     * We essentially do a form of abstract interpretation as we generate
     * the bytecode instructions. For this purpose we use a virtual operand stack.
     *
     * This is similar to the technique described in
     * Dynamic Optimization through the use of Automatic Runtime Specialization
     * by John Whaley
     */
    private List<Operand> virtualStack = new ArrayList<>();

    public CompiledFunction(Symbol.FunctionTypeSymbol functionSymbol, TypeDictionary typeDictionary, EnumSet<Options> options) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        this.functionType = (EZType.EZTypeFunction) functionSymbol.type;
        this.registerPool = new RegisterPool();
        // Incremental SSA is an optional feature
        this.issa = (options != null && options.contains(Options.ISSA)) ? new IncrementalSSABraun(this) : new NoopIncrementalSSA();
        setVirtualRegisters(funcDecl.scope);
        this.BID = 0;
        this.entry = this.currentBlock = createBlock();
        this.exit = createBlock();
        this.currentBreakTarget = null;
        this.currentContinueTarget = null;
        this.typeDictionary = typeDictionary;
        issa.sealBlock(entry);          // Incremental SSA is an optional feature
        generateArgInstructions(funcDecl.scope);
        compileStatement(funcDecl.block);
        exitBlockIfNeeded();
        issa.finish(options);
        this.frameSlots = registerPool.numRegisters();
    }
    public CompiledFunction(Symbol.FunctionTypeSymbol functionSymbol, TypeDictionary typeDictionary) {
        this(functionSymbol,typeDictionary,null);
    }
    public CompiledFunction(EZType.EZTypeFunction functionType, TypeDictionary typeDictionary) {
        this.functionType = (EZType.EZTypeFunction) functionType;
        this.registerPool = new RegisterPool();
        this.issa = new NoopIncrementalSSA();        this.BID = 0;
        this.entry = this.currentBlock = createBlock();
        this.exit = createBlock();
        this.currentBreakTarget = null;
        this.currentContinueTarget = null;
        this.typeDictionary = typeDictionary;
        issa.finish(null);
        this.frameSlots = registerPool.numRegisters();
    }
    public void setDumpTarget(StringBuilder dumpTarget) {
        this.dumpTarget = dumpTarget;
    }
    private void generateArgInstructions(Scope scope) {
        if (scope.isFunctionParameterScope) {
            for (Symbol symbol: scope.getLocalSymbols()) {
                if (symbol instanceof Symbol.ParameterSymbol parameterSymbol) {
                    codeArg(new Operand.LocalRegisterOperand(registerPool.getReg(parameterSymbol.regNumber), parameterSymbol));
                }
            }
        }
    }

    public int frameSize() {
        return frameSlots;
    }
    public void setFrameSize(int size) {
        frameSlots = size;
    }

    private void exitBlockIfNeeded() {
        if (currentBlock != null &&
                currentBlock != exit) {
            startBlock(exit);
        }
    }

    private void setVirtualRegisters(Scope scope) {
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.regNumber = registerPool.newReg(varSymbol.name, varSymbol.type).nonSSAId();
            }
        }
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    public BasicBlock createBlock() {
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
                codeReturn(pop());
            else if (virtualStack.size() > 1)
                throw new CompilerException("Virtual stack has more than one item at return", returnStmt.lineNumber);
        }
        jumpTo(exit);
    }

    public void code(Instruction instruction) {
        currentBlock.add(instruction);
        assert instruction.block == currentBlock;
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
        codeMove(pop(), new Operand.LocalRegisterOperand(registerPool.getReg(varSymbol.regNumber), varSymbol));
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
        assert !issa.isSealed(currentContinueTarget);
        jumpTo(currentContinueTarget);
    }

    private void compileBreak(AST.BreakStmt breakStmt) {
        if (currentBreakTarget == null)
            throw new CompilerException("No break target found", breakStmt.lineNumber);
        assert !issa.isSealed(currentBreakTarget);
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
        startBlock(loopHead);   // ISSA cannot seal until all back edges done
        checkConditionType(whileStmt.condition.type, whileStmt.lineNumber);
        boolean indexed = compileExpr(whileStmt.condition);
        if (indexed)
            codeIndexedLoad();
        codeCBR(currentBlock, pop(), bodyBlock, exitBlock);
        assert vstackEmpty();
        startSealedBlock(bodyBlock);  // ISSA Body is immediately sealed as no new predecessors possible
        compileStatement(whileStmt.stmt);
        if (!isBlockTerminated(currentBlock))
            jumpTo(loopHead);
        issa.sealBlock(loopHead);
        startSealedBlock(exitBlock);    // ISSA seal exit block (breaks already done)
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

    public void jumpTo(BasicBlock block) {
        assert !isBlockTerminated(currentBlock);
        assert !issa.isSealed(block);
        currentBlock.add(new Instruction.Jump(block));
        currentBlock.addSuccessor(block);
    }

    public void startBlock(BasicBlock block) {
        if (!isBlockTerminated(currentBlock)) {
            jumpTo(block);
        }
        currentBlock = block;
    }
    public void startSealedBlock(BasicBlock block) {
        startBlock(block);
        assert !issa.isSealed(currentBlock);
        issa.sealBlock(currentBlock);
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
        codeCBR(currentBlock, pop(), thenBlock, needElse ? elseBlock : exitBlock);
        assert vstackEmpty();
        startSealedBlock(thenBlock);        // ISSA seal immediately
        compileStatement(ifElseStmt.ifStmt);
        if (!isBlockTerminated(currentBlock))
            jumpTo(exitBlock);
        if (elseBlock != null) {
            startSealedBlock(elseBlock);    // ISSA seal immediately
            compileStatement(ifElseStmt.elseStmt);
            if (!isBlockTerminated(currentBlock))
                jumpTo(exitBlock);
        }
        startSealedBlock(exitBlock);        // ISSA seal immediately
    }

    private void compileLet(AST.VarStmt letStmt) {
        if (letStmt.expr != null) {
            boolean indexed = compileExpr(letStmt.expr);
            if (indexed)
                codeIndexedLoad();
            codeMove(pop(), new Operand.LocalRegisterOperand(registerPool.getReg(letStmt.symbol.regNumber), letStmt.symbol));
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
                codeMove(origArg, arg);
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
        }
        codeCall(returnStackPos, ret, calleeType, args.toArray(new Operand.RegisterOperand[args.size()]));
        return false;
    }

    private EZType.EZTypeStruct getStructType(EZType t) {
        if (t instanceof EZType.EZTypeStruct typeStruct) {
            return typeStruct;
        }
        else if (t instanceof EZType.EZTypeNullable ptr &&
                ptr.baseType instanceof EZType.EZTypeStruct typeStruct) {
            return typeStruct;
        }
        else
            throw new CompilerException("Unexpected type: " + t);
    }

    private boolean compileFieldExpr(AST.GetFieldExpr fieldExpr) {
        EZType.EZTypeStruct typeStruct = getStructType(fieldExpr.object.type);
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
            pushLocal(registerPool.getReg(varSymbol.regNumber), varSymbol);
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
            codeCBR(currentBlock, pop(), l1, l2);
        } else {
            codeCBR(currentBlock, pop(), l2, l1);
        }
        startSealedBlock(l1);       // ISSA seal immediately
        compileExpr(binaryExpr.expr2);
        var temp = ensureTemp();    // Normally temps are SSA but this temp gets two assignments, thus must be included during SSA conversion
        jumpTo(l3);
        startSealedBlock(l2);       // ISSA seal immediately
        // Below we must write to the same temp
        codeMove(new Operand.IntConstantOperand(isAnd ? 0 : 1, typeDictionary.INT), temp);
        jumpTo(l3);
        startSealedBlock(l3);       // ISSA seal immediately
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
            codeBinary(opCode, temp, left, right);
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
            codeUnary(opCode, temp, top);
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
        var tempRegister = new Operand.TempRegisterOperand(registerPool.newTempReg(type));
        pushOperand(tempRegister);
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
        codeMove(src, temp);
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

    private void pushLocal(Register reg, Symbol.VarSymbol varSymbol) {
        pushOperand(new Operand.LocalRegisterOperand(reg, varSymbol));
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
            codeArrayLoad(loadIndexedOperand, temp);
        else if (indexed instanceof Operand.LoadFieldOperand loadFieldOperand)
            codeGetField(loadFieldOperand, temp);
        else
            codeMove(indexed, temp);
        return temp;
    }

    private void codeIndexedStore() {
        Operand value = pop();
        Operand indexed = pop();
        if (indexed instanceof Operand.LoadIndexedOperand loadIndexedOperand)
            codeArrayStore(value, loadIndexedOperand);
        else if (indexed instanceof Operand.LoadFieldOperand loadFieldOperand)
            codeSetField(value, loadFieldOperand);
        else
            codeMove(value, indexed);
    }

    private void codeNew(EZType type, AST.Expr len, AST.Expr initVal) {
        if (type instanceof EZType.EZTypeArray typeArray)
            codeNewArray(typeArray, len, initVal);
        else if (type instanceof EZType.EZTypeStruct typeStruct)
            codeNewStruct(typeStruct);
        else
            throw new CompilerException("Unexpected type: " + type);
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
        var target = (Operand.RegisterOperand) issa.write(temp);
        if (lenOperand != null) {
            if (initValOperand != null)
                insn = new Instruction.NewArray(typeArray, target, issa.read(lenOperand), issa.read(initValOperand));
            else
                insn = new Instruction.NewArray(typeArray, target, issa.read(lenOperand));
        }
        else
            insn = new Instruction.NewArray(typeArray, target);
        issa.recordDef(target, insn);
        if (lenOperand != null) issa.recordUse(lenOperand,insn);
        if (initValOperand != null) issa.recordUse(initValOperand,insn);
        code(insn);
    }

    private void codeNewStruct(EZType.EZTypeStruct typeStruct) {
        var temp = createTemp(typeStruct);
        var target = (Operand.RegisterOperand) issa.write(temp);
        var insn = new Instruction.NewStruct(typeStruct, target);
        issa.recordDef(target, insn);
        code(insn);
    }

    private void codeArg(Operand.LocalRegisterOperand target) {
        var newtarget = (Operand.RegisterOperand) issa.write(target);
        var insn = new Instruction.ArgInstruction(newtarget);
        issa.recordDef(newtarget, insn);
        code(insn);
    }

    private void codeMove(Operand srcOperand, Operand destOperand) {
        srcOperand = issa.read(srcOperand);
        destOperand = issa.write(destOperand);
        var insn = new Instruction.Move(srcOperand, destOperand);
        issa.recordDef(destOperand, insn);
        issa.recordUse(srcOperand, insn);
        code(insn);
    }

    private void codeReturn(Operand resultOperand) {
        resultOperand = issa.read(resultOperand);
        var insn = new Instruction.Ret(resultOperand);
        issa.recordUse(resultOperand, insn);
        code(insn);
    }

    private void codeCBR(BasicBlock block, Operand condition, BasicBlock trueBlock, BasicBlock falseBlock) {
        condition = issa.read(condition);
        var insn = new Instruction.ConditionalBranch(block, condition, trueBlock, falseBlock);
        assert !issa.isSealed(trueBlock);
        assert !issa.isSealed(falseBlock);
        block.addSuccessor(trueBlock);
        block.addSuccessor(falseBlock);
        issa.recordUse(condition, insn);
        code(insn);
    }

    private void codeCall(int newBase,
                          Operand.RegisterOperand targetOperand,
                          EZType.EZTypeFunction calleeType,
                          Operand.RegisterOperand ...arguments) {
        if (targetOperand != null)
            targetOperand = (Operand.RegisterOperand) issa.write(targetOperand);
        Operand.RegisterOperand args[] = new Operand.RegisterOperand[arguments.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = (Operand.RegisterOperand) issa.read(arguments[i]);
        }
        var insn = new Instruction.Call(newBase, targetOperand, calleeType, args);
        for (int i = 0; i < args.length; i++) {
            issa.recordUse(args[i], insn);
        }
        if (targetOperand != null)
            issa.recordDef(targetOperand, insn);
        code(insn);
    }

    private void codeUnary(String opCode, Operand.RegisterOperand result, Operand operand) {
        operand = issa.read(operand);
        result = (Operand.RegisterOperand) issa.write(result);
        var insn = new Instruction.Unary(opCode, result, operand);
        issa.recordDef(result, insn);
        issa.recordUse(operand, insn);
        code(insn);
    }

    private void codeBinary(String opCode, Operand.RegisterOperand result, Operand left, Operand right) {
        left = issa.read(left);
        right = issa.read(right);
        result = (Operand.RegisterOperand) issa.write(result);
        var insn = new Instruction.Binary(opCode, result, left, right);
        issa.recordDef(result, insn);
        issa.recordUse(left, insn);
        issa.recordUse(right, insn);
        code(insn);
    }

    private void codeArrayLoad(Operand.LoadIndexedOperand loadIndexedOperand, Operand.RegisterOperand target) {
        loadIndexedOperand = new Operand.LoadIndexedOperand(issa.read(loadIndexedOperand.arrayOperand), issa.read(loadIndexedOperand.indexOperand));
        target = (Operand.RegisterOperand) issa.write(target);
        var insn = new Instruction.ArrayLoad(loadIndexedOperand, target);
        issa.recordDef(target, insn);
        issa.recordUse(loadIndexedOperand.arrayOperand, insn);
        issa.recordUse(loadIndexedOperand.indexOperand, insn);
        code(insn);
    }

    private void codeGetField(Operand.LoadFieldOperand loadFieldOperand, Operand.RegisterOperand target) {
        loadFieldOperand = new Operand.LoadFieldOperand(issa.read(loadFieldOperand.structOperand), loadFieldOperand.fieldName, loadFieldOperand.fieldIndex);
        target = (Operand.RegisterOperand) issa.write(target);
        var insn = new Instruction.GetField(loadFieldOperand, target);
        issa.recordDef(target, insn);
        issa.recordUse(loadFieldOperand.structOperand, insn);
        code(insn);
    }

    private void codeArrayStore(Operand value, Operand.LoadIndexedOperand loadIndexedOperand) {
        loadIndexedOperand = new Operand.LoadIndexedOperand(issa.read(loadIndexedOperand.arrayOperand), issa.read(loadIndexedOperand.indexOperand));
        value = issa.read(value);
        var insn = new Instruction.ArrayStore(value, loadIndexedOperand);
        issa.recordUse(loadIndexedOperand.arrayOperand, insn);
        issa.recordUse(loadIndexedOperand.indexOperand, insn);
        issa.recordUse(value, insn);
        code(insn);
    }

    private void codeSetField(Operand value, Operand.LoadFieldOperand loadFieldOperand) {
        loadFieldOperand = new Operand.LoadFieldOperand(issa.read(loadFieldOperand.structOperand), loadFieldOperand.fieldName, loadFieldOperand.fieldIndex);
        value = issa.read(value);
        var insn = new Instruction.SetField(value, loadFieldOperand);
        issa.recordUse(loadFieldOperand.structOperand, insn);
        issa.recordUse(value, insn);
        code(insn);
    }

    public StringBuilder toStr(StringBuilder sb, boolean verbose) {
        if (verbose) {
            sb.append(this.functionType.describe()).append("\n");
            registerPool.toStr(sb);
        }
        BasicBlock.toStr(sb, entry, new BitSet(), verbose);
        return sb;
    }

    public void dumpIR(boolean verbose, String title) {
        if (dumpTarget != null) {
            dumpTarget.append(title).append("\n");
            toStr(dumpTarget,verbose);
        }
        else {
            System.out.println(title);
            System.out.println(toStr(new StringBuilder(), verbose));
        }
    }

    public void livenessAnalysis() {
        new Liveness(this);
        this.hasLiveness = true;
    }

    public List<BasicBlock> getBlocks() {
        return BBHelper.findAllBlocks(entry);
    }

    public StringBuilder toDot(StringBuilder sb, boolean verbose) {
        sb.append("digraph CompiledFunction {\n");
        List<BasicBlock> blocks = getBlocks();
        for (BasicBlock block: blocks) {
            sb.append("L").append(block.bid).append(" [shape=none, margin=0, label=<");
            block.toDot(sb, verbose);
            sb.append(">];\n");
            for (BasicBlock s: block.successors) {
                sb.append("L").append(block.bid).append(" -> ").append("L").append(s.bid).append("\n");
            }
        }
        sb.append("}\n");
        return sb;
    }

    interface IncrementalSSA {
        Operand read(Operand operand);
        Operand write(Operand operand);
        void recordUse(Operand operand, Instruction instruction);
        void recordDef(Register reg, Instruction instruction);
        void recordDef(Operand operand, Instruction instruction);
        void sealBlock(BasicBlock block);
        boolean isSealed(BasicBlock block);
        void finish(EnumSet<Options> options);
    }

    /**
     * Support for AST to SSA IR using Braun's method.
     * See Simple and Efficient Construction of Static Single Assignment Form, 2013
     * Matthias Braun, Sebastian Buchwald, Sebastian Hack, Roland Leißa
     */
    static final class IncrementalSSABraun implements IncrementalSSA {

        CompiledFunction function;

        // For each unique variable (variable with same name in different scopes must be distinct)
        // a mapping is maintained for the name to SSA value (virtual register) in each block.
        // we could add this mapping to the BB itself but it seems nicer to keep it separate
        // at least for now.
        // Note that we use the nonSSAId as proxy for variable name
        // because this id is unique for non-SSA variables, but for SSA versions this refers
        // back to the original ID
        Map<Integer, Map<BasicBlock, Register>> currentDef = new HashMap<>();
        // Flags blocks that are completed in terms of instruction generation
        BitSet sealedBlocks = new BitSet();
        // Tracks Phis that are not finalized because the basic block is not yet sealed
        Map<BasicBlock, Map<Register, Instruction.Phi>> incompletePhis = new HashMap<>();

        // Not explicitly stated in the paper but implicit in the algo is
        // the availability of Def-use chains. We have to main this incrementally as we
        // generate code - used when eliminating trivial phis
        Map<Register, SSAEdges.SSADef> ssaDefUses = new HashMap<>();

        // This is not part of the spec, it is just an implementation detail
        // We pre-assign registers to local declared vars, but then the SSA part
        // creates new ones. However, the first time a variable is versioned we could just use the
        // original regnum.
        // This set tracks whether a version is being created first time
        // It also helps us maintain version numbers similar to traditional SSA
        Map<Integer,Integer> versioned = new HashMap<>();

        private IncrementalSSABraun(CompiledFunction function) {
            this.function = function;
        }

        /**
         * Associates a new definition (value) to a variable name within a basic block
         */
        private void writeVariable(Register variable, BasicBlock block, Register value) {
            currentDef.computeIfAbsent(variable.nonSSAId(), k -> new HashMap<>()).put(block, value);
        }

        /**
         * Looks up the current SSA value (virtual register) associated with a name, inside a block.
         * If no mapping is found, processing depends on status of the block.
         * @see #readVariableRecursive(Register, BasicBlock)
         */
        private Register readVariable(Register variable, BasicBlock block) {
            Map<BasicBlock, Register> defs = currentDef.get(variable.nonSSAId());
            if (defs != null && defs.containsKey(block)) {
                // local value numbering
                return defs.get(block);
            }
            // global value numbering
            return readVariableRecursive(variable, block);
        }

        /**
         * Called when a block does not have a mapping for a variable.
         * If the block is still under construction, then a Phi is inserted into the
         * block - and the phi is marked as incomplete.
         * If block is constructed, then we look at predecessors for definitions;
         * in case of more than 1 predecessor a phi is created with input
         * obtained recursively via each predecessor block.
         * In case of 1 predecessor the value is read recursively from that predecessor.
         */
        private Register readVariableRecursive(Register variable, BasicBlock block) {
            Register val;
            if (!isSealed(block)) {
                // incomplete CFG
                val = makeVersion(variable);
                Instruction.Phi phi = makePhi(val, block);
                incompletePhis.computeIfAbsent(block, k -> new HashMap<>()).put(variable, phi);
            }
            else if (block.predecessors.size() == 1) {
                // Optimize the common case of one predecessor: No phi needed
                val = readVariable(variable, block.predecessors.get(0));
            }
            else {
                // Break potential cycles with operandless phis
                val = makeVersion(variable);
                Instruction.Phi phi = makePhi(val, block);
                writeVariable(variable, block, val);
                val = addPhiOperands(variable,phi);
            }
            writeVariable(variable, block, val);
            return val;
        }

        private Instruction.Phi makePhi(Register val, BasicBlock block) {
            Instruction.Phi phi = new Instruction.Phi(val, new ArrayList<>());
            recordDef(val, phi);
            block.add(0, phi);
            return phi;
        }

        /**
         * Populate the members of a phi instruction
         */
        private Register addPhiOperands(Register variable, Instruction.Phi phi) {
            assert phi.numInputs() == 0;
            // Determine operands from predecessors
            for (BasicBlock pred: phi.block.predecessors) {
                phi.addInput(readVariable(variable,pred));
            }
            return tryRemovingPhi(phi);
        }

        // reference implementation https://github.com/dibyendumajumdar/libfirm/blob/master/ir/ir/ircons.c#L97
        private Register tryRemovingPhi(Instruction.Phi phi) {
            Register same = null;
            // Check if phi has distinct inputs
            for (int i = 0; i < phi.numInputs(); i++) {
                if (!phi.isRegisterInput(i))
                    // Cannot happen?
                    throw new IllegalStateException();
                var use = phi.inputAsRegister(i);
                if (use.equals(same) || use.equals(phi.value()))
                    continue; // Unique value or self reference
                if (same != null)
                    // More than 1 distinct value, so keep phi
                    return phi.value();
                same = use;
            }
            if (same == null) {
                // phi is unreachable or in the start block
                // Paper suggests we create an Undef, but we throw an exception
                // same = function.registerPool.newReg("Undef", null);
                throw new CompilerException("Undefined value for phi " + phi.value());
            }
            // remember uses except phi
            var users = getUsesExcept(phi);
            // remove all uses of phi to same and remove phi
            replacePhiValueAndUsers(phi, same);
            phi.block.deleteInstruction(phi);
            // try to recursively remove all phi users, which might have become trivial
            for (var use: users) {
                if (use instanceof Instruction.Phi phiuser)
                    tryRemovingPhi(phiuser);
            }
            return same;
        }

        /**
         * Reroute all uses of phi to new value
         */
        private void replacePhiValueAndUsers(Instruction.Phi phi, Register newValue) {
            var oldValue = phi.value();
            var oldDefUseChain = ssaDefUses.get(oldValue);
            var newDefUseChain = ssaDefUses.get(newValue);
            if (newDefUseChain == null) {
                throw new CompilerException("Expected error: undefined var " + newValue);
            }
            if (oldDefUseChain != null) {
                for (Instruction instruction: oldDefUseChain.useList) {
                    boolean replaced;
                    if (instruction instanceof Instruction.Phi somePhi) {
                        replaced = somePhi.replaceInput(oldValue, newValue);
                    }
                    else {
                        replaced = instruction.replaceUse(oldValue, newValue);
                    }
                    if (!replaced) {
                        throw new CompilerException("Discrepancy between var use list and var definition");
                    }
                }
                // Users of phi old value become users of the new value
                newDefUseChain.useList.addAll(oldDefUseChain.useList);
                oldDefUseChain.useList.clear();
            }
            // Since the phi is replaced by newvalue
            // we must also update the memoized defs
            replaceDefs(oldValue, newValue);
        }

        private List<Instruction> getUsesExcept(Instruction.Phi phi) {
            var oldDefUseChain = ssaDefUses.get(phi.value());
            if (oldDefUseChain == null) {
                return new ArrayList<>();
            }
            var useList = new ArrayList<>(oldDefUseChain.useList);
            useList.remove(phi);
            return useList;
        }

        // The Phi's def was replaced
        // We must also replace all occurrences of the phi def from the memoized defs
        // per Basic Block
        private void replaceDefs(Register oldValue, Register newValue) {
            var defs = currentDef.get(oldValue.nonSSAId());
            for (var bb: defs.keySet())
                defs.replace(bb, oldValue, newValue);
        }

        @Override
        public Operand read(Operand operand) {
            // We have to consider temps too because of boolean expressions
            // where temps are not SSA
            if (operand instanceof Operand.RegisterOperand localRegisterOperand) {
                var reg = readVariable(localRegisterOperand.reg, function.currentBlock);
                operand = new Operand.RegisterOperand(reg);
            }
            return operand;
        }
        @Override
        public Operand write(Operand operand) {
            // We have to consider temps too because of boolean expressions
            // where temps are not SSA
            if (operand instanceof Operand.RegisterOperand localRegisterOperand) {
                var variable = localRegisterOperand.reg;
                Register newValue = makeVersion(variable);
                writeVariable(variable, function.currentBlock, newValue);
                operand = new Operand.RegisterOperand(newValue);
            }
            return operand;
        }

        private Register makeVersion(Register variable) {
            Register newValue;
            Integer version = versioned.get(variable.nonSSAId());
            // Avoid creating a new value first time because we already
            // have a pre-created register we can use
            if (version == null) {
                newValue = variable;
                versioned.put(variable.nonSSAId(), 1);
            }
            else {
                versioned.put(variable.nonSSAId(), version + 1);
                newValue = function.registerPool.ssaReg(variable, version);
            }
            return newValue;
        }

        @Override
        public void recordUse(Operand operand, Instruction instruction) {
            if (operand instanceof Operand.RegisterOperand registerOperand) {
                SSAEdges.recordUse(ssaDefUses, instruction, registerOperand.reg);
            }
        }
        @Override
        public void recordDef(Register reg, Instruction instruction) {
            SSAEdges.recordDef(ssaDefUses, reg, instruction);
        }
        @Override
        public void recordDef(Operand operand, Instruction instruction) {
            if (operand instanceof Operand.RegisterOperand registerOperand) {
                SSAEdges.recordDef(ssaDefUses, registerOperand.reg, instruction);
            }
        }
        @Override
        public void sealBlock(BasicBlock block) {
            if (isSealed(block))
                return;
            var pendingPhis = incompletePhis.remove(block);
            if (pendingPhis != null) {
                for (var variable : pendingPhis.keySet()) {
                    addPhiOperands(variable, pendingPhis.get(variable));
                }
            }
            sealedBlocks.set(block.bid);
        }
        @Override
        public boolean isSealed(BasicBlock block) {
            return sealedBlocks.get(block.bid);
        }
        @Override
        public void finish(EnumSet<Options> options) {
            function.isSSA = true;
            if (options != null && options.contains(Options.DUMP_SSA_IR)) {
                function.dumpIR(false, "Post SSA IR");
            }
        }
    }

    static final class NoopIncrementalSSA implements IncrementalSSA {
        @Override
        public Operand read(Operand operand) {
            return operand;
        }
        @Override
        public Operand write(Operand operand) {
            return operand;
        }
        @Override
        public void recordUse(Operand operand, Instruction instruction) {
        }
        @Override
        public void recordDef(Register reg, Instruction instruction) {
        }
        @Override
        public void recordDef(Operand operand, Instruction instruction) {
        }
        @Override
        public void sealBlock(BasicBlock block) {
        }
        @Override
        public boolean isSealed(BasicBlock block) {
            return false;
        }
        @Override
        public void finish(EnumSet<Options> options) {
        }
    }
}
