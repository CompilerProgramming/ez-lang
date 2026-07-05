package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.ASTVisitor;

import java.util.*;

/**
 * Builds a CFG on top of AST, focusing on control flow.
 */
public final class FlowCFG {
    public enum EdgeKind {
        NORMAL,
        TRUE,
        FALSE
    }

    public static final class FlowEdge {
        public final FlowBlock from;
        public final FlowBlock to;
        public final EdgeKind kind;
        public final AST.Expr condition; // non-null for TRUE/FALSE edges

        FlowEdge(FlowBlock from, FlowBlock to, EdgeKind kind, AST.Expr condition) {
            this.from = from;
            this.to = to;
            this.kind = kind;
            this.condition = condition;
        }

        @Override
        public String toString() {
            if (condition != null) {
                return "B" + from.id + " -" + kind + "(" + FlowGraph.render(condition) + ")-> B" + to.id;
            }
            return "B" + from.id + " -" + kind + "-> B" + to.id;
        }
    }

    public static final class FlowBlock {
        public final int id;

        /*
         * Ordinary sequential statements.
         * No if/while/break/continue/return should usually be stored here,
         * except return/break/continue as final block statements if desired.
         */
        public final List<AST.Stmt> statements = new ArrayList<>();

        /*
         * If this block branches, condition is non-null.
         * For short-circuit && / ||, each split condition gets its own block.
         */
        public AST.Expr condition;

        public final List<FlowEdge> preds = new ArrayList<>();
        public final List<FlowEdge> succs = new ArrayList<>();

        FlowBlock(int id) {
            this.id = id;
        }

        public boolean isTerminated() {
            return !succs.isEmpty();
        }

        @Override
        public String toString() {
            return "B" + id;
        }
    }

    public static final class FlowGraph {
        public final AST.FuncDecl function;
        public final FlowBlock entry;
        public final FlowBlock exit;

        public final List<FlowBlock> blocks = new ArrayList<>();
        public final List<FlowEdge> edges = new ArrayList<>();

        FlowGraph(AST.FuncDecl function, FlowBlock entry, FlowBlock exit) {
            this.function = function;
            this.entry = entry;
            this.blocks.add(entry);
            this.exit = exit;
            this.blocks.add(exit);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (FlowBlock b : blocks) {
                sb.append("B" + b.id + ":\n");

                if (b == entry) {
                    sb.append("  <entry>\n");
                }

                if (b == exit) {
                    sb.append("  <exit>\n");
                }

                for (AST.Stmt s : b.statements) {
                    sb.append("  " + render(s)).append('\n');
                }

                if (b.condition != null) {
                    sb.append("  condition: " + render(b.condition)).append('\n');
                }

                for (FlowEdge e : b.succs) {
                    sb.append("  " + e).append('\n');
                }
            }
            return sb.toString();
        }

        public String toDot() {
            StringBuilder sb = new StringBuilder();
            sb.append("digraph CFG {\n");

            for (FlowBlock b : blocks) {
                sb.append("  B").append(b.id).append(" [shape=box,label=\"");
                sb.append("B").append(b.id);

                if (b == entry) sb.append("\\n<entry>");
                if (b == exit) sb.append("\\n<exit>");

                for (AST.Stmt s : b.statements) {
                    sb.append("\\n").append(escape(render(s)));
                }

                if (b.condition != null) {
                    sb.append("\\n? ").append(escape(render(b.condition)));
                }

                sb.append("\"];\n");
            }

            for (FlowEdge e : edges) {
                sb.append("  B").append(e.from.id)
                        .append(" -> B").append(e.to.id);

                if (e.kind != EdgeKind.NORMAL) {
                    sb.append(" [label=\"").append(e.kind).append("\"]");
                }

                sb.append(";\n");
            }

            sb.append("}\n");
            return sb.toString();
        }

        private static String oneLine(AST ast) {
            if (ast == null) return "";
            return ast.toString().replace("\n", " ").trim();
        }

