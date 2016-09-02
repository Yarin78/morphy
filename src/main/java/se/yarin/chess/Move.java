package se.yarin.chess;

import lombok.NonNull;

import static se.yarin.chess.Piece.*;

/**
 * A complete version of a chess move, containing information to
 * generate the SAN and LAN notation of the move.
 *
 * Move is immutable. It's possible to construct illegal moves.
 */
public class Move {
    private final Position fromPosition; // Not used in equals or hashcode
    private final int fromSqi, toSqi;
    private final Stone promotionStone;
    // Specifies if the move was castles. Must be set explicitly for Chess960 castles.
    private final boolean castles;

    // These are caches
    private Boolean isCapture, isCheck, isMate;
    private String sanCache;

    public Move(@NonNull Position fromPosition, int fromCol, int fromRow, int toCol, int toRow) {
        this(fromPosition, Chess.coorToSqi(fromCol, fromRow), Chess.coorToSqi(toCol, toRow));
    }

    public Move(@NonNull Position fromPosition, int fromCol, int fromRow, int toCol, int toRow, @NonNull Stone promotionStone) {
        this(fromPosition, Chess.coorToSqi(fromCol, fromRow), Chess.coorToSqi(toCol, toRow), promotionStone);
    }

    public Move(@NonNull Position fromPosition, int fromSqi, int toSqi) {
        this(fromPosition, fromSqi, toSqi, Stone.NO_STONE);
    }

    public Move(@NonNull Position fromPosition, int fromSqi, int toSqi, Stone promotionStone) {
        if (fromSqi < 0 || fromSqi >= 64 || toSqi < 0 || toSqi >= 64) {
            // Check if the null move
            if (!(fromSqi == Chess.NO_SQUARE && toSqi == Chess.NO_SQUARE && promotionStone == Stone.NO_STONE)) {
                throw new IllegalArgumentException("Not a valid move");
            }
        }

        this.fromSqi = fromSqi;
        this.toSqi = toSqi;
        this.promotionStone = promotionStone;

        this.fromPosition = fromPosition;
        this.castles = Math.abs(fromSqi - toSqi) == 16 && fromPosition.stoneAt(fromSqi).toPiece() == KING;
    }

    private Move(@NonNull Position fromPosition, int fromSqi, int toSqi, boolean castles) {
        this.fromSqi = fromSqi;
        this.toSqi = toSqi;
        this.promotionStone = Stone.NO_STONE;

        this.fromPosition = fromPosition;
        this.castles = castles;
    }

    /**
     * Creates a move representing long (queenside, a-side) castles
     * @param fromPosition the position to castle in
     * @return a long castle move
     */
    public static Move longCastles(@NonNull Position fromPosition) {
        int sp = fromPosition.chess960StartPosition();
        Player player = fromPosition.playerToMove();
        return new Move(fromPosition, Chess960.getKingSqi(sp, player),
                16 + (player == Player.WHITE ? 0 : 7), true);
    }

    /**
     * Creates a move representing short (kingside, h-side) castles
     * @param fromPosition the position to castle in
     * @return a short castle move
     */
    public static Move shortCastles(@NonNull Position fromPosition) {
        int sp = fromPosition.chess960StartPosition();
        Player player = fromPosition.playerToMove();
        return new Move(fromPosition, Chess960.getKingSqi(sp, player),
                48 + (player == Player.WHITE ? 0 : 7), true);
    }

