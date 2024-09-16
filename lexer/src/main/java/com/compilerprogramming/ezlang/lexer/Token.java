package com.compilerprogramming.ezlang.lexer;

public class Token {

    public enum Kind {
        IDENT,
        NUM,
        PUNCT,
        EOZ     // Special kind to signal end of file
    }

    public final Kind kind;
    /**
     * String representation of a token - always
     * populated
     */
    public final String str;
    /**
     * The parsed number value, only populated for Kind.NUM
     */
    public final Number num;
    public final int lineNumber;

    public Token(Kind kind, String str, Number num, int lineNumber) {
        this.kind = kind;
        this.str = str;
        this.num = num;
        this.lineNumber = lineNumber;
    }

    public static Token newIdent(String str, int lineNumber) {
        return new Token(Kind.IDENT, str.intern(), null, lineNumber);
    }
    public static Token newNum(Number num, String str, int lineNumber) {
        return new Token(Kind.NUM, str, num, lineNumber);
    }
    public static Token newPunct(String str, int lineNumber) {
        return new Token(Kind.PUNCT, str.intern(), null, lineNumber);
    }

    /**
     * Special token that indicates that source has been exhausted
     */
    public static Token EOF = new Token(Kind.EOZ, "", null, 0);

    public String toString() {
        return str;
    }
}
