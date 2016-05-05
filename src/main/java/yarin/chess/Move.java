package yarin.chess;

import java.util.List;

public class Move {
    private Piece.PieceType piece; // The moving piece
    private int x1, y1, x2, y2; // Between 0-7 each
    private boolean capture; // Is the move a capture?
    private boolean castle; // Is it castling?
    private boolean enpassant; // Is it en passant?
    private Piece.PieceType promotion; // If pawn promotion, this is the new type of the piece

    public Piece.PieceType getPiece() {
        return piece;
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public boolean isCapture() {
        return capture;
    }

    public boolean isCastle() {
        return castle;
    }

    public boolean isEnpassant() {
        return enpassant;
    }

    public Piece.PieceType getPromotionPiece() {
        return promotion;
    }

    public static String pieceName(Piece.PieceType piece) {
        switch (piece) {
            case KNIGHT:
                return "N";
            case BISHOP:
                return "B";
            case ROOK:
                return "R";
            case QUEEN:
                return "Q";
            case KING:
                return "K";
        }
        return "";
    }

    public Move(Board b, int x1, int y1, int x2, int y2) {
        this(b, x1, y1, x2, y2, Piece.PieceType.EMPTY);
    }

    public Move(Board b, int x1, int y1, int x2, int y2, Piece.PieceType promotion) {
        Piece p = b.pieceAt(y1, x1);
        if (p.getPiece() == Piece.PieceType.EMPTY)
            throw new IllegalArgumentException("Illegal move");
        if (p.getColor() != b.getToMove())
            throw new IllegalArgumentException("Illegal move");
        if (p.getPiece() == Piece.PieceType.PAWN && (y2 == 0 || y2 == 7) && (promotion == Piece.PieceType.EMPTY || promotion == Piece.PieceType.PAWN))
            throw new IllegalArgumentException("Illegal promotion piece");

        this.piece = p.getPiece();
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.promotion = promotion;
        this.castle = (piece == Piece.PieceType.KING && Math.abs(x1 - x2) == 2);
        this.enpassant = p.getPiece() == Piece.PieceType.PAWN && x1 != x2 && b.pieceAt(y2, x2).isEmpty();
        this.capture = this.enpassant || !b.pieceAt(y2, x2).isEmpty();
    }

    public Move(Piece.PieceType piece, int x1, int y1, int x2, int y2, boolean capture) {
        this.piece = piece;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.promotion = Piece.PieceType.EMPTY;
        this.castle = (piece == Piece.PieceType.KING && Math.abs(x1 - x2) == 2);
        this.capture = capture;
        this.enpassant = false;
    }

    public Move(Piece.PieceType piece, int x1, int y1, int x2, int y2, boolean capture, boolean enpassant, Piece.PieceType promotion) {
        this(piece, x1, y1, x2, y2, capture);
        this.enpassant = enpassant;
        this.promotion = promotion;
    }

    /**
     * Returns the full algebraic notation of the move, such as Qd1-d4, e4xd5, Nb2-d2, a7-a8=Q
     * NOTE: Check (+) or checkmate (#) characters will not be appended.
     *
     * @return The full algebraic notation of the move
     */
    public String toString() {
        if (castle)
            return x2 > x1 ? "O-O" : "O-O-O";

        StringBuilder sb = new StringBuilder();
        sb.append(pieceName(piece));
        sb.append((char) ('a' + x1));
        sb.append((char) ('1' + y1));
        if (capture)
            sb.append("x");
        else
            sb.append("-");
        sb.append((char) ('a' + x2));
        sb.append((char) ('1' + y2));
        if (promotion != Piece.PieceType.EMPTY)
            sb.append("=" + pieceName(promotion));
        return sb.toString();
    }

    /**
     * Returns the short algebraic notation of the move, such as Qd4, exd5, Nbd2, a8=Q+
     * The move must be a legal move in the specified game position.
     * Check (+) and/or checkmate (#) characters will be appended.
     *
     * @param position the current game position
     * @return the full algebraic notation of the move
     */
    public String toString(Board position) {
        StringBuilder sb = new StringBuilder();

        if (castle) {
            sb.append("O-O");
            if (x2 == 2) sb.append("-O");
        } else if (piece == Piece.PieceType.PAWN) {
            if (capture) {
                sb.append((char) ('a' + x1));
                sb.append('x');
            }
            sb.append((char) ('a' + x2));
            sb.append((char) ('1' + y2));

            if (promotion != Piece.PieceType.EMPTY)
                sb.append("=").append(Move.pieceName(promotion));
        } else {
            List<Move> allMoves = position.getLegalMoves();
            boolean extend = false, xuniq = true, yuniq = true;
            for (Move m : allMoves) {
                if (m.x2 == x2 && m.y2 == y2 && m.piece == piece) {
                    if (m.x1 == x1 && m.y1 == y1) continue; // Same move
                    extend = true;
                    if (m.x1 == x1) xuniq = false;
                    if (m.y1 == y1) yuniq = false;
                }
            }

            sb.append(Move.pieceName(piece));
            if (extend) {
                if (xuniq)
                    sb.append((char) ('a' + x1));
                else if (yuniq)
                    sb.append((char) ('1' + y1));
                else {
                    sb.append((char) ('a' + x1));
                    sb.append((char) ('1' + y1));
                }
            }
            if (capture)
                sb.append('x');
            sb.append((char) ('a' + x2));
            sb.append((char) ('1' + y2));
        }

        Board b = position.doMove(this);
        if (b.isCheck()) {
            int legalMoves = b.getLegalMoves().size();
            if (legalMoves == 0)
                sb.append("#");
            else
                sb.append("+");
        }

        return sb.toString();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Move))
            return false;
        Move m = (Move) obj;
        return this.x1 == m.x1 && this.x2 == m.x2 && this.y1 == m.y1 && this.y2 == m.y2 &&
                this.piece == m.piece && this.promotion == m.promotion;
    }

    public int hashCode() {
        return x1 + y1 * 8 + x2 * 64 + y2 * 512 + promotion.ordinal() * 4096;
    }
}

