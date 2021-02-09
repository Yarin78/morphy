package se.yarin.cbhlib.util.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.util.List;
import java.util.function.BiFunction;

import static se.yarin.cbhlib.util.parser.TokenType.*;


/**
 * Recursive decent parser based on the following grammar:
 *
 * expression  -> logical_or
 * logical_or  -> logical_and ( "||" logical_and )*
 * logical_and -> equality ( "&&" equality )*
 * bitwise_or  -> bitwise_xor ( "|" bitwise_xor )*
 * bitwise_xor -> bitwise_and ( "^" bitwise_and )*
 * bitwise_and -> equality ( "&" equality )*
 * equality    -> comparison ( ( "!=" | "==" ) comparison )*
 * comparison  -> term ( ( ">" | ">=" | "<" | "<=" ) term )*
 * term        -> factor ( ( "-" | "+" ) factor )*
 * factor      -> unary ( ( "/" | "*" ) unary )*
 * unary       -> ( "!" | "-" | "~" ) unary | primary
 * function    -> identifier "(" expression ")"
 * primary     -> INTEGER | true | false | function | "(" expression ")"
 */
public class Parser {
    private static final Logger log = LoggerFactory.getLogger(Parser.class);

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw this.error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            log.error("at end: " + message);
        } else {
            log.error(" at '" + token.lexeme + "': " + message);
        }
        return new ParseError();
    }


    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }



    private Expr expression() {
        return logical_or();
    }

    private Expr logical_or() {
        Expr expr = logical_and();

        while (match(LOGICAL_OR)) {
            Token operator = previous();
            Expr right = logical_and();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr logical_and() {
        Expr expr = bitwise_or();

        while (match(LOGICAL_AND)) {
            Token operator = previous();
            Expr right = bitwise_or();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr bitwise_or() {
        Expr expr = bitwise_xor();

        while (match(BITWISE_OR)) {
            Token operator = previous();
            Expr right = bitwise_xor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr bitwise_xor() {
        Expr expr = bitwise_and();

        while (match(BITWISE_XOR)) {
            Token operator = previous();
            Expr right = bitwise_and();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr bitwise_and() {
        Expr expr = equality();

        while (match(BITWISE_AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        if (match(LESS, LESS_EQUAL, GREATER, GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS, TILDE)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr function() {
        Token functionToken = consume(IDENTIFIER, "Expect function name");
        consume(LEFT_PAREN, "Expect '(' after function name");
        Expr expr = expression();
        consume(RIGHT_PAREN, "Expect ')' after function call");

        BiFunction<byte[], Integer, Integer> function;
        String functionName = functionToken.lexeme;
        switch (functionName) {
            case "byte" -> function = ByteBufferUtil::getUnsignedByte;
            case "shortb" -> function = ByteBufferUtil::getUnsignedShortB;
            case "shortl" -> function = ByteBufferUtil::getUnsignedShortL;
            case "intb" -> function = ByteBufferUtil::getIntB;
            case "intl" -> function = ByteBufferUtil::getIntL;
            default -> throw new RuntimeException("Unknown function: " + functionName);
        }
        return new Expr.Function(function, functionName, expr);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(INTEGER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (check(IDENTIFIER)) {
            return function();
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression.");
    }
}
