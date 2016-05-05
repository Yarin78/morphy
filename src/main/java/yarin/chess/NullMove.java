package yarin.chess;

/**
 * Created by yarin on 03/05/16.
 */
public class NullMove extends Move {
    public NullMove() {
        super(Piece.PieceType.EMPTY, 0, 0, 0, 0, false);
    }
}
