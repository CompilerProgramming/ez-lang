package com.compilerprogramming.ezlang.compiler;

import java.util.EnumSet;

public enum Options {
    ISSA, // Incremental SSA
    OPTIMIZE,
    SSA_DESTRUCTION_BRIGGS,
    SSA_DESTRUCTION_BOISSINOT_NOCOALESCE,
    SCCP,
    CCP, // constant comparison propagation
    REGALLOC,
    LOWER_SHORT_CIRCUIT,    // Lower boolean && || operators to if blocks, used for testing and SON
    DUMP_INITIAL_IR,
    DUMP_PRE_SSA_DOMTREE,
    DUMP_PRE_SSA_DOMFRONTIERS,
    DUMP_PRE_SSA_LIVENESS,
    DUMP_SSA_IR,
    DUMP_SCCP_PREAPPLY,
    DUMP_SCCP_POSTAPPLY,
    DUMP_CCP_POSTAPPLY,
    DUMP_SSA_LIVENESS,
    DUMP_SSA_DOMTREE,
    DUMP_SSA_TO_CSSA,
    DUMP_CSSA_PHI_REMOVAL,
    DUMP_POST_SSA_IR,
    DUMP_INTERFERENCE_GRAPH,
    DUMP_CHAITIN_COALESCE,
    DUMP_POST_CHAITIN_IR;

    public static final EnumSet<Options> NONE = EnumSet.noneOf(Options.class);
    public static final EnumSet<Options> OPT = EnumSet.of(Options.OPTIMIZE,Options.SCCP,Options.CCP,Options.REGALLOC);
    public static final EnumSet<Options> OPT_B = EnumSet.of(Options.OPTIMIZE,Options.SCCP,Options.CCP,Options.REGALLOC,Options.SSA_DESTRUCTION_BOISSINOT_NOCOALESCE);
    public static final EnumSet<Options> OPT_ISSA = EnumSet.of(Options.OPTIMIZE,Options.ISSA,Options.SCCP,Options.CCP,Options.REGALLOC);
    public static final EnumSet<Options> OPT_ISSA_B = EnumSet.of(Options.OPTIMIZE,Options.ISSA,Options.SCCP,Options.CCP,Options.REGALLOC,Options.SSA_DESTRUCTION_BOISSINOT_NOCOALESCE);
    public static final EnumSet<Options> VERBOSE = EnumSet.range(DUMP_INITIAL_IR, DUMP_POST_CHAITIN_IR);
    public static final EnumSet<Options> OPT_VERBOSE = EnumSet.range(OPTIMIZE, DUMP_POST_CHAITIN_IR);
}
