package se.yarin.chess;

/** A stone is a colored chessman */
public enum Stone {
  NO_STONE(0, ' '),
  WHITE_PAWN(1, 'P'),
  WHITE_KNIGHT(2, 'N'),
  WHITE_BISHOP(3, 'B'),
  WHITE_ROOK(4, 'R'),
  WHITE_QUEEN(5, 'Q'),
  WHITE_KING(6, 'K'),
  BLACK_PAWN(-1, 'p'),
  BLACK_KNIGHT(-2, 'n'),
  BLACK_BISHOP(-3, 'b'),
  BLACK_ROOK(-4, 'r'),
  BLACK_QUEEN(-5, 'q'),
  BLACK_KING(-6, 'k');

  private final int value;
  private final char ch;
  private static Stone[] charToStone;

  static {
    charToStone = new Stone[128];
    for (Stone stone : Stone.values()) {
      charToStone[stone.ch] = stone;
    }
  }

  /**
   * @return an index in the range [0,12] representing this stone &gt;6 = white stones, 6 =
   *     NO_STONE, &lt;6 = black stones
   */
  public int index() {
    return this.value + 6;
  }

  public boolean isWhite() {
    return value > 0;
  }

  public boolean isBlack() {
    return value < 0;
  }

  public boolean isNoStone() {
    return value == 0;
  }

  public boolean hasPlayer(Player player) {
    return toPlayer() == player;
  }

  public Player toPlayer() {
    if (value < 0) return Player.BLACK;
    if (value > 0) return Player.WHITE;
    return Player.NOBODY;
  }

  // The piece of each stone, by its value: looked up for every move played, so not a switch
  private static final Piece[] PIECES = {
    Piece.KING, Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT, Piece.PAWN,
    Piece.NO_PIECE,
    Piece.PAWN, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK, Piece.QUEEN, Piece.KING
  };

  public Piece toPiece() {
    return PIECES[value + 6];
  }

  public char toChar() {
    return ch;
  }

  public static Stone fromChar(char ch) {
    return charToStone[ch];
  }

  Stone(int value, char ch) {
    this.value = value;
    this.ch = ch;
  }
}
