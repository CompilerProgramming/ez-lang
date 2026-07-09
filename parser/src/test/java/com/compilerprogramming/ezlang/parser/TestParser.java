package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Lexer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestParser {

    @Test
    public void testParse() {
        Parser parser = new Parser();
        String src = """
struct Tree {
    var left: Tree?
    var right: Tree?
}
struct Test {
    var intArray: [Int]
}
struct TreeArray {
    var array: [Tree?]?
}
func print(n: Int) {}
func foo(a: Int, b: [Int]) {
    while(1) {
        if (a > b.length)
            break
        print(b[a])
        a = a + 1
        a = a + 2
    }
}
func bar() -> Test {
    var v = new Test { intArray = new [Tree] {} }
    return v
}
func floatAdd(a: Float, b: Float)->Float {
    return a+b
}
func floatArrayCreate()->[Float] {
    var array = new [Float] {len=10,1.0,2.0,3.0}
    return array
}
func main() {
    var m = 42
    var t: Tree
    var array = new [Int] {len=10,1,2,3}
    array[1] = 42
    t.left = null
    if (m < 1)
       print(1)
    else if (m == 5)
       print(2)
    else
       print(3)
}
                """;
        var program = parser.parse(new Lexer(src));
        System.out.println(program.toString());
        return;
    }

    @Test
    public void testShortCircuitWhileConditionLoweredInsideLoop() {
        Parser parser = new Parser();
        String src = """
                func foo(a: Int, b: Int)->Int {
                  var x = 0
                  while (x || b) {
                    x = 0
                  }
                  return x
                }
                """;
        var program = parser.parse(new Lexer(src));
        ShortCircuitLowerer.lower(program);
        String lowered = program.toString();
        assertTrue(lowered.contains("while(1)"));
        assertTrue(lowered.contains("while(1)\n{\nvar __sc0 = 1"));
    }
}
