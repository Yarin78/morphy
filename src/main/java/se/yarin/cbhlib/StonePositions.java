package se.yarin.cbhlib;

import se.yarin.chess.Piece;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;

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
     * Gets all the squares the given stone is on, in stoneNo order
     * @param stone the stone to get squares for
     * @return an array containing the square indexes of the given stone
     */
    public int[] getSqis(Stone stone) {
        // We need to clone here to ensure immutability
        return pieceSqi[stone.index()].clone();
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
     * Removes a stone from the position and returns the updated position
     * @param stone the stone to remove
     * @param sqi the square it should be removed from
     * @return the update position
     */
    public StonePositions remove(Stone stone, int sqi) {
        int pno = getStoneNo(stone, sqi);
        if (pno < 0) {
            // This can happen if e.g. the fourth queen is captured
            return this;
        }
        int[][] pieces = cloneData();
        int[] removeStone = pieces[stone.index()];

        // If it's a pawn, just remove it
        if (stone.toPiece() == Piece.PAWN) {
            removeStone[pno] = -1;
        } else {
            // Otherwise we must adjust the pieces
            int i = 0, j = 0;
            while (i < removeStone.length) {
                if (removeStone[i] != sqi) {
                    removeStone[j++] = removeStone[i++];
                } else {
                    i++;
                }
            }
            while (j < removeStone.length) {
                removeStone[j++] = -1;
            }
        }
        return new StonePositions(pieces);
    }

    /**
     * Changes the square of a stone and returns the updated position
     * @param stone the type of stone to update
     * @param stoneNo the stone number
     * @param sqi the new square for the stone
     * @return the updated position
     */
    public StonePositions move(Stone stone, int stoneNo, int sqi) {
        int[][] pieces = cloneData();
        pieces[stone.index()][stoneNo] = sqi;
        return new StonePositions(pieces);
    }

    /**
     * Adds a new stone and returns the updated position
     * @param stone the stone to add
     * @param sqi the square to add it to
     * @return the updated position
     */
    public StonePositions add(Stone stone, int sqi) {
        int[][] pieces = cloneData();
        int[] pp = pieces[stone.index()];
        for (int j = 0; j < pp.length; j++) {
            if (pp[j] == -1) {
                pp[j] = sqi;
                return new StonePositions(pieces);
            }
        }
        // We can return this instance since it's an immutable data structure
        return this;
    }

}
