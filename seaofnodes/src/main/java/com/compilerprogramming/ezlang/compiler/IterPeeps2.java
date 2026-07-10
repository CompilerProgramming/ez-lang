package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.compiler.codegen.CodeGen;
import com.compilerprogramming.ezlang.compiler.node.Node;
import com.compilerprogramming.ezlang.compiler.node.StopNode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

public class IterPeeps2 {

    static void dfs(Node root, Consumer<Node> consumer, BitSet visited) {
        visited.set(root._nid);
        /* For each successor node */
        for (int i = 0; i < root.nOuts(); i++) {
            Node S = root.out(i);
            if (S == null)
                continue;
            if (!visited.get(S._nid))
                dfs(S, consumer, visited);
        }
        consumer.accept(root);
    }

    public static List<Node> rpo(Node root) {
        List<Node> nodes = new ArrayList<>();
        // note add below prepends
        dfs(root, (n)->nodes.add(0,n), new BitSet());
        return nodes;
    }

    public void iterate(CodeGen code) {
        final Node startNode = code._start;    // We need the start node to get to the full graph
        int iter = 1;
        int count = 0;  // Count of nodes we peepholed
        for (;;iter++) {
            List<Node> rpos = rpo(startNode);
            count += rpos.size();
            boolean progress = false;
            for (int i = 0; i < rpos.size(); i++) {
                Node n = rpos.get(i);
                if (n.isDead()) continue;
                Node x = n.peepholeOpt();
                if (x != null) {
                    progress = true;
                    if (x.isDead()) continue;
                    // peepholeOpt can return brand-new nodes, needing an initial type set
                    if( x._type==null ) x.setType(x.compute());
                    if (x != n) n.subsume(x);
                }
                if( n.isUnused() && !(n instanceof StopNode) )
                    n.kill();       // Just plain dead
            }
            if (!progress) break;
        }
        System.out.println("Completed in " + iter + " iterations; peepholed " + count + " nodes");
//        if( show )
//            System.out.println(new GraphVisualizer().generateDotOutput(stopNode,null,null));
    }
}
