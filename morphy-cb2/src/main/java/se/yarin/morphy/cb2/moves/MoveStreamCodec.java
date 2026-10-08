package se.yarin.morphy.cb2.moves;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Castles;
import se.yarin.chess.Chess;
import se.yarin.chess.Chess960;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.HashingBoard;
import se.yarin.chess.Move;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Piece;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;
import se.yarin.morphy.api.MainLine;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.storage.RecordFile;

/**
 * Converts between the word stream of a game's {@code .2cbg} record and a {@link GameMovesModel}.
 *
 * <p>The stream is a start position section, present only when the game doesn't start from the
 * standard position, and the moves section. The moves are the main line to its end, then the
 * alternatives, most recent branch point first: {@code fffd} after a move says the move has a
 * sibling stored later, and {@code ffff} ends a line and returns to the latest such branch point.
 * See format/v2/2-moves.md.
 */
public final class MoveStreamCodec {

  /** The word that precedes the set-up position of a Chess960 game from a set-up position. */
  private static final int CHESS960_SETUP = 1000;

  private static final int WHITE_LONG = 1, WHITE_SHORT = 2, BLACK_LONG = 4, BLACK_SHORT = 8;

  /**
   * The moves of a game encoded as a record's tag and content.
   *
   * @param tag {@link RecordFile#TAG_GAME} or {@link RecordFile#TAG_CHESS960}
   * @param content the word stream
   */
  public record Encoded(int tag, byte @NotNull [] content) {}

  private MoveStreamCodec() {}

  /**
   * The main line of a game, decoded a move at a time as it's played through, without the rest
   * of the move tree: the main line comes first in the stream, to its first end of line. A game
   * from a set-up position is decoded in full first. A word that can't be played (by the wrong
   * side, of a piece that isn't there) ends the line there.
   *
   * @param tag the record's tag, the variant
   * @param content the record's content
   * @throws InvalidDataException if the content can't be decoded
   */
  public static @NotNull MainLine mainLine(int tag, byte @NotNull [] content) {
    if (tag != RecordFile.TAG_GAME || content.length < 2 || word(content, 0) != MoveWords.MOVES_SECTION) {
      return MainLine.of(decode(tag, content));
    }
    return new WordMainLine(content);
  }

  private static int word(byte[] content, int index) {
    return (content[2 * index] & 0xFF) | (content[2 * index + 1] & 0xFF) << 8;
  }

  /**
   * The main line of a game from the standard position, played off its words on a {@link
   * HashingBoard}: the hash, the side to move and the move codes come without making positions or
   * moves, which are only made if asked for.
   */
  private static final class WordMainLine implements MainLine {
    private final byte[] content;
    private final HashingBoard board = new HashingBoard();
    // The next word to read
    private int next = 1;
    // The move from the current position: read yet, the game ended, its word
    private boolean read;
    private boolean ended;
    private int word;
    private @Nullable MoveWords.Word move;
    // Made when asked for
    private @Nullable Position position;
    private @Nullable Move madeMove;

    WordMainLine(byte[] content) {
      this.content = content;
    }

    /** Reads the move from the current position; false if the game ends there. */
    private boolean readMove() {
      if (read) {
        return !ended;
      }
      read = true;
      // A move with alternatives later in the stream is followed by a marker saying so
      while (next < content.length / 2 && word(content, next) == MoveWords.MORE_ALTERNATIVES) {
        next++;
      }
      word = next < content.length / 2 ? word(content, next) : MoveWords.END_OF_LINE;
      if (word == MoveWords.END_OF_LINE) {
        ended = true;
      } else if (word != MoveWords.NULL_MOVE) {
        move = MoveWords.word(word);
        // A word that isn't a move of the side to move, or of a piece that isn't there, ends it
        ended =
            move == null
                || move.chess960() >= 0
                || (move.stone().toPlayer() == Player.WHITE) != board.whiteToMove()
                || (move.castles() == 0 && board.stoneAt(move.from()) != move.stone());
      }
      return !ended;
    }

    @Override
    public long hash() {
      return board.hash();
    }

    @Override
    public boolean whiteToMove() {
      return board.whiteToMove();
    }

    @Override
    public int moveCode() {
      if (!readMove()) {
        return MoveCode.NONE;
      }
      if (word == MoveWords.NULL_MOVE) {
        return MoveCode.NULL_MOVE;
      }
      if (move.castles() != 0) {
        int row = board.whiteToMove() ? 0 : 7;
        return MoveCode.of(Chess.E1 + row, (move.castles() == 1 ? Chess.C1 : Chess.G1) + row, Piece.NO_PIECE);
      }
      return MoveCode.of(move.from(), move.to(), move.promotion());
    }

