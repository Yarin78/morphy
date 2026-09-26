package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.TournamentDto;

/** Games copied through the facade from a v1 database into a new v2 one read back the same. */
class FacadeWriteTest {

  @TempDir File tempDir;

  @Test
  void copyGamesFromV1() throws Exception {
    File file = new File(tempDir, "copy.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      assertTrue(v2.capabilities().canWrite());
      int copied = 0;
      for (long id = 1; id <= 400; id++) {
        GameDto game = v1.getGame(id, GameFetchOptions.full());
        GameDto unbound = unbind(game);
        long newId = v2.addGame(unbound);
        assertEquals(++copied, newId);
        GameDto back = v2.getGame(newId, GameFetchOptions.full());
        assertNotNull(back);
        assertEquals(game.type(), back.type(), "type of " + id);
        if ("game".equals(game.type())) {
          // Game quotations and training questions read from v1 are in v1's binary layout, which
          // v2 can't store, so they are left out
          if (!game.moves().pgn().contains("[%quote") && !game.moves().pgn().contains("[%train")) {
            assertEquals(game.moves().pgn(), back.moves().pgn(), "moves of " + id);
          }
          assertEquals(game.moves().fen(), back.moves().fen(), "start of " + id);
          assertEquals(game.whitePlayer().lastName(), back.whitePlayer().lastName());
          assertEquals(game.result(), back.result());
          assertEquals(game.date(), back.date());
          assertEquals(game.eco(), back.eco());
          assertEquals(game.tournament().title(), back.tournament().title());
          assertEquals(game.noMoves(), back.noMoves());
        } else {
          assertEquals(game.text().contents(), back.text().contents(), "text of " + id);
        }
      }
      Consistency.check(((Database2CbhFacade) v2).engine());

      // Queries see the copied games
      assertEquals(
          v1.findGames(Query.of("Steinitz date:..1900", Sort.natural(), 0, 500), GameFetchOptions.headersOnly()).items().stream()
              .filter(g -> g.id() <= 400).map(GameDto::id).toList(),
          v2.findGames(Query.of("Steinitz date:..1900", Sort.natural(), 0, 500), GameFetchOptions.headersOnly()).items().stream()
              .map(GameDto::id).toList());

      // Entities can be edited
      PlayerDto player = v2.findEntities(EntityKind.PLAYER, Query.of("Steinitz", Sort.natural(), 0, 1)).items().getFirst();
      PlayerDto renamed =
          v2.updateEntity(
              EntityKind.PLAYER,
              player.id(),
              new PlayerDto(player.id(), "Steinitz", "Wilhelm", null, null, null));
      assertEquals("Wilhelm", renamed.firstName());
      assertEquals(player.gameCount(), renamed.gameCount());
      TournamentDto tournament =
          v2.findEntities(EntityKind.TOURNAMENT, Query.all(0, 1)).items().getFirst();
      TournamentDto retitled =
          v2.updateEntity(
              EntityKind.TOURNAMENT,
              tournament.id(),
              new TournamentDto(
                  tournament.id(), "Renamed", tournament.startDate(), null, "Somewhere", "USA", 5,
                  null, 20, "match", null, null, true, null, null, null, null, null));
      assertEquals("Renamed", retitled.title());
      assertEquals("USA", retitled.nation());
      assertEquals("match", retitled.type());
      Consistency.check(((Database2CbhFacade) v2).engine());

      // Replacing a game through the facade
      GameDto fresh = unbind(v1.getGame(200, GameFetchOptions.full()));
      v2.replaceGame(3, fresh);
      assertEquals(fresh.moves().pgn(), v2.getGame(3, GameFetchOptions.full()).moves().pgn());
      assertThrows(IllegalArgumentException.class, () -> v2.replaceGame(999, fresh));
      Consistency.check(((Database2CbhFacade) v2).engine());
    }
  }

  /** A game with its entity ids dropped, so its entities are found or created by name. */
  private static GameDto unbind(GameDto g) {
    return new GameDto(
        null, g.type(), g.textTitle(),
        g.whitePlayer() == null ? null : new PlayerDto(null, g.whitePlayer().lastName(), g.whitePlayer().firstName(), null, null, null),
        g.whiteElo(),
        g.blackPlayer() == null ? null : new PlayerDto(null, g.blackPlayer().lastName(), g.blackPlayer().firstName(), null, null, null),
        g.blackElo(), null, null, g.result(), g.date(), g.eco(), g.round(), g.subRound(),
        g.lineEvaluation(),
        g.tournament() == null ? null : new TournamentDto(null, g.tournament().title(), g.tournament().startDate(), g.tournament().endDate(), g.tournament().place(), g.tournament().nation(), g.tournament().category(), null, g.tournament().rounds(), g.tournament().type(), g.tournament().timeControl(), null, null, null, null, null, null, null),
        null, null, null, g.medals(), null, null, g.setupPosition(), g.variant(), null, null, null,
        null, null, null, null, null, null, g.moves(), g.text(),
        null);
  }
}
