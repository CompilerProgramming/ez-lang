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

    public NullableAnalysis(Symbol.FunctionTypeSymbol functionSymbol, TypeDictionary typeDictionary) {
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionSymbol.functionDecl;
        setVirtualRegisters(funcDecl.scope);
    }
    private void setVirtualRegisters(Scope scope) {
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.regNumber = reg++;
                LatticeElement elem = null;
                if (varSymbol instanceof Symbol.ParameterSymbol) {
                    var type = varSymbol.type;
                    if (type.isPrimitive())
                        // Some int value, as our only primitive type is int
                        elem = new LatticeElement(F_INT);
                    else if (type instanceof EZType.EZTypeNullable)
                        elem = new LatticeElement(F_MAYBE_NULL);
                    else
                        elem = new LatticeElement(F_NOT_NULL);
                }
                else {
                    elem = new LatticeElement(F_UNKNOWN);
                }
                latticeElements.add(elem);
            }
        }
        for (Scope childScope: scope.children) {
            setVirtualRegisters(childScope);
        }
    }

    static final byte F_UNKNOWN = 0;
    static final byte F_NOT_NULL = 1;
    static final byte F_NULL = 2;
    static final byte F_MAYBE_NULL = 3; // BOTTOM
    static final byte F_ZERO = 4;
    static final byte F_NONZERO_CONST = 5;
    static final byte F_NONZERO_VARYING = 6;
    static final byte F_INT = 7;

    // Associated with each register
    static final class LatticeElement {
        public byte kind;
        private long intValue;

        public LatticeElement() {
            this.kind = F_UNKNOWN;
        }
        public LatticeElement(byte kind) {
            this.kind = kind;
        }
        public LatticeElement(byte kind,long value) {
            this.kind = kind;
            this.intValue = value;
        }
        public LatticeElement(long value) {
            setIntValue(value);
        }

        boolean nullable() {
            return kind == F_NULL || kind == F_NOT_NULL || kind == F_MAYBE_NULL;
        }
        boolean someInt() {
            return kind == F_INT || kind == F_ZERO || kind == F_NONZERO_CONST || kind == F_NONZERO_VARYING;
        }
        LatticeElement copy() {
            return new LatticeElement(kind,intValue);
        }
        boolean isTrue() {
            return kind == F_NONZERO_CONST || kind == F_NONZERO_VARYING;
        }
        boolean isFalse() {
            return kind == F_ZERO;
        }
        boolean isIntegerConstant() {
            return kind == F_ZERO || kind == F_NONZERO_CONST;
        }
        boolean setIntValue(long value) {
            byte prevKind = kind;
            if (kind == F_UNKNOWN) {
                this.intValue = value;
                this.kind = value == 0 ? F_ZERO : F_NONZERO_CONST;
            }
            else if (kind == F_ZERO && value != 0) {
                this.kind = F_INT;
            }
            else if (kind == F_NONZERO_CONST && (value == 0 || value != intValue)) {
                this.kind = F_INT;
            }
            else if (nullable()) {
                throw new CompilerException("Cannot assign integer value to lattice cell that is nullable");
            }
            return (kind != prevKind);
        }

        boolean setNull() {
            byte prevKind = kind;
            if (kind == F_UNKNOWN)
                kind = F_NULL;
            else if (kind == F_NOT_NULL)
                kind = F_MAYBE_NULL;
            else if (someInt()) {
                throw new CompilerException("Cannot assign null to lattice cell that is int type");
            }
            return (kind != prevKind);
        }

        boolean setNotNull() {
            byte prevKind = kind;
            if (kind == F_UNKNOWN)
                kind = F_NOT_NULL;
            else if (kind == F_NULL)
                kind = F_MAYBE_NULL;
            else if (someInt()) {
                throw new CompilerException("Cannot assign not null to lattice cell that is int type");
            }
            return (kind != prevKind);
        }

        boolean setMaybeNull() {
            byte prevKind = kind;
            if (kind == F_UNKNOWN)
                kind = F_MAYBE_NULL;
            else if (kind == F_NULL || kind == F_NOT_NULL)
                kind = F_MAYBE_NULL;
            else if (someInt()) {
                throw new CompilerException("Cannot assign maybe null to lattice cell that is int type");
            }
            return (kind != prevKind);
        }

        boolean meet(LatticeElement other) {
            byte oldKind = this.kind;
            if (kind == F_UNKNOWN) {
                kind = other.kind;
            }
            else if (nullable() && other.nullable()) {
                if (kind == F_NULL && other.kind == F_NOT_NULL ||
                        kind == F_NOT_NULL && other.kind == F_NULL)
                    kind = F_MAYBE_NULL;
            }
            else if (someInt() && other.someInt()) {
                if (kind == F_ZERO) {
                    if (other.kind == F_NONZERO_CONST || other.kind == F_NONZERO_VARYING) {
                        kind = F_INT;
                    }
                }
                else if (kind == F_NONZERO_CONST) {
                    if (other.kind == F_NONZERO_CONST && intValue != other.intValue) {
                        kind = F_INT;
                    }
                    else if (other.kind == F_ZERO) {
                        kind = F_INT;
                    }
                }
                else if (kind == F_NONZERO_VARYING && other.kind == F_ZERO) {
                    kind = F_INT;
                }
            }
            return kind != oldKind;
        }

        @Override
        public String toString() {
            switch (kind) {
                case F_UNKNOWN -> {
                    return "unknown";
                }
                case F_NULL -> { return "null"; }
                case F_NOT_NULL -> { return "not null"; }
                case F_MAYBE_NULL -> { return "maybe null"; }
                case F_ZERO -> { return "zero"; }
                case F_NONZERO_CONST -> { return "non-zero const"; }
                case F_NONZERO_VARYING -> { return "non-zero varying"; }
                case F_INT -> { return "int"; }
                default -> throw new CompilerException("Unknown type in lattice");
            }
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
    static final class Facts {
        final LatticeElement[] vars;

        Facts(List<LatticeElement> elementList) {
            vars = elementList.toArray(new LatticeElement[0]);
        }
        Facts(LatticeElement[] elements) {
            vars = elements;
        }

        Facts copy() {
            LatticeElement[] copyvars = new LatticeElement[vars.length];
            for (int i = 0; i < copyvars.length; i++)
                copyvars[i] = vars[i].copy();
            return new Facts(copyvars);
        }

        LatticeElement get(int reg) {
            return vars[reg];
        }

        static Facts merge(Facts a, Facts b) {
            Facts out = a.copy();

            for (int i = 0; i < out.vars.length; i++) {
                out.vars[i].meet(b.vars[i]);
            }

            return out;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Facts facts = (Facts) o;
            return Objects.deepEquals(vars, facts.vars);
        }
    }

    LatticeElement analyzeExpr(AST.Expr e, Facts facts) {
        if (e instanceof AST.NewExpr || e instanceof AST.InitExpr) {
            return new LatticeElement(F_NOT_NULL);
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
            // First check object is non-null.
            // Then use field type.
            return factFromType(field.type);
        }

        if (e instanceof AST.ArrayLoadExpr arrayLoad) {
            // First check array is non-null.
            // Then use element type.
            return factFromType(arrayLoad.type);
        }

        if (e instanceof AST.LiteralExpr lit) {
            if (lit.value.str.equals("null")) {
                return new LatticeElement(F_NULL);
            }
            if (lit.value.kind == Token.Kind.NUM) {
                return new LatticeElement(Long.parseLong(lit.value.str));
            }
        }

        if (e instanceof AST.UnaryExpr un) {
            LatticeElement v = analyzeExpr(un.expr, facts);

            if (un.op.str.equals("-")) {
                if (v.someInt())
                    v.setIntValue(-v.intValue);
                else
                    throw new CompilerException("Cannot apply - to non integer");
            }

            if (un.op.str.equals("!")) {
                if (v.isTrue()) return new LatticeElement(0L);
                if (v.isFalse()) return new LatticeElement(1L);
                return new LatticeElement(F_INT);
            }
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
                        if (b.kind == F_ZERO)
                            throw new CompilerException("Division by zero");
                        value = a.intValue / b.intValue;
                    }
                    case "%"  -> {
                        if (b.kind == F_ZERO)
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
                        result = new LatticeElement(F_NONZERO_CONST,1);
                    else
                        result = new LatticeElement(F_ZERO);
                }
                return result;
            }
        }

        return new LatticeElement();
    }

    LatticeElement factFromType(EZType type) {
        if (type != null) {
            if (type instanceof EZType.EZTypeNullable)
                return new LatticeElement(F_MAYBE_NULL);
            else if (type instanceof EZType.EZTypeNull)
                return new LatticeElement(F_NULL);
            else if (!type.isPrimitive())
                return new LatticeElement(F_NOT_NULL);
        }
        return new LatticeElement();
    }

    public void doAnalysis(FlowCFG.FlowGraph cfg) {
        Queue<FlowCFG.FlowBlock> worklist = new ArrayDeque<>();

        Map<FlowCFG.FlowBlock, Facts> in = new HashMap<>();
        Map<FlowCFG.FlowBlock, Facts> out = new HashMap<>();

        var initialFacts = new Facts(latticeElements);
        in.put(cfg.entry, initialFacts);
        worklist.add(cfg.entry);

        while (!worklist.isEmpty()) {
            FlowCFG.FlowBlock b = worklist.remove();

            Facts inFacts = in.get(b);
            if (inFacts == null)
                throw new CompilerException("Missing facts");
            Facts outFacts = transferBlock(b, inFacts);

            if (!outFacts.equals(out.get(b))) {
                out.put(b, outFacts);

                for (FlowCFG.FlowEdge e : b.succs) {
                    Facts edgeFacts = applyEdgeFacts(outFacts, e);

                    Facts oldIn = in.get(e.to);
                    Facts newIn = oldIn == null ? edgeFacts : Facts.merge(oldIn, edgeFacts);

                    if (!newIn.equals(oldIn)) {
                        in.put(e.to, newIn);
                        worklist.add(e.to);
                    }
                }
            }
        }
    }

    Facts transferBlock(FlowCFG.FlowBlock block, Facts in) {
        Facts facts = in.copy();

        for (AST.Stmt stmt : block.statements) {
            transferStmt(stmt, facts);
        }

        return facts;
    }

    private void checkAssignment(EZType type, LatticeElement lattice) {
        if (type.isPrimitive()) {
            if (!lattice.someInt() && lattice.kind != F_UNKNOWN)
                throw new CompilerException("Cannot assign reference/null to int");
            return;
        }

        if (type instanceof EZType.EZTypeNullable) {
            return; // null allowed
        }

        // non-null reference
        if (lattice.kind == F_NULL || lattice.kind == F_MAYBE_NULL) {
            throw new CompilerException("Cannot assign null or potentially null value");
        }
    }

    void transferStmt(AST.Stmt stmt, Facts facts) {
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
            EZType.EZTypeStruct structType = (EZType.EZTypeStruct) setFieldExpr.object.type;
            EZType fieldType = structType.getField(setFieldExpr.fieldName);
            var lattice = analyzeExpr(setFieldExpr.value,facts);
            checkAssignment(fieldType,lattice);
            return;
        }

        if (stmt instanceof AST.ExprStmt exprStmt &&
                exprStmt.expr instanceof AST.ArrayStoreExpr arrayStoreExpr) {
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
        }

        if (stmt instanceof AST.ExprStmt exprStmt &&
                exprStmt.expr instanceof AST.CallExpr callExpr) {
            analyzeExpr(callExpr,facts);
        }
    }


    Facts applyEdgeFacts(Facts in, FlowCFG.FlowEdge edge) {
        Facts out = in.copy();

        if (edge.kind == FlowCFG.EdgeKind.TRUE) {
            applyConditionFacts(out, edge.condition, true);
        } else if (edge.kind == FlowCFG.EdgeKind.FALSE) {
            applyConditionFacts(out, edge.condition, false);
        }

        return out;
    }
    void applyConditionFacts(Facts facts, AST.Expr expr, boolean branchIsTrue) {
        if (!(expr instanceof AST.BinaryExpr bin)) {
            return;
        }

        boolean leftNameRightNull =
                isName(bin.expr1) && analyzeExpr(bin.expr2, facts).kind == F_NULL;

        boolean rightNameLeftNull =
                isName(bin.expr2) && analyzeExpr(bin.expr1, facts).kind == F_NULL;

        if (!leftNameRightNull && !rightNameLeftNull) {
            return;
        }

        AST.NameExpr name = leftNameRightNull ? (AST.NameExpr) bin.expr1 : (AST.NameExpr) bin.expr2;
        Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) name.symbol;
        String op = bin.op.str;
        LatticeElement lattice;
        if (branchIsTrue) {
            lattice = op.equals("!=")
                    ? new LatticeElement(F_NOT_NULL)
                    : new LatticeElement(F_NULL);
        } else {
            lattice = op.equals("!=")
                    ? new LatticeElement(F_NULL)
                    : new LatticeElement(F_NOT_NULL);
        }
        facts.vars[varSymbol.regNumber] = lattice;
    }

    boolean isName(AST.Expr e) {
        return e instanceof AST.NameExpr;
    }

}