    @Override
    public @NotNull Position position() {
      if (position == null) {
        position = board.toPosition();
      }
      return position;
    }

    @Override
    public @Nullable Move move() {
      if (!readMove()) {
        return null;
      }
      if (madeMove == null) {
        madeMove = MoveWords.decode(word, position());
      }
      return madeMove;
    }

    @Override
    public void advance() {
      if (!readMove()) {
        throw new IllegalStateException("The game has ended");
      }
      if (word == MoveWords.NULL_MOVE) {
        board.playNull();
      } else if (move.castles() != 0) {
        int row = board.whiteToMove() ? 0 : 7;
        board.play(Chess.E1 + row, (move.castles() == 1 ? Chess.C1 : Chess.G1) + row, Piece.NO_PIECE);
      } else {
        board.play(move.from(), move.to(), move.promotion());
      }
      next++;
      read = false;
      move = null;
      position = null;
      madeMove = null;
    }
  }

  /**
   * Decodes the moves of a game.
   *
   * @param tag the record's tag, the variant
   * @param content the record's content
   * @return the moves
   * @throws InvalidDataException if the content can't be decoded
   */
  public static @NotNull GameMovesModel decode(int tag, byte @NotNull [] content) {
    if (tag != RecordFile.TAG_GAME && tag != RecordFile.TAG_CHESS960) {
      throw new InvalidDataException(String.format("Unknown variant %04x", tag));
    }
    if (content.length % 2 != 0) {
      throw new InvalidDataException("The move data has an odd length " + content.length);
    }
    ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
    int[] words = new int[content.length / 2];
    for (int i = 0; i < words.length; i++) {
      words[i] = buf.getShort() & 0xFFFF;
    }

    int pos = 0;
    GameMovesModel model = new GameMovesModel();
    if (pos < words.length && words[pos] == MoveWords.START_POSITION_SECTION) {
      int end = ++pos;
      while (end < words.length && words[end] < MoveWords.FIRST_MARKER) {
        end++;
      }
      model = decodeStartPosition(tag == RecordFile.TAG_CHESS960, words, pos, end);
      pos = end;
    }
    if (pos >= words.length || words[pos] != MoveWords.MOVES_SECTION) {
      throw new InvalidDataException("The moves section is missing");
    }
    pos++;

    GameMovesModel.Node current = model.root();
    GameMovesModel.Node lastParent = null;
    Deque<GameMovesModel.Node> pending = new ArrayDeque<>();
    while (true) {
      if (pos >= words.length) {
        throw new InvalidDataException("The move data ended before its last line did");
      }
      int word = words[pos++];
      if (word == MoveWords.MORE_ALTERNATIVES) {
        if (lastParent == null) {
          throw new InvalidDataException("An alternative marker without a move before it");
        }
        pending.push(lastParent);
      } else if (word == MoveWords.END_OF_LINE) {
        if (pending.isEmpty()) {
          break;
        }
        current = pending.pop();
        lastParent = null;
      } else {
        Move move = MoveWords.decode(word, current.position());
        lastParent = current;
        current = current.addMoveUnsafe(move);
      }
    }
    if (pos != words.length) {
      throw new InvalidDataException(
          (words.length - pos) + " words left over after the end of the moves");
    }
    return model;
  }

  private static GameMovesModel decodeStartPosition(
      boolean chess960, int[] words, int start, int end) {
    int length = end - start;
    if (chess960 && length == 1) {
      int sp = words[start];
      if (sp < 0 || sp >= 960) {
        throw new InvalidDataException("Invalid Chess960 start position " + sp);
      }
      return new GameMovesModel(Chess960.getStartPosition(sp), 1);
    }
    if (chess960) {
      if (words[start] != CHESS960_SETUP) {
        throw new InvalidDataException("Unexpected start of a Chess960 set-up: " + words[start]);
      }
      start++;
    }
    if (end - start < 3) {
      throw new InvalidDataException("The set-up position is too short");
    }
    int moveNumber = words[start];
    Player toMove = (words[start + 1] & 0xFF) == 0 ? Player.WHITE : Player.BLACK;
    // 1-8 for the files a-h; 0 for none, and 15 is also found where no capture is possible
    int epByte = words[start + 1] >> 8;
    int epFile = epByte >= 1 && epByte <= 8 ? epByte - 1 : -1;
    int castling = words[start + 2];
    Stone[] stones = new Stone[64];
    Arrays.fill(stones, Stone.NO_STONE);
    for (int i = start + 3; i < end; i++) {
      stones[MoveWords.placementSquare(words[i])] = MoveWords.placementStone(words[i]);
    }
    EnumSet<Castles> castles = EnumSet.noneOf(Castles.class);
    if ((castling & WHITE_LONG) != 0) castles.add(Castles.WHITE_LONG_CASTLE);
    if ((castling & WHITE_SHORT) != 0) castles.add(Castles.WHITE_SHORT_CASTLE);
    if ((castling & BLACK_LONG) != 0) castles.add(Castles.BLACK_LONG_CASTLE);
    if ((castling & BLACK_SHORT) != 0) castles.add(Castles.BLACK_SHORT_CASTLE);
    int sp = Chess960.REGULAR_CHESS_SP;
    if (chess960) {
      try {
        sp = Chess960.getStartPositionNo(stones, castles);
      } catch (IllegalArgumentException e) {
        throw new InvalidDataException("No Chess960 start position fits the set-up position", e);
      }
    }
    try {
      Position position = new Position(stones, toMove, castles, epFile, sp);
      return new GameMovesModel(position, Math.max(moveNumber, 1));
    } catch (RuntimeException | AssertionError e) {
      throw new InvalidDataException("Invalid set-up position", e);
    }
  }

