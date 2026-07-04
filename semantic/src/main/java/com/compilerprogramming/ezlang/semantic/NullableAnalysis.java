package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Token;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.*;

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

    public NullableAnalysis(Symbol.FunctionTypeSymbol functionSymbol, TypeDictionary typeDictionary) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        returnType = ((EZType.EZTypeFunction) functionSymbol.type).returnType;
        setVirtualRegisters(funcDecl.scope);
    }
    private void setVirtualRegisters(Scope scope) {
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.regNumber = reg++;
                var type = varSymbol.type;
                LatticeElement elem;
                if (varSymbol instanceof Symbol.ParameterSymbol) {
                    if (type.isPrimitive())
                        elem = new LatticeElement(F_INT_BOTTOM);
                    else if (type instanceof EZType.EZTypeNullable)
                        elem = new LatticeElement(F_REF_BOTTOM);
                    else
                        elem = new LatticeElement(F_REF_NOT_NULL);
                }
                else {
                    elem = type.isPrimitive()
                            ? new LatticeElement(F_INT_TOP)
                            : new LatticeElement(F_REF_TOP);
                }
                latticeElements.add(elem);
            }
        }
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    //                 TOP
    //              /     \
    //         REF_TOP   INT_TOP
    //         /     \    /  |   \
    //      NULL NOT_NULL ZERO NZC NZV
    //         \     /     \  |  /
    //        REF_BOTTOM   INT_BOTTOM
    //              \       /
    //               BOTTOM

    static final byte F_TOP = 0;             // no usable fact yet

    static final byte F_REF_TOP = 1;         // known reference type, value unknown
    static final byte F_REF_NOT_NULL = 2;
    static final byte F_REF_NULL = 3;
    static final byte F_REF_BOTTOM = 4;      // maybe null

    static final byte F_INT_TOP = 5;         // known int type, value unknown
    static final byte F_INT_ZERO = 6;
    static final byte F_INT_NONZERO_CONST = 7;
    static final byte F_INT_NONZERO_VARYING = 8;
    static final byte F_INT_BOTTOM = 9;      // may be zero or non-zero

    static final byte F_BOTTOM = 10;         // impossible / contradiction

    // Associated with each register
    static final class LatticeElement {
        public byte kind;
        private long intValue;

        public LatticeElement(byte kind) {
            this.kind = kind;
        }
        public LatticeElement(byte kind,long value) {
            this.kind = kind;
            this.intValue = value;
        }
        public LatticeElement(long value) {
            kind = F_INT_TOP;
            setIntValue(value);
        }
        LatticeElement copy() {
            return new LatticeElement(kind,intValue);
        }
        boolean isTrue() {
            return kind == F_INT_NONZERO_CONST || kind == F_INT_NONZERO_VARYING;
        }
        boolean isFalse() {
            return kind == F_INT_ZERO;
        }
        boolean isIntegerConstant() {
            return kind == F_INT_ZERO || kind == F_INT_NONZERO_CONST;
        }
        boolean setIntValue(long value) {
            byte prevKind = kind;
            if (kind == F_INT_TOP) {
                this.intValue = value;
                this.kind = value == 0 ? F_INT_ZERO : F_INT_NONZERO_CONST;
            }
            else if (kind == F_INT_ZERO && value != 0) {
                this.kind = F_INT_BOTTOM;
            }
            else if (kind == F_INT_NONZERO_CONST && (value == 0 || value != intValue)) {
                this.kind = F_INT_BOTTOM;
            }
            else if (!isInteger()) {
                throw new CompilerException("Cannot assign integer value to reference type");
            }
            return (kind != prevKind);
        }
        boolean meet(LatticeElement other) {
            byte old = kind;

            // universal top/bottom
            if (kind == F_TOP) {
                copyFrom(other);
                return true;
            }

            if (kind == F_BOTTOM || other.kind == F_TOP)
                return false;

            if (other.kind == F_BOTTOM) {
                kind = F_BOTTOM;
                return kind != old;
            }

            // same value
            if (kind == other.kind) {
                if (kind == F_INT_NONZERO_CONST &&
                        intValue != other.intValue) {
                    kind = F_INT_NONZERO_VARYING;
                }
                return kind != old;
            }

            // reference lattice
            if (isReference(kind) && isReference(other.kind)) {
                kind = meetReference(other);
                return kind != old;
            }

            // integer lattice
            if (isInteger(kind) && isInteger(other.kind)) {
                meetInteger(other);
                return kind != old;
            }

            // impossible
            kind = F_BOTTOM;
            return kind != old;
        }

        private byte meetReference(LatticeElement other) {

            if (kind == F_REF_TOP)
                return other.kind;

            if (other.kind == F_REF_TOP)
                return kind;

            return switch (kind) {

                case F_REF_NULL ->
                        other.kind == F_REF_NULL
                                ? F_REF_NULL
                                : F_REF_BOTTOM;

                case F_REF_NOT_NULL ->
                        other.kind == F_REF_NOT_NULL
                                ? F_REF_NOT_NULL
                                : F_REF_BOTTOM;

                case F_REF_BOTTOM ->
                        F_REF_BOTTOM;

                default ->
                        F_BOTTOM;
            };
        }

        private void meetInteger(LatticeElement other) {

            if (kind == F_INT_TOP) {
                copyFrom(other);
                return;
            }

            if (other.kind == F_INT_TOP)
                return;

            switch (kind) {

                case F_INT_ZERO -> {
                    if (other.kind != F_INT_ZERO)
                        kind = F_INT_BOTTOM;
                }

                case F_INT_NONZERO_CONST -> {
                    switch (other.kind) {

                        case F_INT_NONZERO_CONST -> {
                            if (intValue != other.intValue)
                                kind = F_INT_NONZERO_VARYING;
                        }

                        case F_INT_NONZERO_VARYING ->
                                kind = F_INT_NONZERO_VARYING;

                        case F_INT_ZERO,
                             F_INT_BOTTOM ->
                                kind = F_INT_BOTTOM;
                    }
                }

                case F_INT_NONZERO_VARYING -> {
                    if (other.kind == F_INT_ZERO ||
                            other.kind == F_INT_BOTTOM)
                        kind = F_INT_BOTTOM;
                }

                case F_INT_BOTTOM -> {
                }
            }
        }
        private static boolean isReference(byte kind) {
            return kind >= F_REF_TOP && kind <= F_REF_BOTTOM;
        }

        private static boolean isInteger(byte kind) {
            return kind >= F_INT_TOP && kind <= F_INT_BOTTOM;
        }
        void copyFrom(LatticeElement other) {
            this.kind = other.kind;
            this.intValue = other.intValue;
        }
        boolean isTop() {
            return kind == F_TOP;
        }

        boolean isBottom() {
            return kind == F_BOTTOM;
        }

        boolean isReference() {
            return isReference(kind);
        }

        boolean isInteger() {
            return isInteger(kind);
        }

        boolean isNull() {
            return kind == F_REF_NULL;
        }

        boolean isNotNull() {
            return kind == F_REF_NOT_NULL;
        }

        boolean isMaybeNull() {
            return kind == F_REF_BOTTOM;
        }

        boolean isZero() {
            return kind == F_INT_ZERO;
        }

        boolean isNonZero() {
            return kind == F_INT_NONZERO_CONST ||
                    kind == F_INT_NONZERO_VARYING;
        }
        @Override
        public String toString() {
            return switch (kind) {
                case F_TOP -> "⊤";

                case F_REF_TOP -> "ref";
                case F_REF_NOT_NULL -> "not-null";
                case F_REF_NULL -> "null";
                case F_REF_BOTTOM -> "maybe-null";

                case F_INT_TOP -> "int";
                case F_INT_ZERO -> "0";
                case F_INT_NONZERO_CONST -> Long.toString(intValue);
                case F_INT_NONZERO_VARYING -> "non-zero";
                case F_INT_BOTTOM -> "int?";

                case F_BOTTOM -> "⊥";

                default -> throw new CompilerException("Unknown lattice kind: " + kind);
            };
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            LatticeElement that = (LatticeElement) o;
            return kind == that.kind && intValue == that.intValue;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, intValue);
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
        if (e instanceof AST.NewExpr) {
            return new LatticeElement(F_REF_NOT_NULL);
        }

        if (e instanceof AST.InitExpr initExpr) {
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
                    var latticeElement = analyzeExpr(expr,facts);
                    checkAssignment(elemType,latticeElement);
                }
            }
            return new LatticeElement(F_REF_NOT_NULL);
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
            checkDereference(arrayLoad.array, facts);
            analyzeExpr(arrayLoad.expr, facts);
            return factFromType(arrayLoad.type);
        }

        if (e instanceof AST.LiteralExpr lit) {
            if (lit.value.str.equals("null")) {
                return new LatticeElement(F_REF_NULL);
            }
            if (lit.value.kind == Token.Kind.NUM) {
                return new LatticeElement(Long.parseLong(lit.value.str));
            }
        }

        if (e instanceof AST.UnaryExpr un) {
            LatticeElement v = analyzeExpr(un.expr, facts);

            if (un.op.str.equals("-")) {
                if (v.isIntegerConstant())
                    return new LatticeElement(-v.intValue);
                if (v.isInteger())
                    return new LatticeElement(F_INT_BOTTOM);
                throw new CompilerException("Cannot apply - to non integer");
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
                    }
                    case "%"  -> {
                        if (b.kind == F_INT_ZERO)
                            throw new CompilerException("Division by zero");
                        value = a.intValue % b.intValue;
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
            return factFromType(bin.type);
        }

        return new LatticeElement(F_BOTTOM);
    }

    LatticeElement factFromType(EZType type) {
        if (type != null) {
            if (type instanceof EZType.EZTypeNullable)
                return new LatticeElement(F_REF_BOTTOM);
            else if (type instanceof EZType.EZTypeNull)
                return new LatticeElement(F_REF_NULL);
            else if (type instanceof EZType.EZTypeArray ||
                     type instanceof EZType.EZTypeStruct)
                return new LatticeElement(F_REF_NOT_NULL);
            else if (type.isPrimitive())
                return new LatticeElement(F_INT_BOTTOM);
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

        return lattice;
    }

    private void checkAssignment(EZType type, LatticeElement lattice) {
        if (type.isPrimitive()) {
            if (!lattice.isInteger())
                throw new CompilerException("Cannot assign reference/null to int");
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

    private void checkDereference(AST.Expr receiver, Lattice facts) {
        LatticeElement lattice = analyzeExpr(receiver, facts);
        if (!lattice.isNotNull())
            throw new CompilerException("Cannot dereference null or potentially null value", receiver.lineNumber);
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
            analyzeExpr(arrayStoreExpr.expr, facts);
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

}
