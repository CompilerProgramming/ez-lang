# SSA Destruction Using Briggs

This document describes the algorithm implemented by
`com.compilerprogramming.ezlang.compiler.ExitSSABriggs`.

## Goal

The pass converts a `CompiledFunction` from SSA form back to ordinary
three-address form. It removes phi nodes by inserting the copy operations that
the phi nodes implied on predecessor edges.

The implementation is based on the SSA destruction algorithm described by
Preston Briggs et al. in "Practical Improvements to the Construction and
Destruction of Static Single Assignment Form". The original algorithm has two
main parts:

- Walk the dominator tree, replacing uses with any currently active temporary
  names.
- At each block, schedule the copies needed for phi nodes in successor blocks,
  avoiding the lost-copy and swap problems.

## Setup

`ExitSSABriggs` starts with an SSA function and computes the information needed
by the scheduler:

```text
require function.isSSA
compute liveness
tree <- DominatorTree(function.entry)
initialize one name stack per register
insertCopies(function.entry)
remove all phi instructions
function.isSSA <- false
```

Liveness is required because the scheduler must know whether overwriting a phi
destination would destroy a value that is still live out of the current
predecessor block.

The dominator tree determines the order of the recursive walk. Name stacks are
used only for temporary names created to preserve live phi destinations. When a
temporary is pushed for a register, dominated instructions that use that register
are rewritten to use the temporary until the walk leaves that dominated region.

## Dominator-Tree Walk

The method `insertCopies(block)` corresponds to the original
`insert_copies(block)` routine.

For each block:

1. Replace ordinary instruction uses.

   ```text
   for each instruction i in block
       replace each register use u with top(stacks[u]) when the stack is not empty
   ```

   Phi instructions are skipped here because their inputs are handled as
   predecessor-edge copies by `scheduleCopies`.

2. Schedule copies for successor phis.

   ```text
   scheduleCopies(block, pushed)
   ```

   `pushed` records the register ids whose stacks were extended while handling
   this block.

3. Recurse into dominated children.

   ```text
   for each child c of block in the dominator tree
       insertCopies(c)
   ```

4. Pop names pushed in this block.

   ```text
   for each name in pushed
       pop(stacks[name])
   ```

This preorder traversal makes any temporary introduced for a phi destination
visible to the blocks dominated by the phi's block, and only to those blocks.

## Copy Scheduling

`scheduleCopies(block, pushed)` implements the three scheduling passes from the
Briggs description. For the current predecessor `block`, it looks at every
successor `s` and every phi in `s`. If `block` is predecessor `j` of `s`, then
the phi's `j`th input is the source value that must be copied on this edge.

For a phi:

```text
dest = phi result in successor s
src  = phi input selected by predecessor index j
```

the conceptual edge copy is:

```text
dest <- src
```

In the implementation this pair is stored as a `CopyItem`, along with the
successor block where the phi is defined.

### Pass 1: Initialize Data Structures

Build the set of copies that this predecessor block must provide:

```text
copySet <- empty
map <- empty
usedByAnother <- empty

for each successor s of block
    j <- s.whichPred(block)

    for each phi in s
        dest <- phi.value
        src <- phi.input(j)

        copySet <- copySet union {(src, dest, s)}
        map[dest] <- dest

        if src is a register
            map[src] <- src
            usedByAnother[src] <- true
```

`map` tracks the current location of each register name as copies are emitted.
Initially, every register maps to itself. Later, when a copy or temporary changes
where a value lives, `map` is updated.

`usedByAnother` marks register sources that appear as inputs to other phi-copy
pairs. Constants do not participate in dependency cycles, so the implementation
records them in `copySet` but not in `usedByAnother` or `map`.

### Pass 2: Seed The Worklist

Copies whose destination is not needed as a source by another pending copy can be
emitted first:

```text
workList <- empty

for each copy (src, dest) in copySet
    if dest is not in usedByAnother
        move copy from copySet to workList
```

These copies cannot destroy a value that another pending copy still needs.

### Pass 3: Emit Copies

The scheduler repeatedly drains the worklist. For each copy `(src, dest)`:

1. Preserve a live destination if needed.

   ```text
   if dest is live-out of block
       t <- new temp
       insert "t = dest" after the phi that defines dest in the successor block
       push t on stacks[dest]
       remember dest in pushed
   ```

   This is the lost-copy protection. If `dest` already holds a value that is
   live after this edge, overwriting it in the predecessor would be incorrect.
   Saving `dest` to a temporary at the phi definition and pushing that temporary
   makes later dominated uses read the preserved value.

2. Insert the edge copy at the end of the predecessor block, before its final
   branch:

   ```text
   if src is a register
       insert "dest = map[src]" at the end of block
       map[src] <- dest
   else
       insert "dest = src" at the end of block
   ```

   The code supports integer, float, and null constants as phi inputs.

3. If the source register is itself the destination of another pending copy,
   move that pending copy to the worklist:

   ```text
   if src names a destination in copySet
       move that copy from copySet to workList
   ```

   This exposes the next copy whose dependency has just been satisfied.

If the worklist becomes empty but `copySet` is still non-empty, the remaining
copies form a cycle, such as a swap:

```text
a <- b
b <- a
```

To break the cycle, the implementation chooses one pending copy and saves its
destination at the end of the current block:

```text
t <- dest
map[dest] <- t
move the chosen copy to workList
```

The saved destination can now be overwritten safely, and subsequent copies read
the old value through `map[dest]`.

## Insertion Points

There are two insertion points:

- Edge copies are inserted at the end of the predecessor block, immediately
  before the final branch instruction.
- Lost-copy preservation moves are inserted immediately after the phi node that
  defines the destination in the successor block.

This differs slightly from a literal edge-splitting formulation, but it has the
same effect for this IR because the predecessor index identifies which phi input
belongs to the outgoing edge.

When a register edge copy is inserted just before a conditional branch, the
implementation also rewrites that branch if it still uses the copied source.
This local fix is not part of the Briggs pseudocode, but it keeps the branch
consistent with the newly inserted move sequence in this IR.

## Final Phi Removal

After the dominator-tree walk has scheduled every required copy, all phi
instructions are removed:

```text
for each block in dominator-tree order
    remove instructions that are Phi
```

The inserted moves now represent the data flow that the phi nodes previously
encoded. The pass then marks the function as no longer being in SSA form.

## Why The Scheduler Is Needed

Replacing each phi with naive predecessor copies can be wrong. Two classic
problems motivate Briggs' scheduler:

- Lost copy: a copy overwrites a phi destination even though the old value is
  still live and needed later.
- Swap problem: two or more copies depend on each other's old values, so any
  simple linear order destroys one of the inputs.

The live-out check handles lost copies by saving live destinations into
temporaries and rewriting dominated uses through the name stack. The cycle
handling handles swaps by saving one destination into a temporary and using
`map` to route later copies through that saved value.
