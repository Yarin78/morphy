package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Lexical analyzer for PGN format.
 * Tokenizes PGN text into a stream of tokens.
 */
public class PgnLexer {
    private final Reader reader;
    private int currentChar;
    private int line = 1;
    private int column = 0;
    private boolean eof = false;

    public PgnLexer(@NotNull String text) {
        this(new StringReader(text));
    }

    public PgnLexer(@NotNull Reader reader) {
        this.reader = reader;
        advance();
    }

    /**
     * Reads the next token from the input.
     *
     * @return the next token
     * @throws PgnFormatException if a lexical error occurs
     */
    @NotNull
    public PgnToken nextToken() throws PgnFormatException {
        skipWhitespace();

        int tokenLine = line;
        int tokenColumn = column;

        if (eof) {
            return new PgnToken(PgnToken.TokenType.EOF, line, column);
        }

        char ch = (char) currentChar;

        // Tag pair opening
        if (ch == '[') {
            advance();
            return new PgnToken(PgnToken.TokenType.TAG_OPEN, tokenLine, tokenColumn);
        }

        // Tag pair closing
        if (ch == ']') {
            advance();
            return new PgnToken(PgnToken.TokenType.TAG_CLOSE, tokenLine, tokenColumn);
        }

        // Variation start
        if (ch == '(') {
            advance();
            return new PgnToken(PgnToken.TokenType.VARIATION_START, tokenLine, tokenColumn);
        }

        // Variation end
        if (ch == ')') {
            advance();
            return new PgnToken(PgnToken.TokenType.VARIATION_END, tokenLine, tokenColumn);
        }

        // Comment
        if (ch == '{') {
            return readComment();
        }

        // String (in tag pair)
        if (ch == '"') {
            return readString();
        }

        // NAG
        if (ch == '$') {
            return readNag();
        }

        // Semicolon comment (escape to end of line)
        if (ch == ';') {
            skipLineComment();
            return nextToken();
        }

        // Percent directive (escape to end of line)
        if (ch == '%') {
            skipLineComment();
            return nextToken();
        }

        // Everything else: move numbers, move text, results
        if (isSymbolStart(ch)) {
            return readSymbol();
        }

        throw new PgnFormatException("Unexpected character: '" + ch + "'", tokenLine, tokenColumn);
    }

    private void advance() {
        try {
            currentChar = reader.read();
            if (currentChar == -1) {
                eof = true;
            } else {
                column++;
                if (currentChar == '\n') {
                    line++;
                    column = 0;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading PGN", e);
        }
    }

    private void skipWhitespace() {
        while (!eof && Character.isWhitespace(currentChar)) {
            advance();
        }
    }

    private void skipLineComment() {
        // Skip to end of line
        while (!eof && currentChar != '\n') {
            advance();
        }
        if (!eof) {
            advance(); // Skip the newline
        }
    }

    private PgnToken readComment() throws PgnFormatException {
        int startLine = line;
        int startColumn = column;
        advance(); // Skip '{'

        StringBuilder sb = new StringBuilder();
        while (!eof && currentChar != '}') {
            sb.append((char) currentChar);
            advance();
        }

        if (eof) {
            throw new PgnFormatException("Unclosed comment", startLine, startColumn);
        }

        advance(); // Skip '}'

        return new PgnToken(PgnToken.TokenType.COMMENT, sb.toString(), startLine, startColumn);
    }

    private PgnToken readString() throws PgnFormatException {
        int startLine = line;
        int startColumn = column;
        advance(); // Skip opening quote

        StringBuilder sb = new StringBuilder();
        while (!eof && currentChar != '"') {
            if (currentChar == '\\') {
                advance();
                if (eof) {
                    throw new PgnFormatException("Unclosed string", startLine, startColumn);
                }
                // Handle escape sequences
                char escaped = (char) currentChar;
                if (escaped == '"' || escaped == '\\') {
                    sb.append(escaped);
                } else {
                    // For other characters, include the backslash
                    sb.append('\\').append(escaped);
                }
                advance();
            } else {
                sb.append((char) currentChar);
                advance();
            }
        }

        if (eof) {
            throw new PgnFormatException("Unclosed string", startLine, startColumn);
        }

        advance(); // Skip closing quote

        return new PgnToken(PgnToken.TokenType.TAG_VALUE, sb.toString(), startLine, startColumn);
    }

    private PgnToken readNag() throws PgnFormatException {
        int startLine = line;
        int startColumn = column;
        advance(); // Skip '$'

        StringBuilder sb = new StringBuilder("$");
        while (!eof && Character.isDigit(currentChar)) {
            sb.append((char) currentChar);
            advance();
        }

        if (sb.length() == 1) {
            throw new PgnFormatException("Invalid NAG: expected digits after $", startLine, startColumn);
        }

        return new PgnToken(PgnToken.TokenType.NAG, sb.toString(), startLine, startColumn);
    }

    private boolean isSymbolStart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '*' || ch == '/' || ch == '-' || ch == '+' || ch == '#' || ch == '=';
    }

    private boolean isSymbolChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || ch == '/' || ch == '+' || ch == '#' || ch == '=' || ch == ':' || ch == '.' || ch == '*';
    }

    private PgnToken readSymbol() {
        int startLine = line;
        int startColumn = column;
        StringBuilder sb = new StringBuilder();

        while (!eof && isSymbolChar((char) currentChar)) {
            sb.append((char) currentChar);
            advance();
        }

        String value = sb.toString();

        // Determine token type
        PgnToken.TokenType type = classifySymbol(value);

        return new PgnToken(type, value, startLine, startColumn);
    }

    private PgnToken.TokenType classifySymbol(String value) {
        // Check for game result
        if (value.equals("1-0") || value.equals("0-1") || value.equals("1/2-1/2") || value.equals("*")) {
            return PgnToken.TokenType.RESULT;
        }

        // Check for move number (ends with period(s))
        if (value.matches("\\d+\\.+")) {
            return PgnToken.TokenType.MOVE_NUMBER;
        }

        // Don't try to distinguish TAG_NAME from MOVE_TEXT here
        // The parser will determine the context
        // TAG_NAME is only meaningful after a TAG_OPEN token
        return PgnToken.TokenType.MOVE_TEXT;
    }
}
