package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.TournamentDto;

/** The v2 facade answers queries as the v1 facade does over the same database. */
class FacadeTest {
  private static Database v1, v2;

  @BeforeAll
  static void open() throws IOException {
    v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
    v2 = Databases.open(TestDatabases.wch2(), AccessMode.READ_ONLY);
  }

  @AfterAll
  static void close() throws IOException {
    v1.close();
    v2.close();
  }

  @Test
  void opensThroughTheFactory() {
    assertInstanceOf(Database2CbhFacade.class, v2);
    assertEquals(1038, v2.gameCount());
    assertTrue(v2.capabilities().hasEntities());
  }

  @Test
  void getGame() {
    GameDto a = v1.getGame(1, GameFetchOptions.full()), b = v2.getGame(1, GameFetchOptions.full());
    assertNotNull(b);
    assertEquals("game", b.type());
    assertEquals(a.whitePlayer().lastName(), b.whitePlayer().lastName());
    assertEquals(a.blackPlayer().lastName(), b.blackPlayer().lastName());
    assertEquals(a.result(), b.result());
    assertEquals(a.date(), b.date());
    assertEquals(a.eco(), b.eco());
    assertEquals(a.tournament().title(), b.tournament().title());
    assertEquals(a.noMoves(), b.noMoves());
    assertEquals(a.vcs(), b.vcs());
    assertEquals(a.finalMaterial(), b.finalMaterial());
    assertNotNull(b.moves().pgn());
    assertNull(v2.getGame(0, GameFetchOptions.headersOnly()));
    assertNull(v2.getGame(5000, GameFetchOptions.headersOnly()));
  }

  @Test
  void guidingText() {
    GameDto text =
        v2.findGames(Query.of("type:text", Sort.natural(), 0, 1), GameFetchOptions.full())
            .items()
            .getFirst();
    assertEquals("text", text.type());
    assertNotNull(text.textTitle());
    assertNotNull(text.text().contents());
  }

  @Test
  void gameQueriesMatchV1() {
    for (String filter :
        List.of(
            "Kasparov",
            "player:Karpov,position=white",
            "white:Karpov",
            "winner:Kasparov",
            "result:1-0",
            "result:draw",
            "eco:B*",
            "eco:C42",
            "date:1990..2000",
            "date:1985",
            "date:1990..",
            "date:..1900",
            "white:Kasparov date:1990..",
            "player.name:Kasparov|Karpov,position=both",
            "tournament.time:rapid",
            "tournament.time:normal|rapid",
            "rating:2700..",
            "moves:..20",
            "round:5",
            "type:game",
            "variations:true",
            "commentary:true",
            "tournament:World-ch2",
            "tournament.title:World-ch3 AND result:0-1",
            "Kasparov Karpov",
            "player.lastname:Kas player.firstname:G")) {
      ResultPage<GameDto> a = v1.findGames(Query.of(filter, Sort.natural(), 0, 2000), GameFetchOptions.headersOnly());
      ResultPage<GameDto> b = v2.findGames(Query.of(filter, Sort.natural(), 0, 2000), GameFetchOptions.headersOnly());
      assertEquals(ids(a), ids(b), filter);
      assertEquals(a.total(), b.total(), filter);
    }
  }

  @Test
  void sortedGames() {
    for (String sort : List.of("-whiteElo", "date", "-playedDate", "noMoves", "eco")) {
      ResultPage<GameDto> a = v1.findGames(Query.of("Kasparov", Sort.parse(sort), 0, 50), GameFetchOptions.headersOnly());
      ResultPage<GameDto> b = v2.findGames(Query.of("Kasparov", Sort.parse(sort), 0, 50), GameFetchOptions.headersOnly());
      assertEquals(ids(a), ids(b), sort);
    }
  }

  @Test
  void entities() {
    assertEquals(v1.entityCount(EntityKind.PLAYER), v2.entityCount(EntityKind.PLAYER));
    assertEquals(v1.entityCount(EntityKind.TOURNAMENT), v2.entityCount(EntityKind.TOURNAMENT));
    ResultPage<PlayerDto> a = v1.findEntities(EntityKind.PLAYER, Query.all(0, 100));
    ResultPage<PlayerDto> b = v2.findEntities(EntityKind.PLAYER, Query.all(0, 100));
    assertEquals(
        a.items().stream().map(p -> p.lastName() + "|" + p.firstName() + "|" + p.gameCount()).toList(),
        b.items().stream().map(p -> p.lastName() + "|" + p.firstName() + "|" + p.gameCount()).toList());
    for (String sort : List.of("count", "-name", "lastName")) {
      assertEquals(
          v1.findEntities(EntityKind.PLAYER, Query.of("", Sort.parse(sort), 0, 100)).items().stream()
              .map(PlayerDto::lastName).toList(),
          v2.findEntities(EntityKind.PLAYER, Query.of("", Sort.parse(sort), 0, 100)).items().stream()
              .map(PlayerDto::lastName).toList(),
          sort);
    }
    ResultPage<TournamentDto> tournaments =
        v2.findEntities(EntityKind.TOURNAMENT, Query.of("title:World-ch2", Sort.natural(), 0, 100));
    assertEquals(
        v1.findEntities(EntityKind.TOURNAMENT, Query.of("title:World-ch2", Sort.natural(), 0, 100)).total(),
        tournaments.total());
    PlayerDto player = v2.getEntity(EntityKind.PLAYER, b.items().getFirst().id());
    assertEquals(b.items().getFirst(), player);
  }

  @Test
  void unknownFieldsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> v2.findGames(Query.of("nosuchfield:1", Sort.natural(), 0, 10), GameFetchOptions.headersOnly()));
    assertThrows(
        IllegalArgumentException.class,
        () -> v2.findGames(Query.of("", Sort.parse("nosuch"), 0, 10), GameFetchOptions.headersOnly()));
  }

  private static List<Long> ids(ResultPage<GameDto> page) {
    return page.items().stream().map(GameDto::id).toList();
  }
}
