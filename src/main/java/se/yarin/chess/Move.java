package se.yarin.chess;

import lombok.NonNull;

import static se.yarin.chess.Piece.*;

/**
 * A more complete version of a chess move, containing information to
 * generate the SAN and LAN notation of the move.
 *
 * Move is immutable.
 */
public class Move extends ShortMove {
    private final Position fromPosition; // Not used in equals or hashcode

    // These are caches
    private Boolean isCapture, isCheck, isMate;
    private String sanCache;

    public Move(@NonNull Position fromPosition, int fromCol, int fromRow, int toCol, int toRow) {
        super(fromCol, fromRow, toCol, toRow);
        this.fromPosition = fromPosition;
    }

    public Move(@NonNull Position fromPosition, int fromCol, int fromRow, int toCol, int toRow, @NonNull Stone promotionStone) {
        super(fromCol, fromRow, toCol, toRow, promotionStone);
        this.fromPosition = fromPosition;
    }

    public Move(@NonNull Position fromPosition, int fromSqi, int toSqi) {
        super(fromSqi, toSqi);
        this.fromPosition = fromPosition;
    }

    public Move(@NonNull Position fromPosition, int fromSqi, int toSqi, Stone promotionStone) {
        super(fromSqi, toSqi, promotionStone);
        this.fromPosition = fromPosition;
    }

    public static Move nullMove(@NonNull Position fromPosition) {
        return new Move(fromPosition, Chess.NO_SQUARE, Chess.NO_SQUARE);
    }

    public boolean isCapture() {
        if (isNullMove()) return false;
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
        return movingPiece() == KING && Chess.deltaCol(fromSqi(), toSqi()) == 2;
    }

    public boolean isLongCastle() {
        return movingPiece() == KING && Chess.deltaCol(fromSqi(), toSqi()) == -2;
    }

    public boolean isCastle() {
        return isShortCastle() || isLongCastle();
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
    public String toString() {
        return toLAN();
    }
}
