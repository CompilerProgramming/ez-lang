============================
Intermediate Representations
============================

An input program in the source language may go through many intermediate representations within
a compiler before it is in a form ready for execution.

One of the first such intermediate representations that we have seen is the
Abstract Syntax Tree (AST), which is mainly concerned with the grammar of the source language.

From the AST, we generate a different kind of intermediate representation, one that is more amenable
to the manipulations required during optimization and execution. There are many such representations; we will
limit ourselves to the following.

* Stack based IR
* Register based IR
* Sea of Nodes IR

Stack-Based IR
==============

The stack based IR encodes stack operations as part of the intermediate representation. Let's look at a simple
example::

   func foo(n: Int)->Int {
      return n+1;
   }

Produces::

   L0:
	   load 0
	   pushi 1
	   addi
	   jump L1
   L1:

The stack based IR is so called because many of the instructions in the IR push and pop values to/from an evaluation stack at
runtime. Above for example, we have the following instructions:

* ``load 0`` - this pushes the value of the input parameter ``n`` to the stack. The ``0`` here identifies the location of the variable ``n``.
* ``pushi 1`` - pushes the constant ``1`` to the stack.
* ``addi`` - pops the two topmost values on the stack, and computes the sum and pushes this to the stack

So at the end of the program we are left with the sum of ``n+1`` on the stack, and this forms the return
value of the function.

In this IR, control flow can be represented either using labels and branching instructions, or by grouping
instructions into basic blocks, and linking basic blocks through jump instructions. These two approaches are
equivalent, you can think of a label as indicating the start of a basic block, and a jump as ending
a basic block.

The idea is that inside a basic block, instructions execute linearly one after the other.
Each non-exit basic block normally ends with a branching instruction, something like a goto or a conditional jump.

Here is a simple example of input source code and the IR you might see::

   func foo()->Int
   {
      return 1 == 1 && 2 == 2
   }

This results in IR that may look like this::

   L0:
	   pushi 1
	   pushi 1
	   eq
	   cbr L2 L3
   L2:
	   pushi 2
	   pushi 2
	   eq
	   jump L4
   L3:
	   pushi 0
	   jump L4
   L4:
	   jump L1
   L1:

Each basic block begins with a label, which is just the unique name of the block.

* The ``jump`` instruction transfers control from a basic block to another.
* The ``cbr`` instruction is the conditional branch. It consumes the top most value from the stack,
  and if this value is true (in this case, a non-zero value), then control is transferred
  to the first block, else to the second block.
* The ``eq`` instruction pops the two topmost values from the stack, compares them and pushes a result:
  ``1`` for true or ``0`` for false.

Advantages
----------
* The IR is compact to represent in stored form as most instructions do not have operands.
  This is a reason why many languages choose to encode their compiled code in
  this form. Examples are Java, C#, Web Assembly.
* The IR can be executed easily by an Interpreter.
* Relatively easy to generate IR from an AST.

Disadvantages
-------------
* Not easy to implement optimizations.
* For a reader it is hard to trace values as they flow through instructions,
  as it requires tracking them through a conceptual stack.
* Harder to analyze the IR, although there are methods available to do so.

Examples
--------
* `Example implementation in EeZee Programming Language <../stackvm>`_.
* `Java Specifications <https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-6.html>`_.
* `Web Assembly Specifications <https://webassembly.github.io/spec/core/syntax/instructions.html>`_.

Register Based IR or Three-Address IR
=====================================

This intermediate representation uses named slots called virtual registers in the instruction when referencing
values. Let's look at the same example we saw above::

   func foo(n: Int)->Int {
      return n+1;
   }

Produces::

   L0:
      %t1 = n+1
      ret %t1
      goto  L1
   L1:

The instructions above are as follows:

* ``%t1 = n+1`` - is a typical three-address instruction of the form ``result = value1 operator value2``. The name ``%t1``
  refers to a temporary, whereas ``n`` refers to the input argument ``n``. Both of these names are virtual registers.
* ``ret %t1`` - is the return instruction, in this instance it references the temporary.

The virtual registers in the IR are so called because they are not necessarily registers in a target physical machine.
In the register VM and optimizing VM modules, they are named slots in the abstract machine responsible for executing the IR. The interpreter reads and writes these values through frame slots in the function's stack frame, so the IR references locations directly using virtual names rather than implicitly through push and pop instructions.

The optimizing VM can run register allocation to map many virtual registers onto a smaller set of VM frame slots when their lifetimes do not overlap. That is different from native code generation, where a later backend may map values to real hardware registers or stack locations.

Control flow is represented the same way as for the stack IR. Revisiting the same source example from above, we get following
IR::

   L0:
      %t0 = 1==1
      if %t0 goto L2 else goto L3
   L2:
      %t0 = 2==2
      goto  L4
   L3:
      %t0 = 0
      goto  L4
   L4:
      ret %t0
      goto  L1
   L1:


Advantages
----------
* Readability: the flow of values is easier to trace, whereas with a stack IR you need to conceptualize a stack somewhere,
  and track values being pushed and popped.
* Fewer instructions are needed compared to stack IR.
* The IR can be executed easily by an Interpreter.
* Most optimization algorithms can be applied to this form of IR.
* The IR can represent Static Single Assignment (SSA) in a natural way.

Disadvantages
-------------
* Each instruction has operands, hence representing the IR in serialized form takes more space.
* Harder to generate the IR during compilation.

Examples
--------
* `Example basic register IR in EeZee Programming Language <../registervm>`_.
* `Example register IR including SSA form and optimizations in EeZee Programming Language <../optvm>`_.
* `LLVM instruction set <https://llvm.org/docs/LangRef.html#instruction-reference>`_.
* `Android Dalvik IR <https://source.android.com/docs/core/runtime/dex-format>`_.

Sea of Nodes IR
===============
The final example we will look at is known as the Sea of Nodes IR.

This IR is quite different from the IRs we described above.

The key features of this IR are:

* Instructions are NOT organized into Basic Blocks - instead, instructions form a graph, where
  each instruction has as its inputs the definitions it uses.
* Instructions that produce data values are not directly bound to a Basic Block, instead they "float" around,
  the order being defined purely in terms of the dependencies between the instructions.
* Control flow is represented in a similar way, and control flows between control flow
  instructions. Dependencies between data instructions and control instructions occur at few well
  defined places.
* The IR as described above cannot be readily executed, because to execute the IR, the instructions
  must be scheduled; you can think of this as a process that puts the instructions into a traditional
  Basic Block IR as described earlier.

Describing Sea of Nodes IR is quite involved. For now, I direct you to the `Simple project <https://github.com/SeaOfNodes/Simple/tree/main>`_; this
is an ongoing effort to explain the Sea of Nodes IR representation and how to implement it.

Beyond how the IR is represented, the main benefits of the Sea of Nodes IR are that:

* It is an SSA IR
* Various optimizations such as peephole optimizations, value numbering and common subexpressions elimination,
  dead code elimination, occur as the IR is built.
* The SoN IR can generate optimized code quickly, suitable for Just-In-Time (JIT) compilers.
