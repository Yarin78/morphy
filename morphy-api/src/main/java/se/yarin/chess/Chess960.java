package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** Chess960 specifics */
public final class Chess960 {

  // See https://en.wikipedia.org/wiki/Chess960_numbering_scheme
  private static final String kingsTable[] =
      new String[] {
        "QNNRKR", "NQNRKR", "NNQRKR", "NNRQKR", "NNRKQR", "NNRKRQ", "QNRNKR", "NQRNKR",
        "NRQNKR", "NRNQKR", "NRNKQR", "NRNKRQ", "QNRKNR", "NQRKNR", "NRQKNR", "NRKQNR",
        "NRKNQR", "NRKNRQ", "QNRKRN", "NQRKRN", "NRQKRN", "NRKQRN", "NRKRQN", "NRKRNQ",
        "QRNNKR", "RQNNKR", "RNQNKR", "RNNQKR", "RNNKQR", "RNNKRQ", "QRNKNR", "RQNKNR",
        "RNQKNR", "RNKQNR", "RNKNQR", "RNKNRQ", "QRNKRN", "RQNKRN", "RNQKRN", "RNKQRN",
        "RNKRQN", "RNKRNQ", "QRKNNR", "RQKNNR", "RKQNNR", "RKNQNR", "RKNNQR", "RKNNRQ",
        "QRKNRN", "RQKNRN", "RKQNRN", "RKNQRN", "RKNRQN", "RKNRNQ", "QRKRNN", "RQKRNN",
        "RKQRNN", "RKRQNN", "RKRNQN", "RKRNNQ"
      };

  private static final String bishopsTable[] =
      new String[] {
        "BB------", "B--B----", "B----B--", "B------B",
        "-BB-----", "--BB----", "--B--B--", "--B----B",
        "-B--B---", "---BB---", "----BB--", "----B--B",
        "-B----B-", "---B--B-", "-----BB-", "------BB"
      };

  public static final int REGULAR_CHESS_SP = 518;

  /** The value of the PGN {@code Variant} tag, and of the game DTO's variant, for Chess960. */
  public static final String VARIANT = "Chess960";

  /**
   * Whether a PGN {@code Variant} tag value names Chess960, allowing the spellings found in the
   * wild ("Chess960", "chess 960", "Fischerandom", ...).
   */
  public static boolean isVariant(@Nullable String variant) {
    if (variant == null) {
      return false;
    }
    String v = variant.toLowerCase().replaceAll("[\\s_-]", "");
    return v.equals("chess960") || v.equals("fischerandom") || v.equals("fischerrandom");
  }

  private static Position sp[] = new Position[960];
  private static int pieceStartCol[][] = new int[960][];
  private static Map<String, Integer> firstRankToNo = new HashMap<>();

  static {
    Stone[][] ss = new Stone[960][];
    for (int no = 0; no < 960; no++) {
      Stone[] stones = new Stone[64];
      for (int i = 0; i < 64; i++) {
        stones[i] = Stone.NO_STONE;
      }
      for (int i = 0; i < 8; i++) {
        stones[i * 8 + 1] = Stone.WHITE_PAWN;
        stones[i * 8 + 6] = Stone.BLACK_PAWN;
      }

      String kt = kingsTable[no / 16], bt = bishopsTable[no % 16];
      int rookNo = 0;
      pieceStartCol[no] = new int[3];
      StringBuilder firstRank = new StringBuilder();
      for (int i = 0, j = 0; i < 8; i++) {
        Piece p = Piece.BISHOP;
        if (bt.charAt(i) != 'B') {
          char ch = kt.charAt(j++);
          firstRank.append(ch);
          p = Piece.fromChar(ch);
        } else {
          firstRank.append('B');
        }
        stones[i * 8] = p.toStone(Player.WHITE);
        stones[i * 8 + 7] = p.toStone(Player.BLACK);
        if (p == Piece.ROOK) {
          pieceStartCol[no][rookNo++] = i;
        } else if (p == Piece.KING) {
          pieceStartCol[no][2] = i;
        }
      }

      firstRankToNo.put(firstRank.toString(), no);
      ss[no] = stones;
    }

    // Creating a new Position must be done after setting pieceStartCol since it depends on that
    // Also ensure that the JIT doesn't try to reorder things
    for (int no = 0; no < 960; no++) {
      sp[no] = new Position(ss[no], Player.WHITE, EnumSet.allOf(Castles.class), -1, no);
    }
  }

