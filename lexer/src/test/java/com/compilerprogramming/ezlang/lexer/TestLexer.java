package com.compilerprogramming.ezlang.lexer;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestLexer {

    @Test
    public void testLexer() {
        String src = """
                // A comment
                Ident=,>=<>>=!=!1.5{0}11()[]+-*/ //Another comment
                """;
        Lexer lexer = new Lexer(src);
        Token[] expected = new Token[]{
                Token.newIdent("Ident", 0),
                Token.newPunct("=", 0),
                Token.newPunct(",", 0),
                Token.newPunct(">=", 0),
                Token.newPunct("<", 0),
                Token.newPunct(">", 0),
                Token.newPunct(">=", 0),
                Token.newPunct("!=", 0),
                Token.newPunct("!", 0),
                Token.newNum(null, "1.5", 0),
                Token.newPunct("{", 0),
                Token.newNum(null, "0", 0),
                Token.newPunct("}", 0),
                Token.newNum(null, "11", 0),
                Token.newPunct("(", 0),
                Token.newPunct(")", 0),
                Token.newPunct("[", 0),
                Token.newPunct("]", 0),
                Token.newPunct("+", 0),
                Token.newPunct("-", 0),
                Token.newPunct("*", 0),
                Token.newPunct("/", 0)
        };

        List<Token> tokens = new ArrayList<>();
        Token token = lexer.scan();
        while (token != Token.EOF) {
            tokens.add(token);
            token = lexer.scan();
        }

        Assert.assertEquals(tokens.size(), expected.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i].toString(), tokens.get(i).toString());
        }
    }

}
