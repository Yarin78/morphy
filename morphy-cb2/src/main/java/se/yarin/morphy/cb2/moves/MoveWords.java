package se.yarin.morphy.cb2.moves;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Chess;
import se.yarin.chess.Chess960;
import se.yarin.chess.Move;
import se.yarin.chess.Piece;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;
import se.yarin.morphy.cb2.InvalidDataException;

/**
 * The 16-bit words the moves of a game are stored as.
 *
 * <p>Every move a piece could make on an empty board, with every piece it could capture and
 * promote to, is enumerated in a fixed order, so a word means the same move in any position: which
 * piece moves from where to where, and what it captures or promotes to. Words from {@code fffa} up
 * are markers, and the words just above the moves describe the pieces of a set-up position. See
 * format/v2/2-moves.md.
 */
public final class MoveWords {

  public static final int NULL_MOVE = 0xFFFA;
  public static final int START_POSITION_SECTION = 0xFFFB;
  public static final int MOVES_SECTION = 0xFFFC;
  public static final int MORE_ALTERNATIVES = 0xFFFD;
  public static final int END_OF_LINE = 0xFFFF;
  public static final int FIRST_MARKER = 0xFFFA;

  /** The first of the four castling words of normal chess. */
  public static final int FIRST_CASTLING = 0xB129;
  /** The first of the four castling words of Chess960 start position 0. */
  public static final int FIRST_CHESS960_CASTLING = 0xB12D;
  /** The first word of a piece placement in a set-up position. */
  public static final int FIRST_PLACEMENT = FIRST_CHESS960_CASTLING + 4 * 960;
  /** The first placement word of a white pawn. */
  public static final int FIRST_PAWN_PLACEMENT = FIRST_PLACEMENT + 640;
  /** One past the last placement word. */
  public static final int END_PLACEMENT = FIRST_PAWN_PLACEMENT + 96;

  // The order of the blocks of piece moves and of placements
  private static final Piece[] BLOCK_PIECES = {
    Piece.KING, Piece.QUEEN, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK
  };
  // The order of the captured pieces after "no capture"
  private static final Piece[] CAPTURED = {
    Piece.QUEEN, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK, Piece.PAWN
  };
  private static final Piece[] PROMOTIONS = {Piece.QUEEN, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK};

