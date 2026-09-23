package se.yarin.morphy.api;

/** The concrete on-disk format behind a {@link Database}. */
public enum DatabaseFormat {
  /** ChessBase v1, the {@code .cbh} family. */
  CBH,
  /** ChessBase v2, the {@code .2cbh} family. */
  CB2,
  /** Plain PGN text. */
  PGN
}
