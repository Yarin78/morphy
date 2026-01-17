package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.*;

import java.util.EnumSet;

import static se.yarin.chess.Chess.*;

/**
 * Represents a complete chess position state, including the board position
 * and additional state information (halfmove clock and fullmove number).
 * This class provides bidirectional conversion to/from FEN (Forsyth-Edwards Notation).
 */
public class PositionState {

    private final Position position;
    private final int halfMoveClock;
    private final int fullMoveNumber;

    /**
     * The standard starting position FEN string.
     */
    public static final String START_POSITION_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Creates a PositionState instance.
     *
     * @param position the chess position
     * @param halfMoveClock the halfmove clock for the 50-move rule
     * @param fullMoveNumber the fullmove number
     */
    public PositionState(@NotNull Position position, int halfMoveClock, int fullMoveNumber) {
        if (halfMoveClock < 0) {
            throw new IllegalArgumentException("halfMoveClock must be >= 0");
        }
        if (fullMoveNumber < 1) {
            throw new IllegalArgumentException("fullMoveNumber must be >= 1");
        }
        this.position = position;
        this.halfMoveClock = halfMoveClock;
        this.fullMoveNumber = fullMoveNumber;
    }

    /**
     * @return the chess position
     */
    @NotNull
    public Position position() {
        return position;
    }

    /**
     * @return the halfmove clock
     */
    public int halfMoveClock() {
        return halfMoveClock;
    }

    /**
     * @return the fullmove number
     */
    public int fullMoveNumber() {
        return fullMoveNumber;
    }

    /**
     * Parses a FEN string.
     *
     * @param fen the FEN string to parse
     * @return the parsed position state
     * @throws PgnFormatException if the FEN string is invalid
     */
    @NotNull
    public static PositionState fromFen(@NotNull String fen) throws PgnFormatException {
        String[] parts = fen.trim().split("\\s+");

        if (parts.length < 4 || parts.length > 6) {
            throw new PgnFormatException("Invalid FEN format: expected 4-6 fields, got " + parts.length);
        }

        // Parse piece placement
        Stone[] board = parsePiecePlacement(parts[0]);

        // Parse active color
        Player toMove = parseActiveColor(parts[1]);

        // Parse castling availability
        EnumSet<Castles> castles = parseCastlingRights(parts[2]);

        // Parse en passant square
        int epCol = parseEnPassantSquare(parts[3]);

        // Parse halfmove clock (optional, defaults to 0)
        int halfMoveClock = 0;
        if (parts.length >= 5) {
            try {
                halfMoveClock = Integer.parseInt(parts[4]);
                if (halfMoveClock < 0) {
                    throw new PgnFormatException("Invalid halfmove clock: must be >= 0");
                }
            } catch (NumberFormatException e) {
                throw new PgnFormatException("Invalid halfmove clock: " + parts[4], e);
            }
        }

        // Parse fullmove number (optional, defaults to 1)
        int fullMoveNumber = 1;
        if (parts.length >= 6) {
            try {
                fullMoveNumber = Integer.parseInt(parts[5]);
                if (fullMoveNumber < 1) {
                    throw new PgnFormatException("Invalid fullmove number: must be >= 1");
                }
            } catch (NumberFormatException e) {
                throw new PgnFormatException("Invalid fullmove number: " + parts[5], e);
            }
        }

        // Determine if this is a Chess960 position
        // For now, assume regular chess unless it's clearly Chess960
        int chess960sp = Chess960.REGULAR_CHESS_SP;

        Position position = new Position(board, toMove, castles, epCol, chess960sp);

        return new PositionState(position, halfMoveClock, fullMoveNumber);
    }

    /**
     * Converts this position state to a FEN string.
     *
     * @return the FEN string
     */
    @NotNull
    public String toFen() {
        StringBuilder sb = new StringBuilder();

        // Piece placement
        sb.append(generatePiecePlacement(position));
        sb.append(' ');

        // Active color
        sb.append(position.playerToMove() == Player.WHITE ? 'w' : 'b');
        sb.append(' ');

        // Castling availability
        sb.append(generateCastlingRights(position));
        sb.append(' ');

        // En passant square
        sb.append(generateEnPassantSquare(position));
        sb.append(' ');

        // Halfmove clock
        sb.append(halfMoveClock);
        sb.append(' ');

        // Fullmove number
        sb.append(fullMoveNumber);

        return sb.toString();
    }

    /**
     * Converts a position to a FEN string.
     * Helper method for cases where you just have a position and ply.
     *
     * @param position the position to convert
     * @param ply the current ply number (used to calculate fullmove number)
     * @return the FEN string
     */
    @NotNull
    public static String toFen(@NotNull Position position, int ply) {
        return toFen(position, ply, 0);
    }

