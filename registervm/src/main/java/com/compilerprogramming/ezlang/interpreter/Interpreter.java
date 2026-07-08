package com.compilerprogramming.ezlang.interpreter;

import com.compilerprogramming.ezlang.compiler.BasicBlock;
import com.compilerprogramming.ezlang.compiler.CompiledFunction;
import com.compilerprogramming.ezlang.compiler.Instruction;
import com.compilerprogramming.ezlang.compiler.Operand;
import com.compilerprogramming.ezlang.exceptions.InterpreterException;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

public class Interpreter {

    TypeDictionary typeDictionary;

    public Interpreter(TypeDictionary typeDictionary) {
        this.typeDictionary = typeDictionary;
    }

    public Value run(String functionName) {
        Symbol symbol = typeDictionary.lookup(functionName);
        if (symbol instanceof Symbol.FunctionTypeSymbol functionSymbol) {
            Frame frame = new Frame(functionSymbol);
            ExecutionStack execStack = new ExecutionStack(1024);
            return interpret(execStack, frame);
        }
        else {
            throw new InterpreterException("Unknown function: " + functionName);
        }
    }

    public Value interpret(ExecutionStack execStack, Frame frame) {
        CompiledFunction currentFunction = frame.bytecodeFunction;
        BasicBlock currentBlock = currentFunction.entry;
        int ip = -1;
        int base = frame.base;
        boolean done = false;
        Value returnValue = null;

        while (!done) {
            Instruction instruction;

            ip++;
            instruction = currentBlock.instructions.get(ip);
            switch (instruction) {
                case Instruction.Ret retInst -> {
                    if (retInst.value() instanceof Operand.IntConstantOperand intConstantOperand) {
                        execStack.stack[base] = new Value.IntegerValue(intConstantOperand.value);
                    }
                    else if (retInst.value() instanceof Operand.FloatConstantOperand constantOperand) {
                        execStack.stack[base] = new Value.FloatValue(constantOperand.value);
                    }
                    else if (retInst.value() instanceof Operand.NullConstantOperand) {
                        execStack.stack[base] = new Value.NullValue();
                    }
                    else if (retInst.value() instanceof Operand.RegisterOperand registerOperand) {
                        execStack.stack[base] = execStack.stack[base+registerOperand.frameSlot()];
                    }
                    else throw new IllegalStateException();
                    returnValue = execStack.stack[base];
                }

                case Instruction.Move moveInst -> {
                    if (moveInst.to() instanceof Operand.RegisterOperand toReg) {
                        if (moveInst.from() instanceof Operand.RegisterOperand fromReg) {
                            execStack.stack[base + toReg.frameSlot()] = execStack.stack[base + fromReg.frameSlot()];
                        }
                        else if (moveInst.from() instanceof Operand.IntConstantOperand intConstantOperand) {
                            execStack.stack[base + toReg.frameSlot()] = new Value.IntegerValue(intConstantOperand.value);
                        }
                        else if (moveInst.from() instanceof Operand.FloatConstantOperand constantOperand) {
                            execStack.stack[base + toReg.frameSlot()] = new Value.FloatValue(constantOperand.value);
                        }
                        else if (moveInst.from() instanceof Operand.NullConstantOperand) {
                            execStack.stack[base + toReg.frameSlot()] = new Value.NullValue();
                        }
                        else throw new IllegalStateException();
                    }
                    else throw new IllegalStateException();
                }
                case Instruction.Jump jumpInst -> {
                    currentBlock = jumpInst.jumpTo;
                    ip = -1;
                    if (currentBlock == currentFunction.exit)
                        done = true;
                }
                case Instruction.ConditionalBranch cbrInst -> {
                    boolean condition;
                    if (cbrInst.condition() instanceof Operand.RegisterOperand registerOperand) {
                        Value value = execStack.stack[base + registerOperand.frameSlot()];
                        if (value instanceof Value.IntegerValue integerValue) {
                            condition = integerValue.value != 0;
                        }
                        else {
                            throw new InterpreterException("Condition expression must be Int type");
                        }
                    }
                    else if (cbrInst.condition() instanceof Operand.IntConstantOperand intConstantOperand) {
                        condition = intConstantOperand.value != 0;
                    }
                    else if (cbrInst.condition() instanceof Operand.FloatConstantOperand) {
                        throw new InterpreterException("Condition expression must be Int type");
                    }
                    else throw new IllegalStateException();
                    if (condition)
                        currentBlock = cbrInst.trueBlock;
                    else
                        currentBlock = cbrInst.falseBlock;
                    ip = -1;
                    if (currentBlock == currentFunction.exit)
                        done = true;
                }
                case Instruction.Call callInst -> {
                    // Copy args to new frame
                    int baseReg = base+currentFunction.frameSize();
                    int reg = baseReg;
                    for (Operand arg: callInst.args()) {
                        if (arg instanceof Operand.RegisterOperand param) {
                            execStack.stack[reg] = execStack.stack[base + param.frameSlot()];
                        }
                        else if (arg instanceof Operand.IntConstantOperand intConstantOperand) {
                            execStack.stack[reg] = new Value.IntegerValue(intConstantOperand.value);
                        }
                        else if (arg instanceof Operand.FloatConstantOperand constantOperand) {
                            execStack.stack[reg] = new Value.FloatValue(constantOperand.value);
                        }
                        else if (arg instanceof Operand.NullConstantOperand) {
                            execStack.stack[reg] = new Value.NullValue();
                        }
                        reg += 1;
                    }
                    // Call function
                    Frame newFrame = new Frame(frame, baseReg, callInst.callee);
                    interpret(execStack, newFrame);
                    // Copy return value in expected location
                    if (!(callInst.callee.returnType instanceof EZType.EZTypeVoid)) {
                        execStack.stack[base + callInst.returnOperand().frameSlot()] = execStack.stack[baseReg];
                    }
                }
                case Instruction.Unary unaryInst -> {
                    // We don't expect constant here because we fold constants in unary expressions
                    Operand.RegisterOperand unaryOperand = (Operand.RegisterOperand) unaryInst.operand();
                    Value unaryValue = execStack.stack[base + unaryOperand.frameSlot()];
                    if (unaryValue instanceof Value.IntegerValue integerValue) {
                        switch (unaryInst.unop) {
                            case "-": execStack.stack[base + unaryInst.result().frameSlot()] = new Value.IntegerValue(-integerValue.value); break;
                            // Maybe below we should explicitly set Int
                            case "!": execStack.stack[base + unaryInst.result().frameSlot()] = new Value.IntegerValue(integerValue.value==0?1:0); break;
                            default: throw new InterpreterException("Invalid unary op");
                        }
                    }
                    else if (unaryValue instanceof Value.FloatValue floatValue && unaryInst.unop.equals("-")) {
                        execStack.stack[base + unaryInst.result().frameSlot()] = new Value.FloatValue(-floatValue.value);
                    }
                    else if (unaryValue instanceof Value.ArrayValue arrayValue && unaryInst.unop.equals("#")) {
                        execStack.stack[base + unaryInst.result().frameSlot()] = new Value.IntegerValue(arrayValue.values.size());
                    }
                    else
                        throw new IllegalStateException("Unexpected unary operand: " + unaryOperand);
                }
                case Instruction.Binary binaryInst -> {
                    long value = 0;
                    Value leftValue = valueFromOperand(binaryInst.left(), execStack, base);
                    Value rightValue = valueFromOperand(binaryInst.right(), execStack, base);
                    if ((leftValue instanceof Value.NullValue || rightValue instanceof Value.NullValue) &&
                            (binaryInst.binOp.equals("==") || binaryInst.binOp.equals("!="))) {
                        boolean equal = leftValue instanceof Value.NullValue && rightValue instanceof Value.NullValue;
                        value = switch (binaryInst.binOp) {
                            case "==" -> equal ? 1 : 0;
                            case "!=" -> equal ? 0 : 1;
                            default -> throw new IllegalStateException();
                        };
                        execStack.stack[base + binaryInst.result().frameSlot()] = new Value.IntegerValue(value);
                    }
                    else {
                        if (leftValue instanceof Value.FloatValue left && rightValue instanceof Value.FloatValue right) {
                            double x = left.value;
                            double y = right.value;
                            switch (binaryInst.binOp) {
                                case "+" -> execStack.stack[base + binaryInst.result().frameSlot()] = new Value.FloatValue(x + y);
                                case "-" -> execStack.stack[base + binaryInst.result().frameSlot()] = new Value.FloatValue(x - y);
                                case "*" -> execStack.stack[base + binaryInst.result().frameSlot()] = new Value.FloatValue(x * y);
                                case "/" -> execStack.stack[base + binaryInst.result().frameSlot()] = new Value.FloatValue(x / y);
                                case "==" -> value = x == y ? 1 : 0;
                                case "!=" -> value = x != y ? 1 : 0;
                                case "<" -> value = x < y ? 1 : 0;
                                case ">" -> value = x > y ? 1 : 0;
                                case "<=" -> value = x <= y ? 1 : 0;
                                case ">=" -> value = x >= y ? 1 : 0;
                                default -> throw new IllegalStateException();
                            }
                            if (binaryInst.binOp.equals("==") || binaryInst.binOp.equals("!=") || binaryInst.binOp.equals("<") || binaryInst.binOp.equals(">") || binaryInst.binOp.equals("<=") || binaryInst.binOp.equals(">="))
                                execStack.stack[base + binaryInst.result().frameSlot()] = new Value.IntegerValue(value);
                        }
                        else if (leftValue instanceof Value.IntegerValue left && rightValue instanceof Value.IntegerValue right) {
                            long x = left.value;
                            long y = right.value;
                            switch (binaryInst.binOp) {
                                case "+": value = x + y; break;
                                case "-": value = x - y; break;
                                case "*": value = x * y; break;
                                case "/": value = x / y; break;
                                case "%": value = x % y; break;
                                case "==": value = x == y ? 1 : 0; break;
                                case "!=": value = x != y ? 1 : 0; break;
                                case "<": value = x < y ? 1: 0; break;
                                case ">": value = x > y ? 1 : 0; break;
                                case "<=": value = x <= y ? 1 : 0; break;
                                case ">=": value = x >= y ? 1 : 0; break;
                                default: throw new IllegalStateException();
                            }
                            execStack.stack[base + binaryInst.result().frameSlot()] = new Value.IntegerValue(value);
                        }
                        else throw new IllegalStateException();
                    }
                }
                case Instruction.NewArray newArrayInst -> {
                    long size = 0;
                    Value initValue = null;
                    if (newArrayInst.len() instanceof Operand.IntConstantOperand intConstantOperand)
                        size = intConstantOperand.value;
                    else if (newArrayInst.len() instanceof Operand.RegisterOperand registerOperand) {
                        Value.IntegerValue indexValue = (Value.IntegerValue) execStack.stack[base + registerOperand.frameSlot()];
                        size = (long) indexValue.value;
                    }
                    if (newArrayInst.initValue() != null)
                        initValue = valueFromOperand(newArrayInst.initValue(), execStack, base);
                    execStack.stack[base + newArrayInst.destOperand().frameSlot()] = new Value.ArrayValue(newArrayInst.type, size, initValue);
                }
                case Instruction.NewStruct newStructInst -> {
                    execStack.stack[base + newStructInst.destOperand().frameSlot()] = new Value.StructValue(newStructInst.type);
                }
                case Instruction.ArrayStore arrayStoreInst -> {
                    if (arrayStoreInst.arrayOperand() instanceof Operand.RegisterOperand arrayOperand) {
                        Value.ArrayValue arrayValue = (Value.ArrayValue) execStack.stack[base + arrayOperand.frameSlot()];
                        int index = 0;
                        if (arrayStoreInst.indexOperand() instanceof Operand.IntConstantOperand constant) {
                            index = (int) constant.value;
                        }
                        else if (arrayStoreInst.indexOperand() instanceof Operand.RegisterOperand registerOperand) {
                            Value.IntegerValue indexValue = (Value.IntegerValue) execStack.stack[base + registerOperand.frameSlot()];
                            index = (int) indexValue.value;
                        }
                        else throw new IllegalStateException();
                        Value value = valueFromOperand(arrayStoreInst.sourceOperand(), execStack, base);
                        if (index == arrayValue.values.size())
                            arrayValue.values.add(value);
                        else
                            arrayValue.values.set(index, value);
                    } else throw new IllegalStateException();
                }
                case Instruction.ArrayLoad arrayLoadInst -> {
                    if (arrayLoadInst.arrayOperand() instanceof Operand.RegisterOperand arrayOperand) {
                        Value.ArrayValue arrayValue = (Value.ArrayValue) execStack.stack[base + arrayOperand.frameSlot()];
                        if (arrayLoadInst.indexOperand() instanceof Operand.IntConstantOperand constant) {
                            execStack.stack[base + arrayLoadInst.destOperand().frameSlot()] = arrayValue.values.get((int) constant.value);
                        }
                        else if (arrayLoadInst.indexOperand() instanceof Operand.RegisterOperand registerOperand) {
                            Value.IntegerValue index = (Value.IntegerValue) execStack.stack[base + registerOperand.frameSlot()];
                            execStack.stack[base + arrayLoadInst.destOperand().frameSlot()] = arrayValue.values.get((int) index.value);
                        }
                        else throw new IllegalStateException();
                    } else throw new IllegalStateException();
                }
                case Instruction.SetField setFieldInst -> {
                    if (setFieldInst.structOperand() instanceof Operand.RegisterOperand structOperand) {
                        Value.StructValue structValue = (Value.StructValue) execStack.stack[base + structOperand.frameSlot()];
                        int index = setFieldInst.fieldIndex;
                        Value value = valueFromOperand(setFieldInst.sourceOperand(), execStack, base);
                        structValue.fields[index] = value;
                    } else throw new IllegalStateException();
                }
                case Instruction.GetField getFieldInst -> {
                    if (getFieldInst.structOperand() instanceof Operand.RegisterOperand structOperand) {
                        Value.StructValue structValue = (Value.StructValue) execStack.stack[base + structOperand.frameSlot()];
                        int index = getFieldInst.fieldIndex;
                        execStack.stack[base + getFieldInst.destOperand().frameSlot()] = structValue.fields[index];
                    } else throw new IllegalStateException();
                }
                default -> throw new IllegalStateException("Unexpected value: " + instruction);
            }
        }
        return returnValue;
    }


    private Value valueFromOperand(Operand operand, ExecutionStack execStack, int base) {
        if (operand instanceof Operand.IntConstantOperand constant)
            return new Value.IntegerValue(constant.value);
        if (operand instanceof Operand.FloatConstantOperand constant)
            return new Value.FloatValue(constant.value);
        if (operand instanceof Operand.RegisterOperand registerOperand)
            return execStack.stack[base + registerOperand.frameSlot()];
        if (operand instanceof Operand.NullConstantOperand)
            return new Value.NullValue();
        throw new IllegalStateException();
    }
    static class Frame {
        Frame caller;
        int base;
        CompiledFunction bytecodeFunction;

        public Frame(Symbol.FunctionTypeSymbol functionSymbol) {
            this.caller = null;
            this.base = 0;
            this.bytecodeFunction = (CompiledFunction) functionSymbol.code();
        }

        Frame(Frame caller, int base, EZType.EZTypeFunction functionType) {
            this.caller = caller;
            this.base = base;
            this.bytecodeFunction = (CompiledFunction) functionType.code;
        }
    }
}
