package se.yarin.cbhlib.util.parser;

public enum TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, TILDE, SEMICOLON, SLASH, STAR,
    BITWISE_AND, BITWISE_OR, BITWISE_XOR,
    LOGICAL_AND, LOGICAL_OR,

    // One or two character tokens.
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Literals.
    IDENTIFIER, STRING, INTEGER,

    // Keywords
    FALSE, TRUE,

    EOF
}
