package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Token;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.LatticeElement;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.*;

import static com.compilerprogramming.ezlang.types.LatticeElement.*;

/**
 * Input is a CFG over AST. the CFG should capture control flow that is
 * relevant for null analysis.
 * This performs an iterative forward data flow analysis to fixed point.
 * At the end of this each basic block (FlowBlock) in the CFG has
 * an associated set of facts that inform which variables are null
 * and which are not null or maybe null.
 */
public class NullableAnalysis {

    int reg = 0;
    ArrayList<LatticeElement> latticeElements = new ArrayList<>();
    private final EZType returnType;

    public static void analyze(TypeDictionary typeDictionary) {
        for (Symbol symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionSymbol) {
                new NullableAnalysis(functionSymbol);
            }
        }
    }
    public NullableAnalysis(Symbol.FunctionTypeSymbol functionSymbol) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        var flowGraph = FlowCFG.build(funcDecl);
//                var dot = flowGraph.toDot();
//                System.out.println(dot);
        returnType = ((EZType.EZTypeFunction) functionSymbol.type).returnType;
        setVirtualRegisters(funcDecl.scope);
        doAnalysis(flowGraph);
    }
    private void setVirtualRegisters(Scope scope) {
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.regNumber = reg++;
                var type = varSymbol.type;
                LatticeElement elem;
                if (varSymbol instanceof Symbol.ParameterSymbol) {
                    if (type instanceof EZType.EZTypeInteger)
                        elem = new LatticeElement(F_INT_BOTTOM);
                    else if (type instanceof EZType.EZTypeFloat)
                        elem = new LatticeElement(F_FLT_BOTTOM);
                    else if (type instanceof EZType.EZTypeNullable)
                        elem = new LatticeElement(F_REF_BOTTOM);
                    else
                        elem = new LatticeElement(F_REF_NOT_NULL);
                }
                else {
                    elem = type instanceof EZType.EZTypeInteger
                            ? new LatticeElement(F_INT_TOP)
                            : type instanceof EZType.EZTypeFloat
                            ? new LatticeElement(F_FLT_TOP)
                            : new LatticeElement(F_REF_TOP);
                }
                latticeElements.add(elem);
            }
        }
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    static final class Lattice {
        final LatticeElement[] vars;

        Lattice(List<LatticeElement> elementList) {
            vars = elementList.toArray(new LatticeElement[0]);
        }
        Lattice(LatticeElement[] elements) {
            vars = elements;
        }

        Lattice copy() {
            LatticeElement[] copyvars = new LatticeElement[vars.length];
            for (int i = 0; i < copyvars.length; i++)
                copyvars[i] = vars[i].copy();
            return new Lattice(copyvars);
        }

        LatticeElement get(int reg) {
            return vars[reg];
        }

        static Lattice merge(Lattice a, Lattice b) {
            Lattice out = a.copy();

            for (int i = 0; i < out.vars.length; i++) {
                out.vars[i].meet(b.vars[i]);
            }

            return out;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Lattice lattice = (Lattice) o;
            return Objects.deepEquals(vars, lattice.vars);
        }
    }

    LatticeElement analyzeExpr(AST.Expr e, Lattice facts) {
        // FlowCFG lowers every &&, ||, and ! into condition blocks. Their
        // operands are analyzed in those blocks; walking the original logical
        // AST again here would replay guarded operands after the paths merge.
        if (isLogical(e))
            return factFromType(e.type);

        if (e instanceof AST.NewExpr newExpr) {
            if (newExpr.type instanceof EZType.EZTypeArray arrayType) {
                if (newExpr.len != null)
                    analyzeExpr(newExpr.len, facts);
                if (newExpr.initValue != null)
                    checkAssignment(arrayType.getElementType(), analyzeExpr(newExpr.initValue, facts));
            }
            return new LatticeElement(F_REF_NOT_NULL);
        }

        if (e instanceof AST.InitExpr initExpr) {
            analyzeExpr(initExpr.newExpr, facts);
            LatticeElement result = new LatticeElement(F_REF_NOT_NULL);
            if (initExpr.newExpr.type instanceof EZType.EZTypeStruct typeStruct) {
                for (AST.Expr expr: initExpr.initExprList) {
                    if (expr instanceof AST.SetFieldExpr setFieldExpr) {
                        var fieldType = typeStruct.getField(setFieldExpr.fieldName);
                        var latticeElement = analyzeExpr(setFieldExpr.value,facts);
                        checkAssignment(fieldType,latticeElement);
                    }
                }
            }
            else if (initExpr.newExpr.type instanceof EZType.EZTypeArray arrayType) {
                var elemType = arrayType.getElementType();
                for (AST.Expr expr: initExpr.initExprList) {
                    AST.Expr value = expr instanceof AST.ArrayInitExpr arrayInitExpr
                            ? arrayInitExpr.value
                            : expr;
                    var latticeElement = analyzeExpr(value,facts);
                    checkAssignment(elemType,latticeElement);
                }
            }
            return result;
        }

        if (e instanceof AST.NameExpr name &&
                name.symbol instanceof Symbol.VarSymbol varSymbol) {
            return facts.get(varSymbol.regNumber).copy();
        }

        if (e instanceof AST.CallExpr call) {
            EZType.EZTypeFunction tf = (EZType.EZTypeFunction)call.callee.type;
            for (int i = 0; i < tf.args.size(); i++) {
                EZType type = tf.args.get(i).type;
                AST.Expr expr = call.args.get(i);
                var lattice = analyzeExpr(expr,facts);
                checkAssignment(type,lattice);
            }
            // Use function return type:
            // Foo  -> NON_NULL
            // Foo? -> UNKNOWN
            return factFromType(call.type);
        }

        if (e instanceof AST.GetFieldExpr field) {
            checkDereference(field.object, facts);
            return factFromType(field.type);
        }

        if (e instanceof AST.ArrayLoadExpr arrayLoad) {
            LatticeElement array = checkDereference(arrayLoad.array, facts);
            LatticeElement index = analyzeExpr(arrayLoad.expr, facts);
            return factFromType(arrayLoad.type);
        }

        if (e instanceof AST.LiteralExpr lit) {
            if (lit.value.str.equals("null")) {
                return new LatticeElement(F_REF_NULL);
            }
            if (lit.value.kind == Token.Kind.NUM) {
                if (lit.type instanceof EZType.EZTypeInteger)
                    return new LatticeElement(lit.value.num.longValue());
                if (lit.type instanceof EZType.EZTypeFloat)
                    return new LatticeElement(lit.value.num.doubleValue());
                return factFromType(lit.type);
            }
        }

        if (e instanceof AST.UnaryExpr un) {
            if (un.op.str.equals("#")) {
                checkDereference(un.expr, facts);
                return factFromType(un.type);
            }

            LatticeElement v = analyzeExpr(un.expr, facts);

            if (un.op.str.equals("-")) {
                if (v.isIntegerConstant())
                    return new LatticeElement(-v.intValue);
                if (v.isInteger())
                    return new LatticeElement(F_INT_BOTTOM);
                if (v.isFloatConstant())
                    return new LatticeElement(-v.floatValue);
                if (v.isFloat())
                    return new LatticeElement(F_FLT_BOTTOM);
                throw new CompilerException("Cannot apply - to non numeric value");
            }

            if (un.op.str.equals("!")) {
                if (v.isTrue()) return new LatticeElement(0L);
                if (v.isFalse()) return new LatticeElement(1L);
                return new LatticeElement(F_INT_BOTTOM);
            }
            return factFromType(un.type);
        }

        if (e instanceof AST.BinaryExpr bin) {
            LatticeElement result = null;
            LatticeElement a = analyzeExpr(bin.expr1, facts);
            LatticeElement b = analyzeExpr(bin.expr2, facts);

            if (a.isIntegerConstant() && b.isIntegerConstant()) {
                boolean isArith = false;
                long value = 0;
                switch (bin.op.str) {
                    case "+"  ->  {
                        value = a.intValue + b.intValue;
                        isArith = true;
                    }
                    case "-"  -> {
                        value = a.intValue - b.intValue;
                        isArith = true;
                    }
                    case "*"  -> {
                        value = a.intValue * b.intValue;
                        isArith = true;
                    }
                    case "/"  -> {
                        if (b.kind == F_INT_ZERO)
                            throw new CompilerException("Division by zero");
                        value = a.intValue / b.intValue;
                        isArith = true;
                    }
                    case "%"  -> {
                        if (b.kind == F_INT_ZERO)
                            throw new CompilerException("Division by zero");
                        value = a.intValue % b.intValue;
                        isArith = true;
                    }

                    case "==" -> {
                        value = a.intValue == b.intValue ? 1 : 0;
                    }
                    case "!=" -> {
                        value = a.intValue != b.intValue ? 1 : 0;
                    }
                    case "<"  -> {
                        value = a.intValue < b.intValue ? 1 : 0;
                    }
                    case "<=" -> {
                        value = a.intValue <= b.intValue ? 1 : 0;
                    }
                    case ">"  -> {
                        value = a.intValue >  b.intValue ? 1 : 0;
                    }
                    case ">=" -> {
                        value = a.intValue >= b.intValue ? 1 : 0;
                    }
                    case "&&" -> {
                        value = a.intValue != 0 && b.intValue != 0 ? 1 : 0;
                    }
                    case "||" -> {
                        value = a.intValue != 0 || b.intValue != 0 ? 1 : 0;
                    }

                    default -> throw new CompilerException("Unknown binary operator " + bin.op.str);
                }
                if (isArith)
                    result = new LatticeElement(value);
                else {
                    if (value == 1)
                        result = new LatticeElement(F_INT_NONZERO_CONST,1);
                    else
                        result = new LatticeElement(F_INT_ZERO);
                }
                return result;
            }
            if (a.isFloatConstant() && b.isFloatConstant()) {
                double floatValue = 0.0;
                long intValue = 0;
                boolean isArith = false;
                switch (bin.op.str) {
                    case "+" -> {
                        floatValue = a.floatValue + b.floatValue;
                        isArith = true;
                    }
                    case "-" -> {
                        floatValue = a.floatValue - b.floatValue;
                        isArith = true;
                    }
                    case "*" -> {
                        floatValue = a.floatValue * b.floatValue;
                        isArith = true;
                    }
                    case "/" -> {
                        floatValue = a.floatValue / b.floatValue;
                        isArith = true;
                    }
                    case "==" -> intValue = a.floatValue == b.floatValue ? 1 : 0;
                    case "!=" -> intValue = a.floatValue != b.floatValue ? 1 : 0;
                    case "<" -> intValue = a.floatValue < b.floatValue ? 1 : 0;
                    case "<=" -> intValue = a.floatValue <= b.floatValue ? 1 : 0;
                    case ">" -> intValue = a.floatValue > b.floatValue ? 1 : 0;
                    case ">=" -> intValue = a.floatValue >= b.floatValue ? 1 : 0;
                    default -> throw new CompilerException("Unknown binary operator " + bin.op.str);
                }
                if (isArith)
                    return new LatticeElement(floatValue);
                return intValue == 1
                        ? new LatticeElement(F_INT_NONZERO_CONST, 1)
                        : new LatticeElement(F_INT_ZERO);
            }
            return factFromType(bin.type);
        }

        return new LatticeElement(F_BOTTOM);
    }

    public void doAnalysis(FlowCFG.FlowGraph cfg) {
        Queue<FlowCFG.FlowBlock> worklist = new ArrayDeque<>();

        Map<FlowCFG.FlowBlock, Lattice> in = new HashMap<>();
        Map<FlowCFG.FlowBlock, Lattice> out = new HashMap<>();

        var initialFacts = new Lattice(latticeElements);
        in.put(cfg.entry, initialFacts);
        worklist.add(cfg.entry);

        while (!worklist.isEmpty()) {
            FlowCFG.FlowBlock b = worklist.remove();

            Lattice inLattice = in.get(b);
            if (inLattice == null)
                throw new CompilerException("Missing facts");
            Lattice outLattice = transferBlock(b, inLattice);

            if (!outLattice.equals(out.get(b))) {
                out.put(b, outLattice);

                for (FlowCFG.FlowEdge e : b.succs) {
                    Lattice edgeLattice = applyEdgeFacts(outLattice, e);

                    Lattice oldIn = in.get(e.to);
                    Lattice newIn = oldIn == null ? edgeLattice : Lattice.merge(oldIn, edgeLattice);

                    if (!newIn.equals(oldIn)) {
                        in.put(e.to, newIn);
                        worklist.add(e.to);
                    }
                }
            }
        }
    }

    Lattice transferBlock(FlowCFG.FlowBlock block, Lattice in) {
        Lattice lattice = in.copy();

        for (AST.Stmt stmt : block.statements) {
            transferStmt(stmt, lattice);
        }
        // A condition is an evaluation point produced by logical-expression
        // lowering. Analyze it on every block transfer so fixed-point revisits
        // use the current incoming facts.
        if (block.condition != null)
            analyzeExpr(block.condition, lattice);

        return lattice;
    }

    private void checkAssignment(EZType type, LatticeElement lattice) {
        if (type instanceof EZType.EZTypeInteger) {
            if (!lattice.isInteger())
                throw new CompilerException("Cannot assign non-int value to int");
            return;
        }

        if (type instanceof EZType.EZTypeFloat) {
            if (!lattice.isFloat())
                throw new CompilerException("Cannot assign non-float value to float");
            return;
        }

        if (type instanceof EZType.EZTypeNullable) {
            if (!lattice.isReference())
                throw new CompilerException("Cannot assign non reference value to reference type");
            return;
        }

        // non-null reference
        if (!lattice.isReference() || lattice.isNull() || lattice.isMaybeNull()) {
            throw new CompilerException("Cannot assign null or potentially null value");
        }
    }

    private LatticeElement checkDereference(AST.Expr receiver, Lattice facts) {
        LatticeElement lattice = analyzeExpr(receiver, facts);
        if (!lattice.isNotNull())
            throw new CompilerException("Cannot dereference null or potentially null value", receiver.lineNumber);
        return lattice;
    }

    private EZType.EZTypeStruct structType(EZType type) {
        if (type instanceof EZType.EZTypeStruct structType)
            return structType;
        if (type instanceof EZType.EZTypeNullable nullable &&
                nullable.baseType instanceof EZType.EZTypeStruct structType)
            return structType;
        throw new CompilerException("Expected struct type");
    }

    void transferStmt(AST.Stmt stmt, Lattice facts) {
        if (stmt instanceof AST.AssignStmt assign) {
            Symbol.VarSymbol sym = (Symbol.VarSymbol) assign.nameExpr.symbol;
            var lattice = analyzeExpr(assign.rhs, facts);
            checkAssignment(sym.type,lattice);
            facts.vars[sym.regNumber] = lattice;
            return;
        }

        if (stmt instanceof AST.VarStmt varStmt) {
            Symbol.VarSymbol sym = varStmt.symbol;
            var lattice = analyzeExpr(varStmt.expr, facts);
            checkAssignment(sym.type,lattice);
            facts.vars[sym.regNumber] = lattice;
            return;
        }

        if (stmt instanceof AST.ExprStmt exprStmt &&
                exprStmt.expr instanceof AST.SetFieldExpr setFieldExpr) {
            checkDereference(setFieldExpr.object, facts);
            EZType.EZTypeStruct structType = structType(setFieldExpr.object.type);
            EZType fieldType = structType.getField(setFieldExpr.fieldName);
            var lattice = analyzeExpr(setFieldExpr.value,facts);
            checkAssignment(fieldType,lattice);
            return;
        }

        if (stmt instanceof AST.ExprStmt exprStmt &&
                exprStmt.expr instanceof AST.ArrayStoreExpr arrayStoreExpr) {
            checkDereference(arrayStoreExpr.array, facts);
            LatticeElement index = analyzeExpr(arrayStoreExpr.expr, facts);
            EZType.EZTypeArray arrayType = null;
            EZType elementType = null;
            if (arrayStoreExpr.array.type instanceof EZType.EZTypeArray ta) {
                arrayType = ta;
            }
            else if (arrayStoreExpr.array.type instanceof EZType.EZTypeNullable ptr &&
                    ptr.baseType instanceof EZType.EZTypeArray ta) {
                arrayType = ta;
            }
            if (arrayType == null)
                return;
            elementType = arrayType.getElementType();
            var lattice = analyzeExpr(arrayStoreExpr.value,facts);
            checkAssignment(elementType,lattice);
            return;
        }

        if (stmt instanceof AST.ExprStmt exprStmt &&
                exprStmt.expr instanceof AST.CallExpr callExpr) {
            analyzeExpr(callExpr,facts);
            return;
        }

        if (stmt instanceof AST.ReturnStmt returnStmt && returnStmt.expr != null) {
            LatticeElement lattice = analyzeExpr(returnStmt.expr, facts);
            checkAssignment(returnType, lattice);
        }
    }

    Lattice applyEdgeFacts(Lattice in, FlowCFG.FlowEdge edge) {
        Lattice out = in.copy();

        if (edge.kind == FlowCFG.EdgeKind.TRUE) {
            applyConditionFacts(out, edge.condition, true);
        } else if (edge.kind == FlowCFG.EdgeKind.FALSE) {
            applyConditionFacts(out, edge.condition, false);
        }

        return out;
    }
    void applyConditionFacts(Lattice facts, AST.Expr expr, boolean branchIsTrue) {
        if (!(expr instanceof AST.BinaryExpr bin)) {
            return;
        }

        boolean leftNameRightNull =
                isName(bin.expr1) && analyzeExpr(bin.expr2, facts).isNull();

        boolean rightNameLeftNull =
                isName(bin.expr2) && analyzeExpr(bin.expr1, facts).isNull();

        if (!leftNameRightNull && !rightNameLeftNull) {
            return;
        }

        AST.NameExpr name = leftNameRightNull ? (AST.NameExpr) bin.expr1 : (AST.NameExpr) bin.expr2;
        Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) name.symbol;
        String op = bin.op.str;
        LatticeElement lattice;
        if (branchIsTrue) {
            lattice = op.equals("!=")
                    ? new LatticeElement(F_REF_NOT_NULL)
                    : new LatticeElement(F_REF_NULL);
        } else {
            lattice = op.equals("!=")
                    ? new LatticeElement(F_REF_NULL)
                    : new LatticeElement(F_REF_NOT_NULL);
        }
        facts.vars[varSymbol.regNumber] = lattice;
    }

    boolean isName(AST.Expr e) {
        return e instanceof AST.NameExpr;
    }

    boolean isLogical(AST.Expr e) {
        if (e instanceof AST.BinaryExpr binary)
            return binary.op.str.equals("&&") || binary.op.str.equals("||");
        return e instanceof AST.UnaryExpr unary && unary.op.str.equals("!");
    }

}
