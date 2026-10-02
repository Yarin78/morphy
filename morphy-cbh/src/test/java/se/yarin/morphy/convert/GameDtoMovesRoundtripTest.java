package se.yarin.morphy.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.chessbase.convert.GameDtoImporter;
import se.yarin.morphy.chessbase.convert.GameTimeControl;
import se.yarin.morphy.chessbase.convert.GameMovesDtos;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameMovesDto;

/**
 * The moves of every game in World-ch survive the DTO: the movetext and the annotations a game is
 * sent out as read back to the same moves, variations and annotations, every one of them equal.
 */
public class GameDtoMovesRoundtripTest {

  @Test
  public void everyWorldChGameReadsBackToTheSameMoves() throws IOException {
    File dir = ResourceLoader.materializeStreamPath(DatabaseCbh.class, "database/World-ch");
    GameDtoImporter importer = new GameDtoImporter();
    List<String> failures = new ArrayList<>();
    int checked = 0;
    int annotated = 0;
    try (DatabaseCbh db = DatabaseCbh.open(new File(dir, "World-ch.cbh"), DatabaseMode.READ_ONLY)) {
      for (int id = 1; id <= db.count(); id++) {
        Game game = db.getGame(id);
        if (game.guidingText()) {
          continue;
        }
        checked++;
        // The time control is a field of its own, and is left out here
        GameMovesModel original = GameTimeControl.without(game.getModel().moves());
        GameMovesDto moves = GameMovesDtos.toDto(original);
        if (!moves.annotations().isEmpty()) {
          annotated++;
        }
        GameDto dto = GameDto.builder().moves(moves).build();
        try {
          String difference = firstDifference(original, importer.toGameModel(dto).moves());
          if (difference != null) {
            failures.add("game " + id + ": " + difference);
          }
        } catch (IllegalArgumentException e) {
          failures.add("game " + id + ": " + e.getMessage());
        }
      }
    }
    assertTrue("no games were checked", checked > 0);
    assertTrue("no annotated games were checked", annotated > 0);
    assertEquals(
        failures.size() + " of " + checked + " games, e.g. " + failures.stream().limit(5).toList(),
        0,
        failures.size());
  }

  /** The first node where two move trees differ, in their moves or annotations, or null. */
  private static String firstDifference(GameMovesModel expected, GameMovesModel actual) {
    List<GameMovesModel.Node> e = expected.getAllNodes();
    List<GameMovesModel.Node> a = actual.getAllNodes();
    if (e.size() != a.size()) {
      return "sent " + e.size() + " nodes but read back " + a.size();
    }
    for (int i = 0; i < e.size(); i++) {
      GameMovesModel.Node en = e.get(i);
      GameMovesModel.Node an = a.get(i);
      if (!en.isRoot() && !en.lastMove().equals(an.lastMove())) {
        return "node " + i + ": sent " + en.lastMove() + " but read back " + an.lastMove();
      }
      if (!en.getAnnotations().equals(an.getAnnotations())) {
        return "node "
            + i
            + ": sent "
            + en.getAnnotations()
            + " but read back "
            + an.getAnnotations();
      }
    }
    return null;
  }
}
