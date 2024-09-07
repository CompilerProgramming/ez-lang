/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * <p>
 * Contributor(s):
 * <p>
 * The Initial Developer of the Original Software is Dibyendu Majumdar.
 * <p>
 * This file is part of the Compiler Craft project.
 * See https://github.com/dibyendumajumdar/CompilerCraft
 * <p>
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.compilerprogramming.ezlang.lexer;


import com.compilerprogramming.ezlang.exceptions.CompilerException;

import java.text.NumberFormat;
import java.text.ParseException;

public class Lexer {

    private final char[] input;
    /**
     * Tracks current position in input buffer
     */
    private int position = 0;
    private int lineNumber = 0;

    private final NumberFormat numberFormat;

    public Lexer(String source) {
        input = source.toCharArray();
        numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);
    }

    /**
     * Parses number in format nnn[.nnn]
     * where n is a digit
     */
    private Token parseNumber() {
        assert Character.isDigit(input[position]);
        int startPosition = position++;
        while (position < input.length && Character.isDigit(input[position]))
            position++;
        if (input[position] == '.') {
            position++;
            while (position < input.length && Character.isDigit(input[position]))
                position++;
        }
        String str = new String(input, startPosition, position - startPosition);
        Number number = parseNumber(str);
        return Token.newNum(number, str, lineNumber);
    }

    private Number parseNumber(String str) {
        try {
            return numberFormat.parse(str);
        } catch (ParseException e) {
            throw new CompilerException("Failed to parse number " + str, e);
        }
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isAlphabetic(ch) || ch == '_';
    }

    private boolean isIdentifierLetter(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private Token parseIdentifier() {
        assert isIdentifierStart(input[position]);
        int startPosition = position++;
        while (position < input.length && isIdentifierLetter(input[position]))
            position++;
        return Token.newIdent(new String(input, startPosition, position - startPosition), lineNumber);
    }

    public char peekChar() {
        int pos = position;
        char ch = 0;
        while (pos < input.length && Character.isWhitespace(input[pos]))
            pos++;
        if (pos < input.length)
            ch = input[pos];
        return ch;
    }

    public Token scan() {
        while (true) {
            if (position >= input.length) return Token.EOF;
            switch (input[position]) {
                case 0:
                    return Token.EOF;
                case ' ':
                case '\t':
                    position++;
                    continue;
                case '\r':
                    position++;
                    if (position < input.length && input[position] == '\n') {
                        lineNumber++;
                        position++;
                    }
                    continue;
                case '\n':
                    lineNumber++;
                    position++;
                    continue;
                case '&':
                    position++;
                    if (position < input.length && input[position] == '&') {
                        position++;
                        return Token.newPunct("&&", lineNumber);
                    }
                    return Token.newPunct("&", lineNumber);
                case '|':
                    position++;
                    if (position < input.length && input[position] == '|') {
                        position++;
                        return Token.newPunct("||", lineNumber);
                    }
                    return Token.newPunct("|", lineNumber);
                case '=':
                    position++;
                    if (position < input.length && input[position] == '=') {
                        position++;
                        return Token.newPunct("==", lineNumber);
                    }
                    return Token.newPunct("=", lineNumber);
                case '<':
                    position++;
                    if (position < input.length && input[position] == '=') {
                        position++;
                        return Token.newPunct("<=", lineNumber);
                    }
                    return Token.newPunct("<", lineNumber);
                case '>':
                    position++;
                    if (position < input.length && input[position] == '=') {
                        position++;
                        return Token.newPunct(">=", lineNumber);
                    }
                    return Token.newPunct(">", lineNumber);
                case '!':
                    position++;
                    if (position < input.length && input[position] == '=') {
                        position++;
                        return Token.newPunct("!=", lineNumber);
                    }
                    return Token.newPunct("!", lineNumber);
                case '-':
                    position++;
                    if (position < input.length && input[position] == '>') {
                        position++;
                        return Token.newPunct("->", lineNumber);
                    }
                    return Token.newPunct("-", lineNumber);
                case '{':
                case '}':
                case '[':
                case ']':
                case '(':
                case ')':
                case ',':
                case '.':
                case '%':
                case '+':
                case '*':
                case ';':
                case ':':
                case '?':
                    return Token.newPunct(Character.toString(input[position++]), lineNumber);
                case '/':
                    position++;
                    if (position < input.length && input[position] == '/') {
                        position++;
                        while (position < input.length && input[position] != '\n') position++;
                        continue;
                    }
                    return Token.newPunct("/", lineNumber);
                default: {
                    return scanOthers();
                }
            }
        }
    }

    private Token scanOthers() {
        if (Character.isDigit(input[position])) return parseNumber();
        else if (isIdentifierLetter(input[position])) return parseIdentifier();
        throw new CompilerException("Unexpected character " + input[position] + " at line " + lineNumber);
    }

    public int lineNumber() {return lineNumber;}
}
