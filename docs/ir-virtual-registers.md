# Introduction

Compilers often use named slots to represent locations where a value will be stored. These named slots are virtual in the sense that they model an abstract machine with unlimited registers. However these virtual registers are eventually mapped to a CPU register or to a location in the function's stack frame.

In our compiler we use the term Register to denote such a virtual slot.

Instructions can define a Register, and/or use one or more Registers. Defining means assigning a value to a Register.

# Design Considerations

There are several requirements we need to meet when deciding how to represent these virtual registers.

## Each Source Variable Declaration Gets New Register

The first requirement is that every variable declared in a function must get a unique slot. In the source language a variable name may be reused in multiple scopes, but from a compiler's perspective, each variable declaration is unique, because each has a unique lifetime and type.

Consider this example:

```
func foo(x: Int)
{
  if (x == 0)
  {
    var i = 0
  }
  if (x == 1)
  {
    var i = 1
  }
}
```
The variable `i` has two defined instances, each has a different lifetime. From a compiler's standpoint, each instance of `i` must be mapped to a distinct virtual register.

## Each Virtual Register Eventually Gets Assigned To A Potentially Shared Physical Slot

The second requirement is that although each virtual register is unique - multiple virtual registers may get mapped to the same physical slot eventually. If we are targeting a CPU then this final location may be a CPU register or a memory location in the function's stack frame. In optvm module, we target an abstract virtual machine, a VM that is. This abstract machine supports unlimited number of virtual registers per function. Each such register is a slot in the function's stack frame. We call this a `frameSlot` in the implementation.

So in the above example, after compiling the two virtual registers that represent `i` may end up pointing to the same `frameSlot`.

| source variable | virtual register ID | frame slot |
| --------------- | ------------------- | ---------- |
| i               | 1                   | 0          |
| i               | 2                   | 0          |

Why does this happen? For efficiency and better use of memory - if two variables do not overlap in their lifetimes, they can share the same slot. More about this when we discuss Register Allocation, in a future write-up.

## In SSA Form A Register May Be Versioned

Consider this example:

```
func foo(x: Int)->Int
{
  var y = 0
  if (x == 1)
  {
    y = 5
  }
  return y
}
```

Since this function has a single declaration of the variable `y` initially this variable gets a unique virtual Register slot.

But after SSA form this function looks something like this:

```
func foo(x: Int)->Int
{
  var y = 0
  var y1: Int
  if (x == 1)
  {
    y1 = 5
  }
  var y2 = phi(y,y1)
  return y2
}
```

We now have the original virtual Register `y` but also two new versions of this: `y1` and `y2`.

The requirement is that we need to be able to tell that these are all versions of `y`.

To support this we use a specialized type of virtual Register called SSARegister. Here is how this might look for above example:

| name   | register type  | virtual register ID | frame slot | original Reg Number | SSA version |
| ------ | -------------- | ------------------- | ---------- | ------------------- | ----------- |
| y      | Virtual        | 1                   | 1          | NA                  | NA          |
| y1     | SSA            | 2                   | -1         | 1                   | 1           |
| y2     | SSA            | 3                   | -1         | 1                   | 2           |

Observe following:

* The name of the SSA register is a concatenation of the original name with SSA version.
* The SSA register's original Reg Number refers back to the register ID for `y`.
* The frame slot is set to -1 on SSA registers - SSA registers are converted to regular registers when the compiler exits SSA. See below on life cycle of the frame slot.
* The register ID is always unique per register.

The compiler can treat each Register as unique by referring to the register ID, or ignore versions and treat all three registers as part of the same underlying name.

### Life Cycle of the Frame Slot

| Stage    | Register type | Frame Slot  |
| -------- | ------------- | ----------  |
| Pre-SSA  | Virtual       | Same as Register ID |
| SSA      | Virtual       | Same as Register ID |
| SSA      | SSA Register  | -1                  |
| Post Register Allocation | Virtual | Slot assigned by Register Allocator |

## Each Register Has Unique Integer ID

It is useful to assign each Register an ID from a densely packed sequence of integers. This enables using the Register's ID as a bit in a bitset for various calculations, for example, in the Liveness calculation or building of Interference Graph.

## Def Use Chains

If a compiler only ever uses SSA form then we can attach the Def-Use chain for each Register to the Register itself.

In the EeZee compiler we would like to support optimizations on non-SSA form as well as SSA form. Hence we cannot assume that there will only be a single definition for a Register. We therefore maintain Def Use Chains independently of Registers.

# Comparison with LLVM

LLVM IR has a similar concept to our Register - its called a Value. But there are some differences:

* LLVM IR is always SSA.
* LLVM Instructions are also Values, whereas in EeZee compiler, Instructions and Registers do not share a common base type.
* Actually, LLVM uses Value as the base type for many different types of entities.

# Comparison with Simple

In Simple, which is a Sea of Nodes implementation, instructions are represented as Nodes. Some instructions
result in values. An instance of a Node represents the value of the Instruction, the class of the Node gives the operation type of the Instruction. Nodes are always SSA, therefore, Simple maintains Def Use chains directly on Nodes.

# Implementation Details

The class definitions are shown below. Some fields have been excluded for clarity:

```java
public class Register {
    public final int id;
    protected final String name;
    protected int frameSlot;

    public int nonSSAId() {
        return id;
    }

    public static class SSARegister extends Register {
        public final int ssaVersion;
        public final int originalRegNumber;
        public SSARegister(Register original, int id, int version) {
            super(id, original.name+"_"+version, original.type, -1);
            this.originalRegNumber = original.id;
            this.ssaVersion = version;
        }
        @Override
        public int nonSSAId() {
            return originalRegNumber;
        }
    }
}
```

* The method named `nonSSAId()` is used by the compiler when it needs to ignore the SSA version.

# See Also

* [IR Design - Instructions](https://github.com/CompilerProgramming/ez-lang/wiki/IR-Design-%E2%80%90-Instructions)