package com.compilerprogramming.ezlang.parser;

import com.compilerprogramming.ezlang.lexer.Lexer;
import org.junit.Test;

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
func main() {
    var m = 42
    var t: Tree
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
}
