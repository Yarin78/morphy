package se.yarin.chess;

import java.util.Arrays;
import java.util.EnumSet;
import org.jetbrains.annotations.NotNull;

/**
 * A board of normal chess played through move by move, its Zobrist hash kept up to date: the same
 * hash {@link Position#getZobristHashLo()} gives the same position, worked out with a few
 * operations per move instead of a new position. For going through many games fast, as when
 * indexing them. It doesn't check that the moves are legal.
 */
public final class HashingBoard {
  // The ordinals of the stones
  private static final int EMPTY = Stone.NO_STONE.ordinal();
  private static final int WHITE_PAWN = Stone.WHITE_PAWN.ordinal();
  private static final int BLACK_PAWN = Stone.BLACK_PAWN.ordinal();
  private static final int WHITE_KING = Stone.WHITE_KING.ordinal();
  private static final int BLACK_KING = Stone.BLACK_KING.ordinal();
  private static final int WHITE_ROOK = Stone.WHITE_ROOK.ordinal();
  private static final int BLACK_ROOK = Stone.BLACK_ROOK.ordinal();
  private static final Stone[] STONES = Stone.values();

  // The castling rights a move from or to a square leaves, as in Position's mask: 1 white O-O,
  // 2 white O-O-O, 4 black O-O, 8 black O-O-O
  private static final int[] RIGHTS_LEFT = new int[64];

  static {
    Arrays.fill(RIGHTS_LEFT, 15);
    RIGHTS_LEFT[Chess.E1] = ~3 & 15;
    RIGHTS_LEFT[Chess.H1] = ~1 & 15;
    RIGHTS_LEFT[Chess.A1] = ~2 & 15;
    RIGHTS_LEFT[Chess.E8] = ~12 & 15;
    RIGHTS_LEFT[Chess.H8] = ~4 & 15;
    RIGHTS_LEFT[Chess.A8] = ~8 & 15;
  }

  private final byte[] board = new byte[64];
  private boolean whiteToMove = true;
  private int castles = 15;
  // The file a pawn just moved two squares on, or -1; and that file as the hash has it
  private int enPassantFile = -1;
  private int hashedEnPassantFile = -1;
  private long hash;

  /** A board in the start position. */
  public HashingBoard() {
    Position start = Position.start();
    for (int sqi = 0; sqi < 64; sqi++) {
      board[sqi] = (byte) start.stoneAt(sqi).ordinal();
    }
    hash = start.getZobristHashLo();
  }

  /** The hash of the position, the same as {@link Position#getZobristHashLo()}'s. */
  public long hash() {
    return hash;
  }

  public boolean whiteToMove() {
    return whiteToMove;
  }

  public @NotNull Stone stoneAt(int sqi) {
    return STONES[board[sqi]];
  }

  private void set(int sqi, int stone) {
    hash ^= Zobrist.PIECE_LO[board[sqi]][sqi] ^ Zobrist.PIECE_LO[stone][sqi];
    board[sqi] = (byte) stone;
  }

  /**
   * Plays a move: castling given as the king's move two squares, en passant as the pawn's
   * capture.
   *
   * @param promotion the piece a pawn promotes to, or {@link Piece#NO_PIECE}
   */
  public void play(int fromSqi, int toSqi, @NotNull Piece promotion) {
    int stone = board[fromSqi];
    int fromCol = fromSqi >> 3, toCol = toSqi >> 3, toRow = toSqi & 7;
    int newEnPassantFile = -1;
    if ((stone == WHITE_KING || stone == BLACK_KING) && Math.abs(toCol - fromCol) == 2) {
      // Castling: the rook comes along
      int row = fromSqi & 7;
      int rook = stone == WHITE_KING ? WHITE_ROOK : BLACK_ROOK;
      int rookFrom = (toCol == 6 ? 7 : 0) * 8 + row, rookTo = (toCol == 6 ? 5 : 3) * 8 + row;
      set(rookFrom, EMPTY);
      set(rookTo, rook);
    } else if (stone == WHITE_PAWN || stone == BLACK_PAWN) {
      if (fromCol != toCol && board[toSqi] == EMPTY) {
        // En passant: the pawn taken is beside the one taking
        set(toCol * 8 + (fromSqi & 7), EMPTY);
      } else if (Math.abs(toRow - (fromSqi & 7)) == 2) {
        newEnPassantFile = fromCol;
      }
      if (promotion != Piece.NO_PIECE) {
        stone = promotion.toStone(whiteToMove ? Player.WHITE : Player.BLACK).ordinal();
      }
    }
    set(fromSqi, EMPTY);
    set(toSqi, stone);
    int newCastles = castles & RIGHTS_LEFT[fromSqi] & RIGHTS_LEFT[toSqi];
    if (newCastles != castles) {
      hash ^= Zobrist.CASTLE_LO[castles] ^ Zobrist.CASTLE_LO[newCastles];
      castles = newCastles;
    }
    turn(newEnPassantFile);
  }

  /** Plays a null move: the other side is to move. */
  public void playNull() {
    turn(-1);
  }

  /** Hands the move to the other side, with the en passant file the move left. */
  private void turn(int newEnPassantFile) {
    hash ^= Zobrist.TO_MOVE_LO[whiteToMove ? 1 : 0] ^ Zobrist.TO_MOVE_LO[whiteToMove ? 0 : 1];
    whiteToMove = !whiteToMove;
    enPassantFile = newEnPassantFile;
    // As Position: the file counts only if a pawn of the side to move stands beside the pawn
    int hashed = -1;
    if (newEnPassantFile >= 0) {
      int row = whiteToMove ? 4 : 3;
      int pawn = whiteToMove ? WHITE_PAWN : BLACK_PAWN;
      if ((newEnPassantFile > 0 && board[(newEnPassantFile - 1) * 8 + row] == pawn)
          || (newEnPassantFile < 7 && board[(newEnPassantFile + 1) * 8 + row] == pawn)) {
        hashed = newEnPassantFile;
      }
    }
    if (hashed != hashedEnPassantFile) {
      hash ^= Zobrist.EN_PASSANT_LO[hashedEnPassantFile + 1] ^ Zobrist.EN_PASSANT_LO[hashed + 1];
      hashedEnPassantFile = hashed;
    }
  }

  /** The position on the board. */
  public @NotNull Position toPosition() {
    Stone[] stones = new Stone[64];
    for (int sqi = 0; sqi < 64; sqi++) {
      stones[sqi] = STONES[board[sqi]];
    }
    EnumSet<Castles> rights = EnumSet.noneOf(Castles.class);
    if ((castles & 1) != 0) rights.add(Castles.WHITE_SHORT_CASTLE);
    if ((castles & 2) != 0) rights.add(Castles.WHITE_LONG_CASTLE);
    if ((castles & 4) != 0) rights.add(Castles.BLACK_SHORT_CASTLE);
    if ((castles & 8) != 0) rights.add(Castles.BLACK_LONG_CASTLE);
    return new Position(stones, whiteToMove ? Player.WHITE : Player.BLACK, rights, enPassantFile);
  }
}
