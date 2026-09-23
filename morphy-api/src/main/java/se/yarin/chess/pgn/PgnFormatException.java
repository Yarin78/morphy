package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when PGN parsing fails.
 * Includes line and column information to help locate the error.
 */
public class PgnFormatException extends Exception {
    private final int line;
    private final int column;

    public PgnFormatException(@NotNull String message) {
        this(message, -1, -1);
    }

    public PgnFormatException(@NotNull String message, int line, int column) {
        super(formatMessage(message, line, column));
        this.line = line;
        this.column = column;
    }

    public PgnFormatException(@NotNull String message, @Nullable Throwable cause) {
        this(message, -1, -1, cause);
    }

    public PgnFormatException(@NotNull String message, int line, int column, @Nullable Throwable cause) {
        super(formatMessage(message, line, column), cause);
        this.line = line;
        this.column = column;
    }

    private static String formatMessage(String message, int line, int column) {
        if (line >= 0 && column >= 0) {
            return String.format("%s at line %d, column %d", message, line, column);
        } else if (line >= 0) {
            return String.format("%s at line %d", message, line);
        }
        return message;
    }

    /**
     * @return the line number where the error occurred, or -1 if not available
     */
    public int getLine() {
        return line;
    }

    /**
     * @return the column number where the error occurred, or -1 if not available
     */
    public int getColumn() {
        return column;
    }
}
