package se.yarin.chess;

import lombok.NonNull;

import static se.yarin.chess.Piece.NO_PIECE;

/**
 * Represents the minimum information about a chess move to be able to apply it on a position.
 * Does not contain enough information to display the move without knowing the position.
 * ShortMove is immutable.
 *
 * Note: There may be different ways of representing castles, in which case equals/hashcode doesn't work
 */
public class ShortMove {
    private final int fromSqi, toSqi;
    private final Stone promotionStone;
    // Must be explicitly set to determine castling in a Chess960 game in some situations
    private final boolean longCastling, shortCastling;

    /**
     * Creates a new short move. To create a castling move, use {@link #shortCastles()}
     * or {@link #longCastles()} instead.
     * @param fromSqi the from square index
     * @param toSqi to to square index
     */
    public ShortMove(int fromSqi, int toSqi) {
        this(fromSqi, toSqi, Stone.NO_STONE);
    }

    /**
     * Creates a new short move where a pawn promotes.
     * @param fromSqi the from square index
     * @param toSqi to to square index
     * @param promotionStone the new stone after the promotion
     */
    public ShortMove(int fromSqi, int toSqi, Stone promotionStone) {
        if (fromSqi < 0 || fromSqi >= 64 || toSqi < 0 || toSqi >= 64) {
            // Check if the null move
            if (!(fromSqi == Chess.NO_SQUARE && toSqi == Chess.NO_SQUARE && promotionStone == Stone.NO_STONE)) {
                throw new IllegalArgumentException("Not a valid move");
            }
        }
        this.fromSqi = fromSqi;
        this.toSqi = toSqi;
        this.promotionStone = promotionStone;
        this.longCastling = false;
        this.shortCastling = false;
    }

    private ShortMove(boolean queenSideCastles, boolean kingSideCastles) {
        this.fromSqi = 0;
        this.toSqi = 0;
        this.promotionStone = Stone.NO_STONE;
        this.longCastling = queenSideCastles;
        this.shortCastling = kingSideCastles;
    }

    /**
     * Creates a move representing long (queenside, a-side) castles
     * @return a long castle move
     */
    public static ShortMove longCastles() {
        return new ShortMove(true, false);
    }

    /**
     * Creates a move representing short (kingside, h-side) castles
     * @return a short castle move
     */
    public static ShortMove shortCastles() {
        return new ShortMove(false, true);
    }

    /**
     * Creates a null move
     * @return a null move
     */
    public static ShortMove nullMove() {
        return new ShortMove(Chess.NO_SQUARE, Chess.NO_SQUARE);
    }

    public int fromSqi() {
        return fromSqi;
    }
    public int toSqi() { return toSqi; }
    public int fromCol() { return Chess.sqiToCol(fromSqi); }
    public int fromRow() { return Chess.sqiToRow(fromSqi); }
    public int toCol() { return Chess.sqiToCol(toSqi); }
    public int toRow() { return Chess.sqiToRow(toSqi); }
    public Stone promotionStone() {
        return promotionStone;
    }
    public boolean isLongCastle() {
        return longCastling;
    }

    public boolean isShortCastle() {
        return shortCastling;
    }

    public Move toMove(@NonNull Position fromPosition) {
        if (shortCastling) {
            return Move.shortCastles(fromPosition);
        }
        if (longCastling) {
            return Move.longCastles(fromPosition);
        }
        return new Move(fromPosition, fromSqi(), toSqi(), promotionStone());
    }

    public boolean isNullMove() {
        return fromSqi == Chess.NO_SQUARE;
    }

    public boolean moveEquals(Move move) {
        if (move == null) return false;
        if (isShortCastle() || isLongCastle() || move.isShortCastle() || move.isLongCastle()) {
            return isShortCastle() == move.isShortCastle() &&
                    isLongCastle() == move.isLongCastle();
        }

        return fromSqi() == move.fromSqi() && toSqi() == move.toSqi() &&
                promotionStone() == move.promotionStone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ShortMove)) return false;

        ShortMove move = (ShortMove) o;

        if (fromSqi != move.fromSqi) return false;
        if (toSqi != move.toSqi) return false;
        if (longCastling != move.longCastling) return false;
        if (shortCastling != move.shortCastling) return false;
        return promotionStone == move.promotionStone;
    }

    @Override
    public int hashCode() {
        int result = fromSqi;
        result = 31 * result + toSqi;
        result = 31 * result + promotionStone.hashCode();
        result = 2 * result + (longCastling ? 1 : 0);
        result = 2 * result + (shortCastling ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(5);
        if (isNullMove()) {
            sb.append("----");
        } else if (shortCastling) {
            sb.append("O-O");
        } else if (longCastling) {
            sb.append("O-O-O");
        } else {
            sb.append(Chess.sqiToStr(fromSqi));
            sb.append(Chess.sqiToStr(toSqi));
            if (promotionStone.toPiece() != NO_PIECE) {
                sb.append('=').append(promotionStone.toPiece().toChar());
            }
        }
        return sb.toString();
    }
}
