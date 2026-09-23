package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.*;

import java.util.List;

/**
 * Parser for SAN (Standard Algebraic Notation) move strings.
 * Converts SAN strings like "Nf3", "e4", "O-O", "exd8=Q+" into Move objects.
 */
public class PgnMoveParser {

    private final Position position;

    /**
     * Creates a move parser for the given position.
     *
     * @param position the position from which to parse moves
     */
    public PgnMoveParser(@NotNull Position position) {
        this.position = position;
    }

    /**
     * Parses a SAN move string into a Move object.
     *
     * @param san the SAN string (e.g., "Nf3", "e4", "O-O", "exd8=Q+")
     * @return the parsed move
     * @throws PgnFormatException if the move is invalid or ambiguous
     */
    @NotNull
    public Move parseMove(@NotNull String san) throws PgnFormatException {
        String original = san;

        // Strip whitespace
        san = san.trim();

        // Strip check/mate indicators
        san = san.replaceAll("[+#]$", "");

        switch (san) {
            // Handle castling
            case "O-O", "0-0" -> {
                return Move.shortCastles(position);
            }
            case "O-O-O", "0-0-0" -> {
                return Move.longCastles(position);
            }
            // Handle null move (sometimes used in variations)
            case "--", "Z0" -> {
                return Move.nullMove(position);
            }
        }

        // Parse the move components
        int index = 0;
        Piece piece = Piece.PAWN;
        int disambigCol = -1;
        int disambigRow = -1;
        boolean isCapture = false;
        Stone promotionStone = Stone.NO_STONE;

        // Check for piece type
        if (!san.isEmpty() && Character.isUpperCase(san.charAt(0))) {
            char pieceChar = san.charAt(0);
            piece = Piece.fromChar(pieceChar);
            if (piece == null || piece == Piece.NO_PIECE) {
                throw new PgnFormatException("Invalid piece character: " + pieceChar);
            }
            index++;
        }

        // Now we need to parse: [disambiguation][capture]destination[promotion]
        // Where disambiguation can be: file, rank, or both (e.g., Nbd2, N1c3, Qh4e1)

        // Look ahead to find the destination square (last 2 characters before promotion)
        int destSquareIndex = san.length() - 2;
        if (san.indexOf('=') != -1) {
            destSquareIndex = san.indexOf('=') - 2;
        }

        if (destSquareIndex < index || destSquareIndex + 1 >= san.length()) {
            throw new PgnFormatException("Invalid move format: " + original);
        }

        // Extract destination square
        String destSquare = san.substring(destSquareIndex, destSquareIndex + 2);
        int destCol = parseFile(destSquare.charAt(0));
        int destRow = parseRank(destSquare.charAt(1));
        int destSqi = Chess.coorToSqi(destCol, destRow);

        // Parse everything between piece and destination
        String middle = san.substring(index, destSquareIndex);

        // Check for capture indicator
        if (middle.contains("x")) {
            isCapture = true;
            middle = middle.replace("x", "");
        }

        // Parse disambiguation
        if (middle.length() >= 2) {
            // Both file and rank (e.g., "h4" in "Qh4e1")
            disambigCol = parseFile(middle.charAt(0));
            disambigRow = parseRank(middle.charAt(1));
        } else if (middle.length() == 1) {
            char ch = middle.charAt(0);
            if (ch >= 'a' && ch <= 'h') {
                // File disambiguation
                disambigCol = parseFile(ch);
            } else if (ch >= '1' && ch <= '8') {
                // Rank disambiguation
                disambigRow = parseRank(ch);
            }
        }

        // Parse promotion
        if (san.indexOf('=') != -1) {
            int promotionIndex = san.indexOf('=') + 1;
            if (promotionIndex >= san.length()) {
                throw new PgnFormatException("Invalid promotion format: " + original);
            }
            char promotionChar = san.charAt(promotionIndex);
            Piece promotionPiece = Piece.fromChar(promotionChar);
            if (promotionPiece == null || promotionPiece == Piece.PAWN || promotionPiece == Piece.KING) {
                throw new PgnFormatException("Invalid promotion piece: " + promotionChar);
            }
            promotionStone = promotionPiece.toStone(position.playerToMove());
        }

        // Find the move by filtering legal moves
        List<Move> legalMoves = position.generateAllLegalMoves();
        Move foundMove = null;

        for (Move move : legalMoves) {
            // Check if this move matches all criteria
            if (move.toSqi() != destSqi) continue;
            if (move.movingPiece() != piece) continue;
            if (move.promotionStone() != promotionStone) continue;
            if (isCapture && !move.isCapture()) continue;

            // Check disambiguation
            if (disambigCol != -1 && Chess.sqiToCol(move.fromSqi()) != disambigCol) continue;
            if (disambigRow != -1 && Chess.sqiToRow(move.fromSqi()) != disambigRow) continue;

            if (foundMove != null) {
                throw new PgnFormatException("Ambiguous move: " + original);
            }
            foundMove = move;
        }

        if (foundMove == null) {
            throw new PgnFormatException("Illegal move: " + original);
        }

        return foundMove;
    }

    private int parseFile(char file) throws PgnFormatException {
        if (file < 'a' || file > 'h') {
            throw new PgnFormatException("Invalid file: " + file);
        }
        return file - 'a';
    }

    private int parseRank(char rank) throws PgnFormatException {
        if (rank < '1' || rank > '8') {
            throw new PgnFormatException("Invalid rank: " + rank);
        }
        return rank - '1';
    }
}