        /*
         * Logical descendants have their own CFG blocks. Avoid recursively
         * printing them again as part of the enclosing AST node.
         */
        private static String render(AST ast) {
            String result = oneLine(ast);
            List<String> shortCircuitExpressions = new ArrayList<>();
            ast.accept(new ASTVisitor() {
                @Override
                public ASTVisitor enter(AST.BinaryExpr expr) {
                    if (expr.op.str.equals("&&") || expr.op.str.equals("||")) {
                        shortCircuitExpressions.add(oneLine(expr));
                        return null;
                    }
                    return this;
                }

                @Override
                public ASTVisitor enter(AST.UnaryExpr expr) {
                    if (expr.op.str.equals("!")) {
                        shortCircuitExpressions.add(oneLine(expr));
                        return null;
                    }
                    return this;
                }
            });

            for (String expression : shortCircuitExpressions) {
                result = result.replace(expression, "<short-circuit>");
            }
            return result;
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static final class LoopTargets {
        final FlowBlock breakTarget;
        final FlowBlock continueTarget;

        LoopTargets(FlowBlock breakTarget, FlowBlock continueTarget) {
            this.breakTarget = breakTarget;
            this.continueTarget = continueTarget;
        }
    }

    private final FlowGraph graph;
    private int nextId = 0;
    private final Deque<LoopTargets> loopStack = new ArrayDeque<>();

    private FlowCFG(AST.FuncDecl fn) {
        FlowBlock entry = newBlock();
        FlowBlock exit = newBlock();
        this.graph = new FlowGraph(fn, entry, exit);
    }

    public static FlowGraph build(AST.FuncDecl fn) {
        FlowCFG builder = new FlowCFG(fn);

        FlowBlock body = builder.newBlock();
        builder.addEdge(builder.graph.entry, body, EdgeKind.NORMAL, null);

        FlowBlock fallthrough = builder.buildStmt(fn.block, body);

        if (!fallthrough.isTerminated()) {
            builder.addEdge(fallthrough, builder.graph.exit, EdgeKind.NORMAL, null);
        }

        return builder.graph;
    }

    private FlowBlock newBlock() {
        FlowBlock b = new FlowBlock(nextId++);
        if (graph != null)
            // Chicken and egg problem with entry/exit blocks
            graph.blocks.add(b);
        return b;
    }

    private void addEdge(FlowBlock from, FlowBlock to, EdgeKind kind, AST.Expr condition) {
        FlowEdge e = new FlowEdge(from, to, kind, condition);
        from.succs.add(e);
        to.preds.add(e);
        graph.edges.add(e);
    }

    private FlowBlock buildStmt(AST.Stmt stmt, FlowBlock cur) {
        if (stmt instanceof AST.BlockStmt block) {
            return buildBlock(block, cur);
        }

        if (stmt instanceof AST.IfElseStmt ifs) {
            return buildIf(ifs, cur);
        }

        if (stmt instanceof AST.WhileStmt wh) {
            return buildWhile(wh, cur);
        }

        if (stmt instanceof AST.ReturnStmt ret) {
            if (ret.expr != null) {
                cur = buildExpr(ret.expr, cur);
            }
            cur.statements.add(ret);
            addEdge(cur, graph.exit, EdgeKind.NORMAL, null);
            return newBlock();
        }

        if (stmt instanceof AST.BreakStmt br) {
            cur.statements.add(br);
            addEdge(cur, loopStack.peek().breakTarget, EdgeKind.NORMAL, null);
            return newBlock();
        }

        if (stmt instanceof AST.ContinueStmt cont) {
            cur.statements.add(cont);
            addEdge(cur, loopStack.peek().continueTarget, EdgeKind.NORMAL, null);
            return newBlock();
        }

        if (stmt instanceof AST.AssignStmt assign) {
            cur = buildExpr(assign.rhs, cur);
        } else if (stmt instanceof AST.VarStmt var) {
            cur = buildExpr(var.expr, cur);
        } else if (stmt instanceof AST.ExprStmt exprStmt) {
            cur = buildExpr(exprStmt.expr, cur);
        }

        // Store the statement after the blocks needed to evaluate its expression.
        cur.statements.add(stmt);
        return cur;
    }

    private FlowBlock buildBlock(AST.BlockStmt block, FlowBlock cur) {
        for (AST.Stmt stmt : block.stmtList) {
            cur = buildStmt(stmt, cur);
        }
        return cur;
    }

    /**
     * Builds an if/else diamond and returns its join block.
     * {@link #buildCondition} may insert additional blocks between {@code cur}
     * and the two branch-entry blocks when the condition short-circuits.
     * A branch that terminates (for example with return) has no edge to the join.
     *
     * <pre>
     *                  +--TRUE--> thenBlock -> thenExit --NORMAL--+
     * cur -> condition                                           +--> afterIf
     *                  +--FALSE-> elseBlock -> elseExit --NORMAL--+
     * </pre>
     *
     * When there is no else statement, {@code elseBlock} is an empty block
     * connected directly to {@code afterIf}.
     */
    private FlowBlock buildIf(AST.IfElseStmt ifs, FlowBlock cur) {
        FlowBlock thenBlock = newBlock();
        FlowBlock elseBlock = newBlock();
        FlowBlock afterIf = newBlock();

        buildCondition(ifs.condition, cur, thenBlock, elseBlock);

        FlowBlock thenExit = buildStmt(ifs.ifStmt, thenBlock);
        if (!thenExit.isTerminated()) {
            addEdge(thenExit, afterIf, EdgeKind.NORMAL, null);
        }

        if (ifs.elseStmt != null) {
            FlowBlock elseExit = buildStmt(ifs.elseStmt, elseBlock);
            if (!elseExit.isTerminated()) {
                addEdge(elseExit, afterIf, EdgeKind.NORMAL, null);
            }
        } else {
            addEdge(elseBlock, afterIf, EdgeKind.NORMAL, null);
        }

        return afterIf;
    }

    /**
     * Builds a loop with a dedicated condition entry and returns the block
     * reached when the condition is false or a break is executed.
     * {@link #buildCondition} may expand {@code condBlock} into several blocks.
     *
     * <pre>
     *                                      +--------------------+
     *                                      |                    |
     * cur --NORMAL--> condBlock --TRUE--> bodyBlock -> bodyExit-+
     *                       |
     *                       +--FALSE--> afterLoop
     *
     * continue ---------------------> condBlock
     * break    ---------------------> afterLoop
     * </pre>
     *
     * The loop-back edge is omitted when the body terminates.
     */
    private FlowBlock buildWhile(AST.WhileStmt wh, FlowBlock cur) {
        FlowBlock condBlock = newBlock();
        FlowBlock bodyBlock = newBlock();
        FlowBlock afterLoop = newBlock();

        addEdge(cur, condBlock, EdgeKind.NORMAL, null);

        loopStack.push(new LoopTargets(afterLoop, condBlock));

        buildCondition(wh.condition, condBlock, bodyBlock, afterLoop);

        FlowBlock bodyExit = buildStmt(wh.stmt, bodyBlock);
        if (!bodyExit.isTerminated()) {
            addEdge(bodyExit, condBlock, EdgeKind.NORMAL, null);
        }

        loopStack.pop();

        return afterLoop;
    }

    /**
     * Routes evaluation of {@code expr} to caller-supplied true and false
     * targets. Logical operators are expanded recursively to preserve
     * short-circuit evaluation:
     *
     * <pre>
     * lhs &amp;&amp; rhs:
     *   from --lhs TRUE--> rhsBlock --rhs TRUE--> trueTarget
     *     |                   +------rhs FALSE--> falseTarget
     *     +------lhs FALSE----------------------> falseTarget
     *
     * lhs || rhs:
     *   from --lhs TRUE-------------------------> trueTarget
     *     +------lhs FALSE--> rhsBlock --rhs TRUE-> trueTarget
     *                           +------rhs FALSE-> falseTarget
     *
     * !value: build value with trueTarget and falseTarget exchanged
     * </pre>
     *
     * For a non-logical root, expression children are evaluated first so any
     * nested logical expressions get their own blocks. The resulting block is
     * then marked with {@code expr} and receives TRUE and FALSE outgoing edges.
     */
    private void buildCondition(AST.Expr expr,FlowBlock from,FlowBlock trueTarget,FlowBlock falseTarget) {
        if (expr instanceof AST.BinaryExpr bin) {
            String op = bin.op.str;

            if (op.equals("&&")) {
                FlowBlock rhsBlock = newBlock();

                buildCondition(bin.expr1, from, rhsBlock, falseTarget);
                buildCondition(bin.expr2, rhsBlock, trueTarget, falseTarget);
                return;
            }

            if (op.equals("||")) {
                FlowBlock rhsBlock = newBlock();

                buildCondition(bin.expr1, from, trueTarget, rhsBlock);
                buildCondition(bin.expr2, rhsBlock, trueTarget, falseTarget);
                return;
            }
        }

        if (expr instanceof AST.UnaryExpr un && un.op.str.equals("!")) {
            buildCondition(un.expr, from, falseTarget, trueTarget);
            return;
        }

        FlowBlock conditionBlock = buildExprChildren(expr, from);
        conditionBlock.condition = expr;
        addEdge(conditionBlock, trueTarget, EdgeKind.TRUE, expr);
        addEdge(conditionBlock, falseTarget, EdgeKind.FALSE, expr);
    }

    /**
     * Builds the control flow needed to evaluate an expression used as a value.
     * A logical expression has two short-circuit paths which rejoin once its
     * boolean value has been determined.
     */
    private FlowBlock buildExpr(AST.Expr expr, FlowBlock from) {
        if (isLogical(expr)) {
            FlowBlock trueBlock = newBlock();
            FlowBlock falseBlock = newBlock();
            FlowBlock afterExpr = newBlock();

            buildCondition(expr, from, trueBlock, falseBlock);
            addEdge(trueBlock, afterExpr, EdgeKind.NORMAL, null);
            addEdge(falseBlock, afterExpr, EdgeKind.NORMAL, null);
            return afterExpr;
        }

        return buildExprChildren(expr, from);
    }

    /**
     * Recurses through immediate expression children in evaluation order.
     */
    private FlowBlock buildExprChildren(AST.Expr expr, FlowBlock cur) {
        if (expr instanceof AST.BinaryExpr binary) {
            cur = buildExpr(binary.expr1, cur);
            return buildExpr(binary.expr2, cur);
        }

        if (expr instanceof AST.UnaryExpr unary) {
            return buildExpr(unary.expr, cur);
        }

        if (expr instanceof AST.ArrayStoreExpr store) {
            cur = buildExpr(store.array, cur);
            cur = buildExpr(store.expr, cur);
            return buildExpr(store.value, cur);
        }

        if (expr instanceof AST.ArrayLoadExpr load) {
            cur = buildExpr(load.array, cur);
            return buildExpr(load.expr, cur);
        }

        if (expr instanceof AST.SetFieldExpr set) {
            cur = buildExpr(set.object, cur);
            return buildExpr(set.value, cur);
        }

        if (expr instanceof AST.GetFieldExpr get) {
            return buildExpr(get.object, cur);
        }

        if (expr instanceof AST.CallExpr call) {
            cur = buildExpr(call.callee, cur);
            for (AST.Expr arg : call.args) {
                cur = buildExpr(arg, cur);
            }
            return cur;
        }

        if (expr instanceof AST.NewExpr newExpr) {
            if (newExpr.len != null) {
                cur = buildExpr(newExpr.len, cur);
            }
            if (newExpr.initValue != null) {
                cur = buildExpr(newExpr.initValue, cur);
            }
            return cur;
        }

        if (expr instanceof AST.InitExpr init) {
            cur = buildExpr(init.newExpr, cur);
            for (AST.Expr initializer : init.initExprList) {
                cur = buildExpr(initializer, cur);
            }
        }

        return cur;
    }

    private boolean isLogical(AST.Expr expr) {
        if (expr instanceof AST.BinaryExpr binary) {
            return binary.op.str.equals("&&") || binary.op.str.equals("||");
        }
        return expr instanceof AST.UnaryExpr unary && unary.op.str.equals("!");
    }
}