  /**
   * Encodes the moves of a game. Annotations are ignored.
   *
   * @param model the moves
   * @return the record's tag and content
   * @throws IllegalArgumentException if a move can't be encoded
   */
  public static @NotNull Encoded encode(@NotNull GameMovesModel model) {
    WordWriter out = new WordWriter();
    Position start = model.root().position();
    int sp = start.chess960StartPosition();
    boolean chess960 = sp != Chess960.REGULAR_CHESS_SP;
    int ply = model.root().ply();
    if (chess960 && ply == 0 && start.equals(Chess960.getStartPosition(sp))) {
      out.add(MoveWords.START_POSITION_SECTION);
      out.add(sp);
    } else if (model.isSetupPosition()) {
      out.add(MoveWords.START_POSITION_SECTION);
      if (chess960) {
        out.add(CHESS960_SETUP);
      }
      out.add(Chess.plyToMoveNumber(ply));
      int epFile = start.getEnPassantCol() + 1;
      out.add((start.playerToMove() == Player.WHITE ? 0 : 1) | (epFile << 8));
      int castling = 0;
      if (start.isCastles(Castles.WHITE_LONG_CASTLE)) castling |= WHITE_LONG;
      if (start.isCastles(Castles.WHITE_SHORT_CASTLE)) castling |= WHITE_SHORT;
      if (start.isCastles(Castles.BLACK_LONG_CASTLE)) castling |= BLACK_LONG;
      if (start.isCastles(Castles.BLACK_SHORT_CASTLE)) castling |= BLACK_SHORT;
      out.add(castling);
      for (int sqi = 0; sqi < 64; sqi++) {
        Stone stone = start.stoneAt(sqi);
        if (!stone.isNoStone()) {
          out.add(MoveWords.placement(stone, sqi));
        }
      }
    }

    out.add(MoveWords.MOVES_SECTION);
    // Branch points with alternatives still to be written, most recent first
    Deque<Pending> pending = new ArrayDeque<>();
    GameMovesModel.Node node = model.root();
    int childIndex = 0;
    while (true) {
      List<GameMovesModel.Node> children = node.children();
      if (childIndex < children.size()) {
        GameMovesModel.Node child = children.get(childIndex);
        out.add(MoveWords.encode(child.lastMove()));
        if (childIndex + 1 < children.size()) {
          out.add(MoveWords.MORE_ALTERNATIVES);
          pending.push(new Pending(node, childIndex + 1));
        }
        node = child;
        childIndex = 0;
      } else {
        out.add(MoveWords.END_OF_LINE);
        if (pending.isEmpty()) {
          break;
        }
        Pending next = pending.pop();
        node = next.node();
        childIndex = next.childIndex();
      }
    }
    return new Encoded(chess960 ? RecordFile.TAG_CHESS960 : RecordFile.TAG_GAME, out.toBytes());
  }

  /** A branch point, and the index of the next of its children to write. */
  private record Pending(@NotNull GameMovesModel.Node node, int childIndex) {}

  private static final class WordWriter {
    private char[] words = new char[64];
    private int size;

    void add(int word) {
      if (size == words.length) {
        words = Arrays.copyOf(words, size * 2);
      }
      words[size++] = (char) word;
    }

    byte[] toBytes() {
      ByteBuffer buf = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < size; i++) {
        buf.putChar(words[i]);
      }
      return buf.array();
    }
  }
}
