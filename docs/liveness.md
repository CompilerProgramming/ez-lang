# Liveness Analysis

This document describes the algorithm implemented by
`com.compilerprogramming.ezlang.compiler.Liveness`.

## Goal

For every reachable basic block, the implementation computes these sets:

- `UEVar`: registers used before any local definition in the block.
- `varKill`: registers defined in the block.
- `phiDefs`: registers defined by leading phi nodes in the block.
- `phiUses`: registers used by successor phis on outgoing edges from this block.
- `liveIn`: registers live at block entry.
- `liveOut`: registers live at block exit.

The implementation supports ordinary CFG liveness and adds special handling
for SSA phi semantics.

## Algorithm

1. Collect reachable blocks from the function entry using a postorder traversal
   of the forward CFG.

2. Allocate empty `LiveSet` instances for every block. Each set is sized from
   the function's register pool:

   ```text
   UEVar, varKill, liveIn, liveOut, phiUses, phiDefs
   ```

3. Initialize local block sets.

   First, scan leading phi instructions in each block. For every phi:

   ```text
   x = phi(...)
   ```

   add `x` to `block.phiDefs`.

   Then scan all instructions in the block.

   For phi instructions, each input is treated as live on the corresponding
   predecessor edge, not live-in to the phi's own block. For each phi input at
   index `i`:

   ```text
   pred = block.predecessors[i]
   pred.phiUses += phi.input[i]
   ```

   There is one special case: if the predecessor is the same block and the input
   is also a phi definition in that block, the implementation skips adding it to
   `phiUses`. This is intended to avoid over-marking same-block phi cycles.

   For non-phi instructions, standard upward-exposed use/def collection is
   performed:

   ```text
   for each use in instruction.uses:
       if use not in block.varKill:
           block.UEVar += use

   if instruction defines a register:
       block.varKill += def
   ```

4. Iterate to a fixed point.

   Repeatedly recompute each block's `liveOut` and `liveIn` until no `liveIn`
   set changes.

   The live-out formula is:

   ```text
   liveOut(B) =
       union over successors S of (liveIn(S) - phiDefs(S))
       union phiUses(B)
   ```

   This means successor phi definitions are not considered live-out of the
   predecessor, while phi inputs on predecessor edges are.

   The live-in formula is:

   ```text
   liveIn(B) =
       phiDefs(B)
       union UEVar(B)
       union (liveOut(B) - varKill(B))
   ```

   This means phi results are considered live at entry to their block, normal
   upward-exposed uses are live at entry, and values live-out remain live-in
   unless killed by a definition inside the block.

5. Store the final sets directly on each `BasicBlock`, and mark the function as
   having liveness information.

## Phi Semantics

The implementation models a phi as if edge copies existed:

```text
B1 -> B3: x3 = x1
B2 -> B3: x3 = x2
B3:
    x3 = phi(x1, x2)
```

So:

- `x1` is live-out of `B1`, but not live-in to `B3` because of the phi.
- `x2` is live-out of `B2`, but not live-in to `B3`.
- `x3` is live-in to `B3`, but not live-out of `B1` or `B2` just because of the
  phi definition.

This is the purpose of splitting phi information into `phiUses` and `phiDefs`.
