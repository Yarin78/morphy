package se.yarin.morphy.tools.testdata;

/** The database formats the test databases are written in. */
enum Format {
  CBH("cbh", "cbh"),
  CB2("2cbh", "2cbh"),
  PGN("pgn", "pgn");

  /** The name of the output directory. */
  final String dir;

  /** The extension of the primary file. */
  final String extension;

  Format(String dir, String extension) {
    this.dir = dir;
    this.extension = extension;
  }
}
