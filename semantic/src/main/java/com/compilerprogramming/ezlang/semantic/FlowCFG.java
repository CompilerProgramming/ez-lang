package com.compilerprogramming.ezlang.semantic;

import com.compilerprogramming.ezlang.parser.AST;

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
                return "B" + from.id + " -" + kind + "(" + condition + ")-> B" + to.id;
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
                    sb.append("  " + oneLine(s)).append('\n');
                }

                if (b.condition != null) {
                    sb.append("  condition: " + oneLine(b.condition)).append('\n');
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
                    sb.append("\\n").append(escape(oneLine(s)));
                }

                if (b.condition != null) {
                    sb.append("\\n? ").append(escape(oneLine(b.condition)));
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

        /*
         * Ordinary non-branching statements are accumulated into the current block:
         * AssignStmt, VarStmt, ExprStmt, VarDeclStmt, etc.
         */
        cur.statements.add(stmt);
        return cur;
    }

    private FlowBlock buildBlock(AST.BlockStmt block, FlowBlock cur) {
        for (AST.Stmt stmt : block.stmtList) {
            cur = buildStmt(stmt, cur);
        }
        return cur;
    }

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

    private void buildCondition(
            AST.Expr expr,
            FlowBlock from,
            FlowBlock trueTarget,
            FlowBlock falseTarget
    ) {
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

        from.condition = expr;
        addEdge(from, trueTarget, EdgeKind.TRUE, expr);
        addEdge(from, falseTarget, EdgeKind.FALSE, expr);
    }
}