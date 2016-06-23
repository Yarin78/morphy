package se.yarin.chess;

import lombok.NonNull;

import static se.yarin.chess.Piece.NO_PIECE;

/**
 * Represents the minimum information about a chess move to be able to apply it on a position.
 * Does not contain enough information to display the move without knowing the position.
 * ShortMove is immutable.
 */
public class ShortMove {
    private final int fromSqi, toSqi;
    private final Stone promotionStone;

    public ShortMove(int fromCol, int fromRow, int toCol, int toRow) {
        this(fromCol, fromRow, toCol, toRow, Stone.NO_STONE);
    }

    public ShortMove(int fromCol, int fromRow, int toCol, int toRow, @NonNull Stone promotionStone) {
        this(Chess.coorToSqi(fromCol, fromRow), Chess.coorToSqi(toCol, toRow), promotionStone);
    }

    public ShortMove(int fromSqi, int toSqi) {
        this(fromSqi, toSqi, Stone.NO_STONE);
    }

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

    public Move toMove(@NonNull Position fromPosition) {
        return new Move(fromPosition, fromSqi(), toSqi(), promotionStone());
    }

    public static ShortMove nullMove() {
        return new ShortMove(Chess.NO_SQUARE, Chess.NO_SQUARE);
    }

    public boolean isNullMove() {
        return fromSqi == Chess.NO_SQUARE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ShortMove)) return false;

        ShortMove move = (ShortMove) o;

        if (fromSqi != move.fromSqi) return false;
        if (toSqi != move.toSqi) return false;
        return promotionStone == move.promotionStone;

    }

    @Override
    public int hashCode() {
        int result = fromSqi;
        result = 31 * result + toSqi;
        result = 31 * result + promotionStone.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(5);
        if (isNullMove()) {
            sb.append("----");
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