    /**
     * Creates a null move
     * @param fromPosition the position to to a null move
     * @return a null move
     */
    public static Move nullMove(@NonNull Position fromPosition) {
        return new Move(fromPosition, Chess.NO_SQUARE, Chess.NO_SQUARE);
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

    public Position position() { return fromPosition; }

    public boolean isNullMove() {
        return fromSqi == Chess.NO_SQUARE;
    }

    public boolean isCapture() {
        if (isNullMove() || isCastle()) return false;
        if (isCapture == null) {
            isCapture = !fromPosition.stoneAt(toSqi()).isNoStone() || isEnPassant();
        }
        return isCapture;
    }

    public Stone movingStone() {
        if (isNullMove()) return Stone.NO_STONE;
        return fromPosition.stoneAt(fromSqi());
    }

    public Piece movingPiece() {
        return movingStone().toPiece();
    }

    public Stone capturedStone() {
        if (isCapture()) {
            if (isEnPassant()) {
                return Piece.PAWN.toStone(fromPosition.playerToMove().otherPlayer());
            }
            return fromPosition.stoneAt(toSqi());
        }
        return Stone.NO_STONE;
    }

    public Piece capturedPiece() {
        return capturedStone().toPiece();
    }

    public boolean isShortCastle() {
        return castles && Chess.sqiToCol(toSqi) == 6;
    }

    public boolean isLongCastle() {
        return castles && Chess.sqiToCol(toSqi) == 2;
    }

    public boolean isCastle() {
        return castles;
    }

    public boolean isEnPassant() {
        return movingPiece() == PAWN && Chess.deltaCol(fromSqi(), toSqi()) != 0
                && fromPosition.stoneAt(toSqi()).isNoStone();
    }

    public boolean isCheck() {
        if (isCheck == null) {
            isCheck = fromPosition.doMove(this).isCheck();
        }
        return isCheck;
    }

    public boolean isMate() {
        if (isMate == null) {
            isMate = fromPosition.doMove(this).isMate();
        }
        return isMate;
    }

    public String toSAN() {
        return toAlgebraicNotation(-1, true);
    }

    public String toSAN(int ply) {
        return toAlgebraicNotation(ply, true);
    }

    public String toLAN() {
        return toAlgebraicNotation(-1, false);
    }

    public String toLAN(int ply) {
        return toAlgebraicNotation(ply, false);
    }

    private String toAlgebraicNotation(int ply, boolean shortNotation) {
        StringBuilder sb = new StringBuilder();
        if (isNullMove()) {
            sb.append("--");
        } else if (isShortCastle()) {
            sb.append("O-O");
        } else if (isLongCastle()) {
            sb.append("O-O-O");
        } else {
            Piece piece = movingPiece();
            if (shortNotation) {
                // SAN
                if (sanCache == null) {
                    if (piece == PAWN) {
                        if (isCapture()) {
                            sb.append(Chess.colToChar(Chess.sqiToCol(fromSqi())));
                        }
                    } else {
                        sb.append(piece.toChar());
                        // TODO: A bit overkill to generate all legal moves; could be made faster
                        boolean colUnique = true, rowUnique = true, destUnique = true;
                        for (Move move : fromPosition.generateAllLegalMoves()) {
                            if (move.equals(this)) continue;
                            if (move.movingPiece() == piece && move.toSqi() == toSqi()) {
                                destUnique = false;
                                if (Chess.sqiToCol(move.fromSqi()) == Chess.sqiToCol(fromSqi())) {
                                    colUnique = false;
                                }
                                if (Chess.sqiToRow(move.fromSqi()) == Chess.sqiToRow(fromSqi())) {
                                    rowUnique = false;
                                }
                            }
                        }
                        if (!destUnique) {
                            if (colUnique) {
                                sb.append(Chess.colToChar(Chess.sqiToCol(fromSqi())));
                            } else if (rowUnique) {
                                sb.append(Chess.rowToChar(Chess.sqiToRow(fromSqi())));
                            } else {
                                sb.append(Chess.sqiToStr(fromSqi()));
                            }
                        }
                    }
                    sanCache = sb.toString();
                } else {
                    sb.append(sanCache);
                }
            } else {
                // LAN
                if (piece != PAWN) {
                    sb.append(piece.toChar());
                }
                sb.append(Chess.sqiToStr(fromSqi()));
            }
            if (isCapture()) {
                sb.append('x');
            } else if (!shortNotation) {
                sb.append('-');
            }
            sb.append(Chess.sqiToStr(toSqi()));
            if (promotionStone() != Stone.NO_STONE) {
                sb.append('=').append(promotionStone().toPiece().toChar());
            }
        }

        if (isMate()) {
            sb.append('#');
        } else if (isCheck()) {
            sb.append('+');
        }

        StringBuilder sb2 = new StringBuilder();
        if (ply >= 0) {
            sb2.append(Chess.plyToMoveNumber(ply));
            sb2.append('.');
            if (!Chess.isWhitePly(ply)) sb2.append("..");
        }
        sb2.append(sb.toString());
        return sb2.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Move)) return false;

        Move move = (Move) o;

        if (fromSqi != move.fromSqi) return false;
        if (toSqi != move.toSqi) return false;
        if (castles != move.castles) return false;
        return promotionStone == move.promotionStone;

    }

    @Override
    public int hashCode() {
        int result = fromPosition.hashCode();
        result = 31 * result + fromSqi;
        result = 31 * result + toSqi;
        result = 31 * result + promotionStone.hashCode();
        result = 31 * result + (castles ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return toLAN();
    }
}
