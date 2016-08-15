package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.chess.*;

/**
 * An internal representation of where the stones are on the board.
 * This can't be determined from {@link Position} as the internal order of the stones
 * must is important for the {@link MovesParser}.
 * <p>
 * If there are more than 3 knights, bishops, rooks or queens, those piece will silently be ignored.
 * Referring to those pieces are done in a special way in {@link MovesParser}.
 * </p>
 * <p>
 * This class is immutable.
 * </p>
 */
class StonePositions {
    // For every stone, there's a list of square indexes
    private int[][] pieceSqi;

    private StonePositions(int[][] pieceSqi) {
        this.pieceSqi = pieceSqi;
    }

    /**
     * Initializes a {@link StonePositions} from a board position.
     */
    public static StonePositions fromPosition(Position position) {
        int[][] pps = new int[13][];
        for (Stone stone : Stone.values()) {
            int cnt;
            switch (stone.toPiece()) {
                case PAWN: cnt = 8; break;
                case KING: cnt = 1; break;
                default  : cnt = 3; break;
            }
            pps[stone.index()] = new int[cnt];
            for (int i = 0; i < cnt; i++) {
                pps[stone.index()][i] = -1;
            }
        }

        for (int i = 0; i < 64; i++) {
            Stone stone = position.stoneAt(i);
            if (!stone.isNoStone()) {
                int[] pp = pps[stone.index()];
                for (int j = 0; j < pp.length; j++) {
                    if (pp[j] == -1) {
                        pp[j] = i;
                        break;
                    }
                }
            }
        }
        return new StonePositions(pps);
    }

    private int[][] cloneData() {
        int[][] a = new int[pieceSqi.length][];
        for (int i = 0; i < a.length; i++) {
            a[i] = pieceSqi[i].clone();
        }
        return a;
    }

    /**
     * Gets the square of a specific stone give the stoneNo
     * @param stone the stone to get
     * @param stoneNo the stone number
     * @return the square for this stone, or -1 if no stone with this stoneNo on the board
     */
    public int getSqi(Stone stone, int stoneNo) {
        if (stoneNo >= 0 && stoneNo < pieceSqi[stone.index()].length) {
            return pieceSqi[stone.index()][stoneNo];
        }
        return -1;
    }

    /**
     * Finds which stoneNo is on a given square
     * @param stone the stone to look for
     * @param sqi the square to check
     * @return the stone number, or -1 if the given stone isn't on the given square
     */
    public int getStoneNo(Stone stone, int sqi) {
        int[] pp = pieceSqi[stone.index()];
        for (int j = 0; j < pp.length; j++) {
            if (pp[j] == sqi) return j;
        }
        return -1;
    }

    /**
     * Moves a piece and returns a new instance with correct stone positions.
     * No checking is done if the move is a valid chess move.
     * @param move the move to make
     * @return the updated position
     */
    public StonePositions doMove(@NonNull Move move) {
        if (move.isNullMove()) {
            return this;
        }

        int[][] pieces = cloneData();
        Stone stone = move.movingStone();
        int stoneNo = getStoneNo(stone, move.fromSqi());
        if (stoneNo >= 0) {
            pieces[stone.index()][stoneNo] = move.toSqi();
        }

        // In case of pawn promotion, the pawn must be removed and the promoted piece added
        if (move.promotionStone() != Stone.NO_STONE && stone.toPiece() == Piece.PAWN) {
            // Remove the pawn (pawn positions are not adjusted)
            pieces[stone.index()][stoneNo] = -1;

            // Add the promotion piece by finding the first available position for that stone
            // If there are no available positions, the new piece will not be added (which is okay)
            int[] pp = pieces[move.promotionStone().index()];
            for (int j = 0; j < pp.length; j++) {
                if (pp[j] == -1) {
                    pp[j] = move.toSqi();
                    break;
                }
            }
        }

        // In case of castle, we need to update the rook position as well
        if (move.isCastle()) {
            int rookX1 = move.toCol() == 6 ? 7 : 0;
            int rookX2 = (move.fromCol() + move.toCol()) / 2;
            int rookY = stone.hasPlayer(Player.WHITE) ? 0 : 7;
            int rookFromSqi = Chess.coorToSqi(rookX1, rookY);
            int rookToSqi = Chess.coorToSqi(rookX2, rookY);
            Stone rook = Piece.ROOK.toStone(stone.toPlayer());
            int rookNo = getStoneNo(rook, rookFromSqi);
            // This can probably be -1 in case of a setup position with more than 3 rooks and castling still allowed...
            if (rookNo >= 0) {
                pieces[rook.index()][rookNo] = rookToSqi;
            }
        }

        // In case of a capture, we need to remove the capture piece
        if (move.isCapture()) {
            int captureSqi = move.toSqi();
            Stone capturedStone = move.capturedStone();
            if (move.isEnPassant()) {
                captureSqi = Chess.coorToSqi(move.toCol(), move.fromRow());
            }
            int pno = getStoneNo(capturedStone, captureSqi);
            int[] removeStone = pieces[capturedStone.index()];

            // If it's a pawn, just remove it
            if (capturedStone.toPiece() == Piece.PAWN) {
                removeStone[pno] = -1;
            } else {
                // Otherwise we must adjust the pieces (shift left)
                int i = 0, j = 0;
                while (i < removeStone.length) {
                    if (removeStone[i] != captureSqi) {
                        removeStone[j++] = removeStone[i++];
                    } else {
                        i++;
                    }
                }
                while (j < removeStone.length) {
                    removeStone[j++] = -1;
                }
            }
        }

        return new StonePositions(pieces);
    }

    void validate(@NonNull Position position) {
        // Verify that all pieces are accounted for in the given position and vice versa
        // Only for debugging!
        int piecesFound = 0, piecesOnBoard = 0;
        for (Stone stone : Stone.values()) {
            if (stone.isNoStone()) continue;
            boolean endReached = false;
            for (int sqi : pieceSqi[stone.index()]) {
                if (sqi < 0) {
                    endReached = true;
                } else {
                    if (endReached && stone.toPiece() != Piece.PAWN) {
                        throw new RuntimeException("Pieces not adjusted correctly");
                    }
                    if (position.stoneAt(sqi) != stone) {
                        throw new RuntimeException("Board is in inconsistent state");
                    }
                    piecesFound++;
                }
            }
            if (stone.toPiece() != Piece.PAWN) {
                // There may be more than 3 pieces of this color, count them off
                for (int sqi = 0; sqi < 64; sqi++) {
                    if (stone == position.stoneAt(sqi)) {
                        if (getStoneNo(stone, sqi) < 0) {
                            piecesFound++; // This is an extra piece
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 64; i++) {
            if (!position.stoneAt(i).isNoStone()) {
                piecesOnBoard++;
            }
        }
        if (piecesFound != piecesOnBoard) {
            throw new RuntimeException("Board is in inconsistent state"); // Some pieces are missing
        }
    }
}