    /**
     * Converts a position to a FEN string.
     * Helper method for cases where you just have a position and ply.
     *
     * @param position the position to convert
     * @param ply the current ply number (used to calculate fullmove number)
     * @param halfMoveClock the halfmove clock for the 50-move rule
     * @return the FEN string
     */
    @NotNull
    public static String toFen(@NotNull Position position, int ply, int halfMoveClock) {
        // Fullmove number (starts at 1, increments after black's move)
        int fullMoveNumber = (ply / 2) + 1;
        PositionState state = new PositionState(position, halfMoveClock, fullMoveNumber);
        return state.toFen();
    }

    private static Stone[] parsePiecePlacement(String placement) throws PgnFormatException {
        Stone[] board = new Stone[64];
        for (int i = 0; i < 64; i++) {
            board[i] = Stone.NO_STONE;
        }

        String[] ranks = placement.split("/");
        if (ranks.length != 8) {
            throw new PgnFormatException("Invalid piece placement: expected 8 ranks, got " + ranks.length);
        }

        for (int rank = 0; rank < 8; rank++) {
            String rankStr = ranks[rank];
            int row = 7 - rank; // FEN starts from rank 8 (row 7)
            int col = 0;

            for (int i = 0; i < rankStr.length(); i++) {
                char ch = rankStr.charAt(i);

                if (Character.isDigit(ch)) {
                    int emptySquares = ch - '0';
                    if (emptySquares < 1 || emptySquares > 8) {
                        throw new PgnFormatException("Invalid empty square count: " + ch);
                    }
                    col += emptySquares;
                } else {
                    Stone stone = Stone.fromChar(ch);
                    if (stone == null || stone == Stone.NO_STONE) {
                        throw new PgnFormatException("Invalid piece character: " + ch);
                    }
                    if (col >= 8) {
                        throw new PgnFormatException("Too many pieces in rank " + (rank + 1));
                    }
                    board[coorToSqi(col, row)] = stone;
                    col++;
                }
            }

            if (col != 8) {
                throw new PgnFormatException("Incomplete rank " + (rank + 1) + ": expected 8 squares, got " + col);
            }
        }

        return board;
    }

    private static Player parseActiveColor(String color) throws PgnFormatException {
        return switch (color.toLowerCase()) {
            case "w" -> Player.WHITE;
            case "b" -> Player.BLACK;
            default -> throw new PgnFormatException("Invalid active color: " + color);
        };
    }

    private static EnumSet<Castles> parseCastlingRights(String castling) throws PgnFormatException {
        EnumSet<Castles> rights = EnumSet.noneOf(Castles.class);

        if (castling.equals("-")) {
            return rights;
        }

        for (int i = 0; i < castling.length(); i++) {
            char ch = castling.charAt(i);
            switch (ch) {
                case 'K' -> rights.add(Castles.WHITE_SHORT_CASTLE);
                case 'Q' -> rights.add(Castles.WHITE_LONG_CASTLE);
                case 'k' -> rights.add(Castles.BLACK_SHORT_CASTLE);
                case 'q' -> rights.add(Castles.BLACK_LONG_CASTLE);
                default -> throw new PgnFormatException("Invalid castling character: " + ch);
            }
        }

        return rights;
    }

    private static int parseEnPassantSquare(String epSquare) throws PgnFormatException {
        if (epSquare.equals("-")) {
            return NO_COL;
        }

        if (epSquare.length() != 2) {
            throw new PgnFormatException("Invalid en passant square: " + epSquare);
        }

        char file = epSquare.charAt(0);
        char rank = epSquare.charAt(1);

        if (file < 'a' || file > 'h') {
            throw new PgnFormatException("Invalid en passant file: " + file);
        }
        if (rank != '3' && rank != '6') {
            throw new PgnFormatException("Invalid en passant rank: " + rank + " (must be 3 or 6)");
        }

        return file - 'a'; // Return the column
    }

    private static String generatePiecePlacement(Position position) {
        StringBuilder sb = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            int emptyCount = 0;

            for (int col = 0; col < 8; col++) {
                Stone stone = position.stoneAt(col, row);

                if (stone == Stone.NO_STONE) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    sb.append(stone.toChar());
                }
            }

            if (emptyCount > 0) {
                sb.append(emptyCount);
            }

            if (row > 0) {
                sb.append('/');
            }
        }

        return sb.toString();
    }

    private static String generateCastlingRights(Position position) {
        StringBuilder sb = new StringBuilder();

        if (position.isCastles(Castles.WHITE_SHORT_CASTLE)) {
            sb.append('K');
        }
        if (position.isCastles(Castles.WHITE_LONG_CASTLE)) {
            sb.append('Q');
        }
        if (position.isCastles(Castles.BLACK_SHORT_CASTLE)) {
            sb.append('k');
        }
        if (position.isCastles(Castles.BLACK_LONG_CASTLE)) {
            sb.append('q');
        }

        return sb.length() > 0 ? sb.toString() : "-";
    }

    private static String generateEnPassantSquare(Position position) {
        int epCol = position.getEnPassantCol();
        if (epCol == NO_COL) {
            return "-";
        }

        char file = (char) ('a' + epCol);
        char rank = position.playerToMove() == Player.WHITE ? '6' : '3';
        return "" + file + rank;
    }
}
