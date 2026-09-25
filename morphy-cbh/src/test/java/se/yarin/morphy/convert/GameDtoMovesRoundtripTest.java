package se.yarin.morphy.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.model.GameDto;

/**
 * The moves of every game in World-ch survive the DTO: the PGN a game is sent out as reads back to
 * the same moves, variations, annotations and comments.
 */
public class GameDtoMovesRoundtripTest {

  @Test
  public void everyWorldChGameReadsBackToTheSameMoves() throws IOException {
    File dir = ResourceLoader.materializeStreamPath(DatabaseCbh.class, "database/World-ch");
    GameDtoImporter importer = new GameDtoImporter();
    List<String> failures = new ArrayList<>();
    int checked = 0;
    try (Database db = Databases.open(new File(dir, "World-ch.cbh"), AccessMode.READ_ONLY)) {
      for (long id = 1; id <= db.gameCount(); id++) {
        GameDto dto = db.getGame(id, GameFetchOptions.full());
        if (dto == null || dto.moves() == null) {
          continue;
        }
        checked++;
        String pgn = dto.moves().pgn();
        try {
          GameModel model = importer.toGameModel(dto);
          String again = GameMovesPgn.toPgn(model.moves());
          if (!pgn.equals(again)) {
            failures.add("game " + id + ": " + firstDifference(pgn, again));
          }
        } catch (IllegalArgumentException e) {
          failures.add("game " + id + ": " + e.getMessage());
        }
      }
    }
    assertTrue("no games were checked", checked > 0);
    assertEquals(
        failures.size() + " of " + checked + " games, e.g. " + failures.stream().limit(5).toList(),
        0,
        failures.size());
  }

  /** Where two movetexts part ways, with a little context. */
  private static String firstDifference(String expected, String actual) {
    int i = 0;
    while (i < expected.length() && i < actual.length() && expected.charAt(i) == actual.charAt(i)) {
      i++;
    }
    int from = Math.max(0, i - 40);
    return "sent '…"
        + expected.substring(from, Math.min(expected.length(), i + 40))
        + "…' but read back '…"
        + actual.substring(from, Math.min(actual.length(), i + 40))
        + "…'";
  }
}
