package com.sqlorb;

import java.util.List;

/**
 * ParserContext
 * ------------------------------------------------------------
 * Maintains parsing state and token navigation utilities.
 *
 * Future-ready for:
 *  - Structured syntax exceptions
 *  - Error recovery
 *  - Suggested fixes
 */
public class ParserContext {

    private final List<Token> tokens;
    private int current = 0;

    public ParserContext(List<Token> tokens) {
        this.tokens = tokens;
    }

    // =====================================================
    // NAVIGATION
    // =====================================================
    public Token peek() {
        return current < tokens.size()
            ? tokens.get(current)
            : new Token(TokenType.EOF, "", -1, -1);
    }

    public Token peekNext() {
        return (current + 1 < tokens.size())
            ? tokens.get(current + 1)
            : new Token(TokenType.EOF, "", -1, -1);
    }

    public Token consume() {
        Token t = peek();
        if (current < tokens.size()) current++;
        return t;
    }

    // =====================================================
    // MATCHING
    // =====================================================

    /**
     * Strict match.
     * Throws syntax error if token does not match.
     */
    public void match(TokenType expected) {
        Token t = peek();
        if (t.type == expected) {
            consume();
        } else {
            throw new ParseException(
                "Syntax Error: Expected " + expected + " but found '" + t.value + "'",
                "Ensure you have the right keyword", t.line, t.column
            );
        }
    }
    public void match(TokenType expected, String desc) {
        Token token = peek();
        if (token.type != expected) {
            throw new ParseException(
                "Syntax Error: Expected " + desc + " but found '" + token.value + "'",
                "Double check the keyword spelling", token.line, token.column
            );
        }
        consume();
    }

    /**
     * Optional match.
     * Useful for commas, optional keywords, etc.
     */
    public boolean matchIf(TokenType expected) {
        if (peek().type == expected) {
            consume();
            return true;
        }
        return false;
    }
    public boolean isEOF() {
        return current >= tokens.size() || peek().type == TokenType.EOF;
    }
}
