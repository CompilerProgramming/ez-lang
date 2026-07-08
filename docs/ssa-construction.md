# SSA Construction

This document describes the algorithm implemented by
`com.compilerprogramming.ezlang.compiler.EnterSSA`.

## Goal

The pass transforms a non-SSA `CompiledFunction` into SSA form. It inserts phi
nodes where multiple definitions of the same original register can meet, then
renames every definition and use so that each SSA register has exactly one
definition.

The implementation follows the usual dominance-frontier construction described
by Briggs et al., with an additional liveness check during phi placement. In this
codebase, source variables and temporaries are represented by `Register`
instances. During SSA construction, each version is represented by an
`SSARegister` that keeps the original register id as its `nonSSAId()`.

## Data Structures

- `domTree`: dominator tree and dominance frontiers for the reachable CFG.
- `blocks`: reachable basic blocks in reverse postorder.
- `nonLocalNames`: original registers used before a local definition in at
  least one block.
- `blockSets`: for each original register, the set of blocks that define it.
- `counters`: the next SSA version number for each original register.
- `stacks`: the current SSA name stack for each original register.

## Algorithm

1. Compute dominance information.

   ```text
   domTree <- DominatorTree(function.entry)
   blocks <- domTree.blocks
   ```

   The dominator tree also computes the dominance frontier for each reachable
   block.

2. Find non-local names and definition blocks.

   For each block, scan instructions from top to bottom with a block-local
   `varKill` set:

   ```text
   for each block in blocks
       varKill <- empty set

       for each instruction in block
           for each used register r
               if r is not in varKill
                   nonLocalNames[r] <- r

           if instruction defines register r
               add r to varKill
               add block to blockSets[r]
   ```

   A register is non-local if some block can use it before defining it locally.
   Registers that are only used after local definitions do not need phi nodes.

3. Compute liveness.

   ```text
   Liveness(function)
   ```

   Phi placement uses `liveIn` to avoid inserting phi nodes for values that are
   dead at a join block.

4. Insert phi nodes.

   For each non-local register `v`, start with the blocks that define `v`. Walk
   the iterated dominance frontier with a worklist:

   ```text
   for each non-local register v
       visited <- empty set
       worklist <- blockSets[v]

       while worklist is not empty
           b <- pop(worklist)
           mark b as visited

           for each d in dominanceFrontier(b)
               if v is live-in at d
                   insert phi for v in d unless one already exists

                   if d has not been visited
                       push d onto worklist
   ```

   The inserted phi initially has the form:

   ```text
   v = phi(v, v, ...)
   ```

   with one input per predecessor. The rename pass rewrites the result and all
   inputs to the appropriate SSA versions.

5. Initialize version state.

   ```text
   for each original register v
       counters[v] <- 0
       stacks[v] <- empty stack
   ```

6. Rename variables by walking the dominator tree.

   ```text
   search(function.entry)
   ```

7. Mark the function as SSA and invalidate old liveness.

   ```text
   function.isSSA <- true
   function.hasLiveness <- false
   ```

## Rename Search

`search(block)` performs a preorder walk of the dominator tree. The stack for an
original register always contains the SSA name visible at the current program
point.

1. Rename phi results.

   ```text
   for each phi v = phi(...) in block
       v_i <- makeVersion(v)
       replace phi result with v_i
   ```

   `makeVersion(v)` creates a new `SSARegister`, pushes it onto `stacks[v]`,
   and increments `counters[v]`.

2. Rename ordinary instruction uses, then definitions.

   ```text
   for each non-phi instruction in block
       for each used register x
           replace x with top(stacks[x])

       if instruction defines register v
           v_i <- makeVersion(v)
           replace definition with v_i
   ```

   Uses are rewritten before the instruction definition is renamed, so an
   instruction such as `x = x + 1` reads the old visible version of `x` and
   defines a new version.

3. Fill phi inputs in successor blocks.

   ```text
   for each successor s of block
       j <- s.whichPred(block)

       for each phi in s
           v <- phi input j
           replace input j with top(stacks[v])
   ```

   The predecessor index selects the phi operand that corresponds to the edge
   from the current block to the successor.

4. Recurse into dominated children.

   ```text
   for each child c of block in the dominator tree
       search(c)
   ```

5. Pop definitions introduced in this block.

   ```text
   for each instruction in block
       if instruction is a phi or defines a register v
           pop(stacks[v])
   ```

   Popping restores the visible name from the dominating scope.

## Notes

The class comment describes this pass as semi-pruned SSA. The implementation also
uses block liveness during phi placement, so it performs the pruned-style check:

```text
insert phi for v in d only if v is live-in at d
```

This keeps dead phi nodes out of the IR and avoids rename failures caused by phi
inputs with no available reaching definition.