  private static final int[][] KING_STEPS = {
    {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
  };
  private static final int[][] KNIGHT_STEPS = {
    {-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}
  };
  private static final int[][] BISHOP_RAYS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
  private static final int[][] ROOK_RAYS = {{-1, 0}, {0, -1}, {1, 0}, {0, 1}};
  private static final int[][] QUEEN_RAYS = {
    {-1, -1}, {1, -1}, {1, 1}, {-1, 1}, {-1, 0}, {0, -1}, {1, 0}, {0, 1}
  };

  // Captured "piece" code of an en passant capture
  private static final int EN_PASSANT = 6;

  /**
   * What a move word stands for.
   *
   * @param stone the moving stone; the king for castling
   * @param from the origin square, or -1 for castling
   * @param to the destination square, or -1 for castling
   * @param captured 0 for no capture, 1-5 for a captured queen, knight, bishop, rook or pawn, 6
   *     for en passant
   * @param promotion the piece promoted to, or {@link Piece#NO_PIECE}
   * @param castles 0 if not castling, 1 for O-O-O and 2 for O-O
   * @param chess960 the Chess960 start position a castling word belongs to, or -1 for normal chess
   */
  public record Word(
      @NotNull Stone stone,
      int from,
      int to,
      int captured,
      @NotNull Piece promotion,
      int castles,
      int chess960) {}

  private static final Word[] WORDS;
  private static final Map<Integer, Integer> MOVE_TO_WORD = new HashMap<>();

  static {
    Word[] words = new Word[FIRST_PLACEMENT];
    int w = 1;
    for (Player player : new Player[] {Player.WHITE, Player.BLACK}) {
      for (Piece piece : BLOCK_PIECES) {
        Stone stone = piece.toStone(player);
        for (int sq = 0; sq < 64; sq++) {
          for (int dest : destinations(piece, sq)) {
            for (int captured = 0; captured <= 5; captured++) {
              words[w++] = new Word(stone, sq, dest, captured, Piece.NO_PIECE, 0, -1);
            }
          }
        }
      }
    }
    for (Player player : new Player[] {Player.WHITE, Player.BLACK}) {
      Stone stone = Piece.PAWN.toStone(player);
      boolean white = player == Player.WHITE;
      int step = white ? 1 : -1;
      int startRank = white ? 1 : 6, epRank = white ? 4 : 3, promotionRank = white ? 6 : 1;
      for (int x = 0; x < 8; x++) {
        for (int y = 1; y < 7; y++) {
          int sq = x * 8 + y;
          if (y == promotionRank) {
            for (Piece p : PROMOTIONS) {
              words[w++] = new Word(stone, sq, sq + step, 0, p, 0, -1);
            }
          } else {
            if (y == startRank) {
              words[w++] = new Word(stone, sq, sq + 2 * step, 0, Piece.NO_PIECE, 0, -1);
            }
            words[w++] = new Word(stone, sq, sq + step, 0, Piece.NO_PIECE, 0, -1);
          }
          for (int dx : new int[] {-1, 1}) {
            if (x + dx < 0 || x + dx > 7) {
              continue;
            }
            int dest = (x + dx) * 8 + y + step;
            if (y == promotionRank) {
              for (int captured = 1; captured <= 4; captured++) {
                for (Piece p : PROMOTIONS) {
                  words[w++] = new Word(stone, sq, dest, captured, p, 0, -1);
                }
              }
            } else {
              for (int captured = 1; captured <= 5; captured++) {
                words[w++] = new Word(stone, sq, dest, captured, Piece.NO_PIECE, 0, -1);
              }
              if (y == epRank) {
                words[w++] = new Word(stone, sq, dest, EN_PASSANT, Piece.NO_PIECE, 0, -1);
              }
            }
          }
        }
      }
    }
    if (w != FIRST_CASTLING) {
      throw new IllegalStateException("The move words don't end where castling begins: " + w);
    }
    for (int sp = -1; sp < 960; sp++) {
      for (Stone king : new Stone[] {Stone.WHITE_KING, Stone.BLACK_KING}) {
        words[w++] = new Word(king, -1, -1, 0, Piece.NO_PIECE, 1, sp);
        words[w++] = new Word(king, -1, -1, 0, Piece.NO_PIECE, 2, sp);
      }
    }
    WORDS = words;
    for (int i = 1; i < FIRST_CASTLING; i++) {
      Word word = words[i];
      MOVE_TO_WORD.put(key(word.stone(), word.from(), word.to(), word.captured(), word.promotion()), i);
    }
  }

  private MoveWords() {}

  private static int[] destinations(Piece piece, int sq) {
    int x = sq / 8, y = sq % 8;
    int[] out = new int[64];
    int n = 0;
    if (piece == Piece.KING || piece == Piece.KNIGHT) {
      for (int[] d : piece == Piece.KING ? KING_STEPS : KNIGHT_STEPS) {
        int nx = x + d[0], ny = y + d[1];
        if (nx >= 0 && nx < 8 && ny >= 0 && ny < 8) {
          out[n++] = nx * 8 + ny;
        }
      }
    } else {
      int[][] rays =
          switch (piece) {
            case BISHOP -> BISHOP_RAYS;
            case ROOK -> ROOK_RAYS;
            default -> QUEEN_RAYS;
          };
      for (int[] d : rays) {
        for (int k = 1; ; k++) {
          int nx = x + k * d[0], ny = y + k * d[1];
          if (nx < 0 || nx > 7 || ny < 0 || ny > 7) {
            break;
          }
          out[n++] = nx * 8 + ny;
        }
      }
    }
    int[] result = new int[n];
    System.arraycopy(out, 0, result, 0, n);
    return result;
  }

  private static int key(Stone stone, int from, int to, int captured, Piece promotion) {
    return (((((stone.index() * 64 + from) * 64 + to) * 8) + captured) * 8) + promotion.ordinal();
  }

  private static int capturedCode(Piece piece) {
    return switch (piece) {
      case NO_PIECE -> 0;
      case QUEEN -> 1;
      case KNIGHT -> 2;
      case BISHOP -> 3;
      case ROOK -> 4;
      case PAWN -> 5;
      case KING -> throw new IllegalArgumentException("A king can't be captured");
    };
  }

  /**
   * What a move word stands for.
   *
   * @return the move, or null if the word is not a move word
   */
  public static @Nullable Word word(int word) {
    return word > 0 && word < WORDS.length ? WORDS[word] : null;
  }

  /**
   * Decodes a move word into a move in a position.
   *
   * @param word the word
   * @param position the position the move is made in
   * @return the move
   * @throws InvalidDataException if the word is not a move, or doesn't fit the position
   */
  public static @NotNull Move decode(int word, @NotNull Position position) {
    if (word == NULL_MOVE) {
      return Move.nullMove(position);
    }
    Word w = word(word);
    if (w == null) {
      throw new InvalidDataException(String.format("%04x is not a move word", word));
    }
    if (w.stone().toPlayer() != position.playerToMove()) {
      throw new InvalidDataException(
          String.format("Move word %04x is a move by the wrong side", word));
    }
    if (w.castles() != 0) {
      return w.castles() == 1 ? Move.longCastles(position) : Move.shortCastles(position);
    }
    if (position.stoneAt(w.from()) != w.stone()) {
      throw new InvalidDataException(
          String.format(
              "Move word %04x moves a %s from %s, which holds %s",
              word, w.stone(), Chess.sqiToStr(w.from()), position.stoneAt(w.from())));
    }
    Stone promotion =
        w.promotion() == Piece.NO_PIECE
            ? Stone.NO_STONE
            : w.promotion().toStone(position.playerToMove());
    return new Move(position, w.from(), w.to(), promotion);
  }

  /**
   * Encodes a move.
   *
   * @param move the move, in the position it's made in
   * @return its word
   * @throws IllegalArgumentException if the move can't be encoded
   */
  public static int encode(@NotNull Move move) {
    if (move.isNullMove()) {
      return NULL_MOVE;
    }
    Position position = move.position();
    boolean white = position.playerToMove() == Player.WHITE;
    if (move.isCastle()) {
      int index = (white ? 0 : 2) + (move.isLongCastle() ? 0 : 1);
      int sp = position.chess960StartPosition();
      return sp == Chess960.REGULAR_CHESS_SP
          ? FIRST_CASTLING + index
          : FIRST_CHESS960_CASTLING + 4 * sp + index;
    }
    int captured = move.isEnPassant() ? EN_PASSANT : capturedCode(move.capturedPiece());
    Piece promotion = move.promotionStone().toPiece();
    Integer word =
        MOVE_TO_WORD.get(
            key(move.movingStone(), move.fromSqi(), move.toSqi(), captured, promotion));
    if (word == null) {
      throw new IllegalArgumentException("No move word for " + move);
    }
    return word;
  }

  /**
   * The word placing a stone on a square in a set-up position.
   *
   * @throws IllegalArgumentException if the stone can't stand on the square (a pawn on the first
   *     or last rank)
   */
  public static int placement(@NotNull Stone stone, int sqi) {
    if (stone.toPiece() == Piece.PAWN) {
      int rank = sqi % 8;
      if (rank < 1 || rank > 6) {
        throw new IllegalArgumentException("A pawn can't stand on " + Chess.sqiToStr(sqi));
      }
      return FIRST_PAWN_PLACEMENT + (stone.isWhite() ? 0 : 48) + 6 * (sqi / 8) + rank - 1;
    }
    int piece = -1;
    for (int i = 0; i < BLOCK_PIECES.length; i++) {
      if (BLOCK_PIECES[i] == stone.toPiece()) {
        piece = i;
      }
    }
    if (piece < 0) {
      throw new IllegalArgumentException("Can't place " + stone);
    }
    return FIRST_PLACEMENT + (stone.isWhite() ? 0 : 320) + 64 * piece + sqi;
  }

  /**
   * The stone a placement word puts on the board.
   *
   * @throws InvalidDataException if the word is not a placement
   */
  public static @NotNull Stone placementStone(int word) {
    int index = word - FIRST_PLACEMENT;
    if (index >= 0 && index < 640) {
      Player player = index < 320 ? Player.WHITE : Player.BLACK;
      return BLOCK_PIECES[(index % 320) / 64].toStone(player);
    }
    if (index >= 640 && index < 736) {
      return Piece.PAWN.toStone(index < 688 ? Player.WHITE : Player.BLACK);
    }
    throw new InvalidDataException(String.format("%04x is not a piece placement", word));
  }

  /** The square a placement word puts a stone on. */
  public static int placementSquare(int word) {
    int index = word - FIRST_PLACEMENT;
    if (index >= 0 && index < 640) {
      return index % 64;
    }
    if (index >= 640 && index < 736) {
      int pawn = (index - 640) % 48;
      return pawn / 6 * 8 + pawn % 6 + 1;
    }
    throw new InvalidDataException(String.format("%04x is not a piece placement", word));
  }
}
