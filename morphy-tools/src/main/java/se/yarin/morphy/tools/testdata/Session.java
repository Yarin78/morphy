package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.List;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.model.GameDto;

/**
 * One version of a database being built: the open database, and what it is expected to contain.
 * The expected games are kept by the caller from version to version, since each version continues
 * where the previous one left off.
 */
final class Session {
  final Format format;
  final Database db;
  final List<GameDto> expected;
  final List<String> manifest = new ArrayList<>();

  Session(Format format, Database db, List<GameDto> expected) {
    this.format = format;
    this.db = db;
    this.expected = expected;
  }

  /** Adds a game, which must get the next id. */
  int add(GameSpec spec) {
    GameDto game = spec.build();
    long id = db.addGame(game);
    if (id != expected.size() + 1) {
      throw new IllegalStateException(
          "Expected the new game to get id " + (expected.size() + 1) + " but got " + id);
    }
    expected.add(game);
    return (int) id;
  }

  /**
   * Adds games and records in the manifest what they are for.
   *
   * @param what a description of the features the games exercise
   */
  void feature(String what, Runnable adds) {
    int first = expected.size() + 1;
    adds.run();
    int last = expected.size();
    manifest.add(
        (first == last ? "game " + first : "games " + first + "–" + last) + ": " + what);
  }

  /** Records something in the manifest that doesn't add games. */
  void note(String what) {
    manifest.add(what);
  }

  /** Checks that the database holds exactly what is expected. */
  void verify(String when) {
    Verifier.verify(this, when);
  }
}
