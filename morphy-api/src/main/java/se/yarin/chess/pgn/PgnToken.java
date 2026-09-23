package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a token in a PGN file.
 */
public record PgnToken(
        @NotNull TokenType type,
        @Nullable String value,
        int line,
        int column
) {
    /**
     * Types of tokens in PGN format.
     */
    public enum TokenType {
        /** Opening bracket of a tag pair: [ */
        TAG_OPEN,
        /** Closing bracket of a tag pair: ] */
        TAG_CLOSE,
        /** Tag name within a tag pair */
        TAG_NAME,
        /** Tag value within a tag pair (string without quotes) */
        TAG_VALUE,
        /** Move number indicator (e.g., "1.", "23...", "5...") */
        MOVE_NUMBER,
        /** Move text in SAN notation (e.g., "e4", "Nf3", "O-O") */
        MOVE_TEXT,
        /** Numeric Annotation Glyph (e.g., "$1", "$14") */
        NAG,
        /** Comment in braces (value includes content without braces) */
        COMMENT,
        /** Opening parenthesis for variation start */
        VARIATION_START,
        /** Closing parenthesis for variation end */
        VARIATION_END,
        /** Game result (1-0, 0-1, 1/2-1/2, *) */
        RESULT,
        /** End of file */
        EOF
    }

    public PgnToken(@NotNull TokenType type, @Nullable String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    /**
     * Creates a token without a value.
     */
    public PgnToken(@NotNull TokenType type, int line, int column) {
        this(type, null, line, column);
    }

    @Override
    public String toString() {
        if (value != null) {
            return type + "(" + value + ")";
        }
        return type.toString();
    }
}
