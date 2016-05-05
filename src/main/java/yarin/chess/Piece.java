package yarin.chess;

public class Piece {
    public enum PieceType {
        EMPTY, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING;
        private static final PieceType[] allPieceTypes = new PieceType[] { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING };
        public static PieceType[] all() {
            return allPieceTypes;
        }
    }

    public enum PieceColor {
        EMPTY, WHITE, BLACK;
        private static final PieceColor[] allColors = new PieceColor[] { WHITE, BLACK };
        public static PieceColor[] all() {
            return allColors;
        }
    }

    private PieceType piece;
    private PieceColor color;

    public Piece(PieceType piece, PieceColor color) {
        if (piece == null) throw new IllegalArgumentException("piece must not be null");
        if (color == null) throw new IllegalArgumentException("color must not be null");
        this.piece = piece;
        this.color = color;
    }

    public Piece() {
        this.piece = PieceType.EMPTY;
        this.color = PieceColor.EMPTY;
    }

    public PieceType getPiece() {
        return piece;
    }

    public PieceColor getColor() {
        return color;
    }

    public boolean isEmpty() {
        return piece == PieceType.EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Piece piece1 = (Piece) o;

        if (piece != piece1.piece) return false;
        return color == piece1.color;

    }

    @Override
    public int hashCode() {
        int result = piece.hashCode();
        result = 31 * result + color.hashCode();
        return result;
    }
}
