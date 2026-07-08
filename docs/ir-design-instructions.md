This is part of the series that discusses what you must think about when deciding how to represent IRs.

* [Part 1 - Virtual Registers](ir-virtual-registers.md)

# Linear IR

The discussion here is primarily for linear Intermediate Representations. By linear we mean that the instructions are organized into sequences that are logically executed one after the other. This is in contrast to graph IRs where instructions are organized as a graph and the order of execution is not sufficiently defined by the graph.

# Organize Instructions Into Basic Blocks

For many algorithms we need to operate on the control flow graph. It is important to organize the instructions into Basic Blocks, where each BB encapsulates instructions that are executed sequentially. In this implementation, non-exit blocks are normally terminated by a jump or branch to another BB.

```java
public class BasicBlock {
    List<Instruction> instructions;
}
```

# Control Flow Graph

The Basic Blocks should contain links to predecessor and successor BBs - this forms the Control Flow graph. The links are driven by the jump instructions that terminate each BB.

```java
public class BasicBlock {
    List<Instruction> instructions;
    List<BasicBlock> successors;
    List<BasicBlock> predecessors;
}
```

# Instruction Operands

Instructions deal with values, some of those may be Virtual Registers and some Constants. We therefore create a type called Operand that represents values handled by an Instruction. Operands can have different subtypes in an OO design (or an Operand can be a union or sumtype in non-OO languages) - where each subtype is for a specific type of value.

Control-flow targets are modeled separately. For example, `Instruction.Jump` and `Instruction.ConditionalBranch` store their target `BasicBlock`s as instruction fields, while register and constant values appear in the instruction's operand list.

```java
class Operand {}
class RegisterOperand extends Operand {}
class IntConstantOperand extends Operand {}
class FloatConstantOperand extends Operand {}
class NullConstantOperand extends Operand {}
```

# Instructions Define and Use Registers

From the optimization point of view, it is important to model the Instruction in such a way that we can easily handle following requirements:

* Does the instruction define a Register - i.e. assigns a value to it?
* Does it use Registers defined by other instructions
* Replace a register that is defined
* Replace a specific use of a Register - or replace all uses - a Register may be replaced by another Register or by something else such as a Constant.

Whether an Instruction defines a Register and/or uses Values including Registers depends on the Instruction type. So each Instruction type sets its own requirements. Most non-phi instructions satisfy the interface requirements above, so that optimizers can treat them uniformly. Phi instructions and temporary parallel-copy instructions are deliberately special-cased; see below.

```java
class Instruction {
    // Does this instruction define a value?
    boolean definesVar();
    // Get the defined value
    Register def();
    // Get register uses
    List<Register> uses();
    // Replace register that is defined
    void replaceDef(Register newReg);
    // Replace uses
    void replaceUses(Register[] newUses);
    // Replace specific use
    boolean replaceUse(Register oldUse, Register newUse);
    // Replace specific register with constant
    void replaceUseWithConstant(Register register, Operand constantOperand);
}
```

# Phis Are Special

Phi instructions also define a Register and use one or more Registers, but Phis do not have the same semantics as other instructions in many situations, such as when computing Liveness. So a consideration is how to model Phi instructions, whether they obey the general interface or have their own specialized interface. In EeZee language we give Phi instructions their own interface, which is similar to the general instruction but ensures that Phis cannot be erroneously manipulated by the optimizer. This has a trade off because there are scenarios where Phis do not require the special treatment.

In the implementation, `Phi.def()`, `Phi.replaceDef()`, `Phi.replaceUses()`, and `Phi.replaceUse()` throw `UnsupportedOperationException`, and `Phi.uses()` returns an empty list. Code that needs phi definitions or inputs must use the phi-specific methods.

```java
class Phi extends Instruction {
        // def = value
        Register value();
        void replaceValue(Register newReg);

        // input = use
        int numInputs();
        Operand input(int i);
        Register inputAsRegister(int i);
        boolean isRegisterInput(int i);
        Register[] inputRegisters();
        void replaceInput(int i, Register newReg);
        boolean replaceInput(Register oldReg, Register newReg);
        void addInput(Register register);
        void removeInput(int i);
}
```

# Parallel Copies Are Temporary

SSA destruction can temporarily introduce `ParallelCopyInstruction`. Like phi nodes, parallel copies do not support the ordinary def/use interface. They are sequenced into ordinary move instructions before the pass finishes.

# Instructions Can Be Replaced

The optimizer must be able to:

* Identify an instruction
* Replace it with a new Instruction
* Or delete it

Thus every Instruction must have an identity. It is also helpful for an Instruction to know which BB it lives in.

# Define Data Structures

The compiler must be able to operate and manipulate BBs, Instructions,  Operands and Registers - so it is important to model these as data structures, and not encode them into a serialized form until the end. Some languages such as Lua directly encode instructions - this is only suitable for single pass compilers that do not do optimizations.

# Def Use Chains

Def use chains link Instructions to Registers - in SSA form, each Register can have a set of Instructions that use it. In non-SSA form, a Register may have multiple definitions and each definition its own set of Uses. Think how this will be handled.

# Walking Basic Blocks

Basic Blocks may need to be traversed in specific order such as Reverse Post Order (RPO). To support this auxiliary information has to be recorded for each Basic Block.

# Dominator Trees and Dominance Frontiers

The optimizer will most likely need to compute and maintain Dominator Trees and Dominance Frontiers. The algorithms that compute these and the data structures for maintaining this information will typically be attached to Basic Blocks.

It is useful for Basic Blocks to be assigned a unique ID from a densely packed sequence of integers, this makes for a good way to generate names for the BBs, but also can be used to key into auxiliary data structures.

```java
public class BasicBlock {
    /**
     * Unique BB ID
     */
    int bid;

    /**
     * The preorder traversal number.
     */
    int pre;
    /**
     * The depth of the BB in the dominator tree
     */
    int domDepth;
    /**
     * Reverse post order traversal number.
     */
    int rpo;
    /**
     * Immediate dominator is the closest strict dominator.
     */
    BasicBlock idom;
    /**
     * Nodes for whom this node is the immediate dominator,
     * thus the dominator tree.
     */
    List<BasicBlock> dominatedChildren;
    /**
     * Dominance frontier
     */
    Set<BasicBlock> dominationFrontier;
}
```
