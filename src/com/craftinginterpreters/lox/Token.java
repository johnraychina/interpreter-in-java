package com.craftinginterpreters.lox;

public class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;

    // Some token implementations store the location as two numbers:
    // the offset from the beginning of the source file to the beginning of the lexeme,
    // and the length of the lexeme. The scanner needs to know these anyway,
    // so there’s no overhead to calculate them.
    // An offset can be converted to line and column positions later by looking back at the source file and counting the preceding newlines.
    // That sounds slow, and it is.
    // However, you need to do it only when you need to actually display a line and column to the user.
    // Most tokens never appear in an error message.
    // For those, the less time you spend calculating position information ahead of time, the better.
    final int line;

    Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
