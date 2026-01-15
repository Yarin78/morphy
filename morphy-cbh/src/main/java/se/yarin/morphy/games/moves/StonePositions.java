package se.yarin.morphy.games.moves;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.*;

import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Player.WHITE;

/**
 * An internal representation of where the stones are on the board. This can't be determined from
 * {@link Position} as the internal order of the stones must is important for the {@link
 * MoveSerializer}.
 *
 * <p>If there are more than 3 knights, bishops, rooks or queens, those piece will silently be
 * ignored. Referring to those pieces are done in a special way in {@link MoveSerializer}.
 *
 * <p>This class is immutable.
 */
class StonePositions {
  // For every stone, there's a list of square indexes
  private final int[] pieceSqi;
  private static final int[] stoneOffset = {0, 1, 4, 7, 10, 13, 21, 21, 29, 32, 35, 38, 41, 42};

  private StonePositions(int[] pieceSqi) {
    this.pieceSqi = pieceSqi;
  }

  public static StonePositions fromPosition(Position position) {
    return fromPosition(position, false);
  }

  /** Initializes a {@link StonePositions} from a board position. */
  public static StonePositions fromPosition(Position position, boolean reverse) {
    int[] pps = new int[42];
    for (int i = 0; i < 42; i++) {
      pps[i] = -1;
    }

    for (int ii = 0; ii < 64; ii++) {
      int i = reverse ? 63 - ii : ii;
      Stone stone = position.stoneAt(i);
      if (!stone.isNoStone()) {
        int from = stoneOffset[stone.index()], to = stoneOffset[stone.index() + 1];
        for (int j = from; j < to; j++) {
          if (pps[j] == -1) {
            pps[j] = i;
            break;
          }
        }
      }
    }
    return new StonePositions(pps);
  }

  private int[] cloneData() {
    return pieceSqi.clone();
  }

  /**
   * Gets the square of a specific stone give the stoneNo
   *
   * @param stone the stone to get
   * @param stoneNo the stone number
   * @return the square for this stone, or -1 if no stone with this stoneNo on the board
   */
  public int getSqi(Stone stone, int stoneNo) {
    int from = stoneOffset[stone.index()], to = stoneOffset[stone.index() + 1];
    if (stoneNo >= 0 && stoneNo < to - from) {
      return pieceSqi[from + stoneNo];
    }
    return -1;
  }

  /**
   * Finds which stoneNo is on a given square
   *
   * @param stone the stone to look for
   * @param sqi the square to check
   * @return the stone number, or -1 if the given stone isn't on the given square
   */
  public int getStoneNo(Stone stone, int sqi) {
    int from = stoneOffset[stone.index()], to = stoneOffset[stone.index() + 1];
    for (int j = from; j < to; j++) {
      if (pieceSqi[j] == sqi) return j - from;
    }
    return -1;
  }

  /**
   * Moves a piece and returns a new instance with correct stone positions. No checking is done if
   * the move is a valid chess move.
   *
   * @param move the move to make
   * @return the updated position
   */
  public StonePositions doMove(@NotNull Move move) {
    if (move.isNullMove()) {
      return this;
    }

    int[] pieces = cloneData();
    Stone stone = move.movingStone();
    int stoneNo = getStoneNo(stone, move.fromSqi());
    if (stoneNo >= 0) {
      pieces[stoneOffset[stone.index()] + stoneNo] = move.toSqi();
    }

    // In case of pawn promotion, the pawn must be removed and the promoted piece added
    if (move.promotionStone() != Stone.NO_STONE && stone.toPiece() == Piece.PAWN) {
      // Remove the pawn (pawn positions are not adjusted)
      pieces[stoneOffset[stone.index()] + stoneNo] = -1;

      // Add the promotion piece by finding the first available position for that stone
      // If there are no available positions, the new piece will not be added (which is okay)
      int from = stoneOffset[move.promotionStone().index()],
          to = stoneOffset[move.promotionStone().index() + 1];
      for (int j = from; j < to; j++) {
        if (pieces[j] == -1) {
          pieces[j] = move.toSqi();
          break;
        }
      }
    }

    // In case of castle, we need to update the rook position as well
    if (move.isCastle()) {
      int rookFromSqi, rookToSqi, sp = move.position().chess960StartPosition();
      Player toMove = stone.toPlayer();
      if (move.isShortCastle()) {
        rookFromSqi = Chess960.getHRookSqi(sp, toMove);
        rookToSqi = toMove == WHITE ? F1 : F8;
      } else {
        rookFromSqi = Chess960.getARookSqi(sp, toMove);
        rookToSqi = toMove == WHITE ? D1 : D8;
      }

      Stone rook = Piece.ROOK.toStone(toMove);
      int rookNo = getStoneNo(rook, rookFromSqi);
      // This can probably be -1 in case of a setup position with more than 3 rooks and castling
      // still allowed...
      if (rookNo >= 0) {
        pieces[stoneOffset[rook.index()] + rookNo] = rookToSqi;
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
      int removeFrom = stoneOffset[capturedStone.index()],
          removeTo = stoneOffset[capturedStone.index() + 1];

      // If it's a pawn, just remove it
      if (capturedStone.toPiece() == Piece.PAWN) {
        pieces[removeFrom + pno] = -1;
      } else {
        // Otherwise we must adjust the pieces (shift left)
        int i = removeFrom, j = removeFrom;
        while (i < removeTo) {
          if (pieces[i] != captureSqi) {
            pieces[j++] = pieces[i++];
          } else {
            i++;
          }
        }
        while (j < removeTo) {
          pieces[j++] = -1;
        }
      }
    }

    return new StonePositions(pieces);
  }

  void validate(@NotNull Position position) {
    // Verify that all pieces are accounted for in the given position and vice versa
    // Only for debugging!
    int piecesFound = 0, piecesOnBoard = 0;
    for (Stone stone : Stone.values()) {
      if (stone.isNoStone()) continue;
      boolean endReached = false;
      int from = stoneOffset[stone.index()], to = stoneOffset[stone.index() + 1];
      for (int i = from; i < to; i++) {
        int sqi = pieceSqi[i];
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