  /**
   * Gets the start position of a Chess960 game
   *
   * @param no the start position number (between 0 and 959)
   * @return the start position for the specified Chess960 game
   */
  public static Position getStartPosition(int no) {
    if (no < 0 || no >= 960) {
      throw new IllegalArgumentException("Start position must be between 0 and 959");
    }

    return sp[no];
  }

  /**
   * Gets the start position number from the position of the pieces on the first rank
   *
   * @param firstRank an eight character string representing the pieces on the first rank (case
   *     insensitive)
   * @return the position number, between 0 and 959
   * @throws IllegalArgumentException if firstRank is not a valid Chess960 start position
   */
  public static int getStartPositionNo(@NotNull String firstRank) {
    Integer no = firstRankToNo.get(firstRank.toUpperCase());
    if (no == null) throw new IllegalArgumentException("Invalid Chess960 first rank: " + firstRank);
    return no;
  }

  /**
   * Gets the start position number from the position of the pieces
   *
   * @param stones the start position
   * @return the position number, between 0 and 959
   * @throws IllegalArgumentException if stones is not a valid Chess960 start position
   */
  public static int getStartPositionNo(@NotNull Stone[] stones) {
    if (stones.length != 64) {
      throw new IllegalArgumentException("stones must contain 64 elements");
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      sb.append(stones[i * 8].toChar());
    }
    return getStartPositionNo(sb.toString());
  }

  /**
   * Determines the start position number of a Chess960 game from a position in it, as when reading
   * a FEN, which only says which castling rights remain. It is the start position that the pieces
   * on the first rank form, if they form one that agrees with the castling rights; otherwise the
   * first start position whose king and rook squares agree with them. The same heuristic is used
   * when a stored start position number turns out to be wrong.
   *
   * @param stones the position, 64 elements
   * @param castles the castling rights in the position
   * @return the position number, between 0 and 959
   * @throws IllegalArgumentException if no start position agrees with the castling rights
   */
  public static int getStartPositionNo(@NotNull Stone[] stones, @NotNull EnumSet<Castles> castles) {
    try {
      int no = getStartPositionNo(stones);
      if (agreesWithCastles(no, stones, castles)) {
        return no;
      }
    } catch (IllegalArgumentException ignored) {
      // The first rank is not a start position
    }
    for (int no = 0; no < 960; no++) {
      if (agreesWithCastles(no, stones, castles)) {
        return no;
      }
    }
    throw new IllegalArgumentException(
        "No Chess960 start position agrees with the castling rights " + castles);
  }

  private static boolean agreesWithCastles(int no, Stone[] stones, EnumSet<Castles> castles) {
    for (Castles castle : castles) {
      Player player =
          castle == Castles.WHITE_SHORT_CASTLE || castle == Castles.WHITE_LONG_CASTLE
              ? Player.WHITE
              : Player.BLACK;
      boolean shortCastle =
          castle == Castles.WHITE_SHORT_CASTLE || castle == Castles.BLACK_SHORT_CASTLE;
      int rookSqi = shortCastle ? getHRookSqi(no, player) : getARookSqi(no, player);
      Stone king = player == Player.WHITE ? Stone.WHITE_KING : Stone.BLACK_KING;
      Stone rook = player == Player.WHITE ? Stone.WHITE_ROOK : Stone.BLACK_ROOK;
      if (stones[getKingSqi(no, player)] != king || stones[rookSqi] != rook) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the start square for the a-rook in a Chess960 game
   *
   * @param no the Chess960 start position
   * @param player the side to move
   * @return the sqi where the first rook starts at
   */
  public static int getARookSqi(int no, Player player) {
    return getPieceStartSqi(no, player, 0);
  }

  /**
   * Gets the start square for the h-rook in a Chess960 game
   *
   * @param no the Chess960 start position
   * @param player the side to move
   * @return the sqi where the second rook starts at
   */
  public static int getHRookSqi(int no, Player player) {
    return getPieceStartSqi(no, player, 1);
  }

  /**
   * Gets the start square for the king in a Chess960 game
   *
   * @param no the Chess960 start position
   * @param player the side to move
   * @return the sqi where the king starts at
   */
  public static int getKingSqi(int no, Player player) {
    return getPieceStartSqi(no, player, 2);
  }

  private static int getPieceStartSqi(int no, Player player, int pieceNo) {
    return 8 * pieceStartCol[no][pieceNo] + (player == Player.WHITE ? 0 : 7);
  }
}
