package se.yarin.chess.pgn;

/** How the PGN exporter writes numeric annotation glyphs (NAGs). */
public enum NagStyle {
  /** Every NAG as {@code $n}: the PGN export format. */
  NUMERIC,
  /**
   * The six move suffix annotations ({@code !}, {@code ?}, {@code !!}, {@code ??}, {@code !?},
   * {@code ?!}) directly after the move, every other NAG as {@code $n}. Readable, and reads back to
   * the same NAGs.
   */
  SUFFIXES,
  /**
   * Every NAG that has one as its ASCII symbol (such as {@code +-} or {@code unclear}), for display.
   * Lossy: several NAGs share a symbol, and the PGN parser can't read most symbols back.
   */
  SYMBOLS
}
