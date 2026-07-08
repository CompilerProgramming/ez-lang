# Documentation

This folder contains language and compiler design notes for ez-lang.

## Language

- [EeZee Programming Language](ez-lang.rst) - Source language overview, including syntax, types, control flow, expressions, and grammar.

## Intermediate Representations

- [Intermediate Representations](ir-overview.rst) - Overview of the compiler IRs used in the project, including stack-based, register-based, and sea-of-nodes forms.
- [IR Design Instructions](ir-design-instructions.md) - Notes on designing linear IRs, including basic blocks, control flow graphs, and instruction organization.
- [Virtual Registers](ir-virtual-registers.md) - Design discussion for virtual register representation, source variable slots, temporaries, and register identity.

## Data-Flow And SSA

- [Liveness Analysis](liveness.md) - Description of the liveness sets and algorithm used by the optimizing VM.
- [SSA Construction](ssa-construction.md) - Explanation of SSA conversion, dominance-frontier phi placement, and register renaming.
- [SSA Destruction Using Briggs](ssa-destruction-briggs.md) - Explanation of the Briggs SSA destruction algorithm and how phi nodes are lowered to copies.
