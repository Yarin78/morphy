package se.yarin.chess;

/** General chess-specific definition */
public final class Chess {

  private Chess() {}

  public static final int A8 = 7,
      B8 = 15,
      C8 = 23,
      D8 = 31,
      E8 = 39,
      F8 = 47,
      G8 = 55,
      H8 = 63,
      A7 = 6,
      B7 = 14,
      C7 = 22,
      D7 = 30,
      E7 = 38,
      F7 = 46,
      G7 = 54,
      H7 = 62,
      A6 = 5,
      B6 = 13,
      C6 = 21,
      D6 = 29,
      E6 = 37,
      F6 = 45,
      G6 = 53,
      H6 = 61,
      A5 = 4,
      B5 = 12,
      C5 = 20,
      D5 = 28,
      E5 = 36,
      F5 = 44,
      G5 = 52,
      H5 = 60,
      A4 = 3,
      B4 = 11,
      C4 = 19,
      D4 = 27,
      E4 = 35,
      F4 = 43,
      G4 = 51,
      H4 = 59,
      A3 = 2,
      B3 = 10,
      C3 = 18,
      D3 = 26,
      E3 = 34,
      F3 = 42,
      G3 = 50,
      H3 = 58,
      A2 = 1,
      B2 = 9,
      C2 = 17,
      D2 = 25,
      E2 = 33,
      F2 = 41,
      G2 = 49,
      H2 = 57,
      A1 = 0,
      B1 = 8,
      C1 = 16,
      D1 = 24,
      E1 = 32,
      F1 = 40,
      G1 = 48,
      H1 = 56;
  public static final int NO_COL = -1, NO_ROW = -1, NO_SQUARE = -1;

  /**
   * Converts coordinates to square index.
   *
   * @param col the column (file)
   * @param row the row (rank)
   * @return the square index
   */
  public static final int coorToSqi(int col, int row) {
    return col * 8 + row;
  }

  /**
   * Extract the row of a square index.
   *
   * @param sqi the square index
   * @return the row
   */
  public static final int sqiToRow(int sqi) {
    return sqi % 8;
  }

  /**
   * Extract the column of a square index.
   *
   * @param sqi the square index
   * @return the column
   */
  public static final int sqiToCol(int sqi) {
    return sqi / 8;
  }

  /**
   * Returns the row difference from one square index to the other.
   *
   * @param sqi1 the one square index
   * @param sqi2 the other square index
   * @return the row difference from sqi1 to sqi2
   */
  public static final int deltaRow(int sqi1, int sqi2) {
    return (sqi2 % 8) - (sqi1 % 8);
  }

  /**
   * Returns the column difference from one square index to the other.
   *
   * @param sqi1 the one square index
   * @param sqi2 the other square index
   * @return the column difference from sqi1 to sqi2
   */
  public static final int deltaCol(int sqi1, int sqi2) {
    return (sqi2 / 8) - (sqi1 / 8);
  }

  /**
   * Returns the character of a column (file): 'a'..'h'.
   *
   * @param col the column
   * @return the character representing the column
   */
  public static final char colToChar(int col) {
    final char c[] = {'-', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};
    return c[col + 1];
  }

  /**
   * Returns the character of a row (rank): '1'..'8'.
   *
   * @param row the row
   * @return the character representing the row
   */
  public static final char rowToChar(int row) {
    final char r[] = {'-', '1', '2', '3', '4', '5', '6', '7', '8'};
    return r[row + 1];
  }

  /**
   * Returns the algebraic representation of a square "a1".."h8".
   *
   * @param sqi the square
   * @return the algebraic representation
   */
  public static final String sqiToStr(int sqi) {
    return String.valueOf(colToChar(sqiToCol(sqi))) + rowToChar(sqiToRow(sqi));
  }

  /**
   * Returns the algebraic representation of a square "a1".."h8".
   *
   * @param col the col
   * @param row the row
   * @return the algebraic representation
   */
  public static final String coorToStr(int col, int row) {
    return sqiToStr(coorToSqi(col, row));
  }

  /**
   * Returns whether the square is white.
   *
   * @param sqi the square
   * @return whether sqi is a white square
   */
  public static final boolean isWhiteSquare(int sqi) {
    return ((sqiToCol(sqi) + sqiToRow(sqi)) % 2) != 0;
  }

  /**
   * Returns the column represented by the character.
   *
   * @param ch the column character ('a'..'h')
   * @return the column, or <code>NO_COL</code> if an illegal character is passed
   */
  public static final int charToCol(char ch) {
    if ((ch >= 'a') && (ch <= 'h')) {
      return ch - 'a';
    } else {
      return NO_COL;
    }
  }

  /**
   * Returns the row represented by the character.
   *
   * @param ch the row character ('1'..'8')
   * @return the column, or <code>NO_ROW</code> if an illegal character is passed
   */
  public static final int charToRow(char ch) {
    if ((ch >= '1') && (ch <= '8')) {
      return ch - '1';
    } else {
      return NO_ROW;
    }
  }

  /**
   * Converts a square representation to a square index.
   *
   * @param s the algebraic square representation
   * @return the square index, or <code>NO_SQUARE</code> if an illegal string is passed
   */
  public static final int strToSqi(String s) {
    if (s == null || s.length() != 2) return NO_SQUARE;
    int col = charToCol(s.charAt(0));
    if (col == NO_COL) return NO_SQUARE;
    int row = charToRow(s.charAt(1));
    if (row == NO_ROW) return NO_SQUARE;
    return coorToSqi(col, row);
  }

  /**
   * Converts a col and row character pair to a square index.
   *
   * @param colCh the row character
   * @param rowCh the column character
   * @return the square index, or <code>NO_SQUARE</code> if an illegal character is passed
   */
  public static final int strToSqi(char colCh, char rowCh) {
    int col = charToCol(colCh);
    if (col == NO_COL) return NO_SQUARE;
    int row = charToRow(rowCh);
    if (row == NO_ROW) return NO_SQUARE;
    return coorToSqi(col, row);
  }

  /**
   * Returns whether it is white move at the given ply.
   *
   * @param plyNumber the ply number, starting at 0
   */
  public static boolean isWhitePly(int plyNumber) {
    return plyNumber % 2 == 0;
  }

  /**
   * Converts a ply to a move number
   *
   * @param plyNumber the ply number, starting at 0
   */
  public static int plyToMoveNumber(int plyNumber) {
    return plyNumber / 2 + 1;
  }

  /**
   * Converts a move number to a ply.
   *
   * @param moveNumber the move number, starting from 1
   * @param toMove the player to move
   * @return the ply; 0 is first move with white to play
   */
  public static int moveNumberToPly(int moveNumber, Player toMove) {
    return moveNumber * 2 - (toMove == Player.WHITE ? 2 : 1);
  }
}
