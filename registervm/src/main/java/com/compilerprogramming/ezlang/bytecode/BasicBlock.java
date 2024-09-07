package com.compilerprogramming.ezlang.bytecode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class BasicBlock {
    public final int bid;
    public final boolean loopHead;
    public final List<BasicBlock> successors = new ArrayList<>(); // successors
    public final List<BasicBlock> predecessors = new ArrayList<>();
    public final List<Instruction> instructions = new ArrayList<>();

    public BasicBlock(int bid, boolean loopHead) {
        this.bid = bid;
        this.loopHead = loopHead;
    }
    public BasicBlock(int bid) {
        this(bid, false);
    }
    public void add(Instruction instruction) {
        instructions.add(instruction);
    }
    public void addSuccessor(BasicBlock successor) {
        successors.add(successor);
        successor.predecessors.add(this);
    }
    public static StringBuilder toStr(StringBuilder sb, BasicBlock bb, BitSet visited)
    {
        if (visited.get(bb.bid))
            return sb;
        visited.set(bb.bid);
        sb.append("L").append(bb.bid).append(":\n");
        for (Instruction n: bb.instructions) {
            sb.append("\t");
            n.toStr(sb).append("\n");
        }
        for (BasicBlock succ: bb.successors) {
            toStr(sb, succ, visited);
        }
        return sb;
    }
}
