package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.Date;
import se.yarin.chess.EloType;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TimeControlDto;
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

  @Test
  void boardRoundTrips() throws Exception {
    File file = new File(tempDir, "board.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto game = withBoard(unbind(v1.getGame(1, GameFetchOptions.full())), 4);
      long id = v2.addGame(game);
      assertEquals(4, v2.getGame(id, GameFetchOptions.full()).board());

      // Replacing the game without a board clears it
      v2.replaceGame(id, withBoard(game, null));
      assertEquals(null, v2.getGame(id, GameFetchOptions.full()).board());
    }
  }

  @Test
  void newTournamentTakesItsFlagsFromTheGame() throws Exception {
    File file = new File(tempDir, "flags.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
      TournamentDto t = g.tournament();
      TournamentDto flagged =
          new TournamentDto(
              null, t.title(), t.startDate(), t.endDate(), t.place(), t.nation(), t.category(),
              null, t.rounds(), t.type(), t.timeControl(), null, true, true, null, null, null,
              null);
      long id = v2.addGame(withTournament(g, flagged));
      TournamentDto back = v2.getGame(id, GameFetchOptions.full()).tournament();
      assertEquals(true, back.complete());
      assertEquals(true, back.teamTournament());
    }
  }

  @Test
  void eloTypesRoundTrip() throws Exception {
    File file = new File(tempDir, "elo.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
      EloType national = EloType.national(EloType.TimeControl.BLITZ, "NOR");
      EloType lichess = EloType.server(EloType.TimeControl.RAPID, EloType.LICHESS);
      long id = v2.addGame(withElos(g, 2100, national, 2300, lichess));
      GameDto back = v2.getGame(id, GameFetchOptions.full());
      assertEquals(national, back.whiteEloType());
      assertEquals(lichess, back.blackEloType());

      // An elo without a type gets a FIDE one
      id = v2.addGame(withElos(g, 2100, null, 2300, null));
      assertEquals(EloType.FIDE, v2.getGame(id, GameFetchOptions.full()).whiteEloType());

      // Server ratings at any of their time controls
      EloType lichessBullet = EloType.server(EloType.TimeControl.BULLET, EloType.LICHESS);
      EloType chessCom = EloType.server(EloType.TimeControl.BLITZ, EloType.CHESS_COM);
      id = v2.addGame(withElos(g, 2100, lichessBullet, 2300, chessCom));
      back = v2.getGame(id, GameFetchOptions.full());
      assertEquals(lichessBullet, back.whiteEloType());
      assertEquals(chessCom, back.blackEloType());

      // Types ChessBase doesn't offer can't be stored, and a national rating needs a nation
      for (EloType invalid :
          new EloType[] {
            EloType.international(EloType.TimeControl.BULLET),
            EloType.server(EloType.TimeControl.CORRESPONDENCE, EloType.LICHESS),
            new EloType(EloType.Kind.NATIONAL, EloType.TimeControl.NORMAL, null, null),
          }) {
        assertThrows(IllegalArgumentException.class, () -> v2.addGame(withElos(g, 2100, invalid, 2300, null)));
      }
    }
  }

  private static GameDto withElos(GameDto g, Integer whiteElo, EloType whiteType, Integer blackElo, EloType blackType) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), whiteElo, whiteType, g.blackPlayer(), blackElo,
        blackType, g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(), g.round(), g.subRound(),
        g.board(), g.lineEvaluation(), g.timeControl(), g.tournament(), g.source(), g.annotator(), g.gameTag(), g.medals(),
        g.deleted(), g.topGame(), g.setupPosition(), g.variant(), g.noMoves(), g.notation(),
        g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(), g.gameVersion(), g.creationTimestamp(),
        g.lastChanged(), g.moves(), g.text(), g.extraTags());
  }

  @Test
  void newSourceTakesItsFieldsFromTheGame() throws Exception {
    File file = new File(tempDir, "source.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
      SourceDto source =
          new SourceDto(null, "Scratch Source", "Morphy", new Date(2025, 3, 1), new Date(2024, 12, 31), 3, "MEDIUM", null);
      long id = v2.addGame(withSource(g, source));
      SourceDto back = v2.getGame(id, GameFetchOptions.full()).source();
      assertEquals("Scratch Source", back.title());
      assertEquals("Morphy", back.publisher());
      assertEquals(new Date(2025, 3, 1), back.publication());
      assertEquals(new Date(2024, 12, 31), back.date());
      assertEquals(3, back.version());
      assertEquals("MEDIUM", back.quality());
    }
  }

  @Test
  void newTeamsTakeTheirDetailsFromTheGame() throws Exception {
    File file = new File(tempDir, "teams.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
      TeamDto white = new TeamDto(null, "Norway", 1, true, 2024, "NOR", null);
      TeamDto black = new TeamDto(null, "Sweden", 2, null, 2023, null, null);
      long id = v2.addGame(withTeams(g, white, black));
      GameDto back = v2.getGame(id, GameFetchOptions.full());
      assertEquals(new TeamDto(back.whiteTeam().id(), "Norway", 1, true, 2024, "NOR", null), back.whiteTeam());
      assertEquals("Sweden", back.blackTeam().title());
      assertEquals(2, back.blackTeam().teamNumber());
      assertEquals(2023, back.blackTeam().year());

      // A game without teams has none
      assertEquals(null, v2.getGame(v2.addGame(g), GameFetchOptions.full()).whiteTeam());
    }
  }

  @Test
  void newGameTagsTakeTheirLanguagesFromTheGame() throws Exception {
    for (String name : new String[] {"tags2.2cbh", "tags1.cbh"}) {
      File file = new File(tempDir, name);
      try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
          Database db = Databases.create(file)) {
        GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
        GameTagDto german =
            new GameTagDto(
                null, "Strategie", null, null, null, "Strategie", null, null, null, null, null, null,
                null, null);
        GameTagDto back = db.getGame(db.addGame(withGameTag(g, german)), GameFetchOptions.full()).gameTag();
        assertEquals("Strategie", back.title(), name);
        assertEquals("Strategie", back.germanTitle(), name);
        assertEquals(null, back.englishTitle(), name);

        // Shown in English when the tag has a title in it
        GameTagDto both =
            new GameTagDto(
                null, "Tactics", null, null, "Tactics", null, "Tactique", null, null, null, null, null,
                null, null);
        back = db.getGame(db.addGame(withGameTag(g, both)), GameFetchOptions.full()).gameTag();
        assertEquals("Tactics", back.title(), name);
        assertEquals("Tactique", back.frenchTitle(), name);

        // A tag known only by its title has it in English
        GameTagDto plain =
            new GameTagDto(
                null, "Model game", null, null, null, null, null, null, null, null, null, null, null,
                null);
        back = db.getGame(db.addGame(withGameTag(g, plain)), GameFetchOptions.full()).gameTag();
        assertEquals("Model game", back.englishTitle(), name);
      }
    }
  }

  @Test
  void newPlayersTakeTheirFideIdsFromTheGame() throws Exception {
    File file = new File(tempDir, "fide.2cbh");
    try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
        Database v2 = Databases.create(file)) {
      GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
      PlayerDto white = new PlayerDto(null, "Carlsen", "Magnus", null, 1503014L, null);
      PlayerDto black = new PlayerDto(null, "Unrated", "Player", null, null, null);
      GameDto withPlayers =
          new GameDto(
              g.id(), g.type(), g.textTitle(), white, g.whiteElo(), g.whiteEloType(), black,
              g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(),
              g.eco(), g.round(), g.subRound(), g.board(), g.lineEvaluation(), g.timeControl(), g.tournament(),
              g.source(), g.annotator(), g.gameTag(), g.medals(), g.deleted(), g.topGame(),
              g.setupPosition(), g.variant(), g.noMoves(), g.notation(), g.variationMoves(), g.ait(),
              g.vcs(), g.finalMaterial(), g.gameVersion(), g.creationTimestamp(), g.lastChanged(),
              g.moves(), g.text(), g.extraTags());
      GameDto back = v2.getGame(v2.addGame(withPlayers), GameFetchOptions.full());
      assertEquals(1503014L, back.whitePlayer().fideId());
      assertEquals(null, back.blackPlayer().fideId());
    }
  }

  @Test
  void timeControlsAreKeptApartFromTheMoves() throws Exception {
    TimeControlDto classical =
        new TimeControlDto(
            List.of(new TimeControlDto.Period(5400, 0, 40), new TimeControlDto.Period(1800, 30, null)));
    for (String name : new String[] {"tc2.2cbh", "tc1.cbh"}) {
      File file = new File(tempDir, name);
      try (Database v1 = Databases.open(TestDatabases.worldCh(), AccessMode.READ_ONLY);
          Database db = Databases.create(file)) {
        GameDto g = unbind(v1.getGame(1, GameFetchOptions.full()));
        long id = db.addGame(withTimeControl(g, classical));
        GameDto back = db.getGame(id, GameFetchOptions.full());
        assertEquals(classical, back.timeControl(), name);
        assertFalse(back.moves().pgn().contains("[%tc"), name);

        // Saved again without one, it has none
        db.replaceGame(id, withTimeControl(back, null));
        assertEquals(null, db.getGame(id, GameFetchOptions.full()).timeControl(), name);
      }
    }
  }

  private static GameDto withTimeControl(GameDto g, TimeControlDto timeControl) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(), g.blackPlayer(),
        g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(),
        g.round(), g.subRound(), g.board(), g.lineEvaluation(), timeControl, g.tournament(), g.source(),
        g.annotator(), g.gameTag(), g.medals(), g.deleted(), g.topGame(), g.setupPosition(), g.variant(),
        g.noMoves(), g.notation(), g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(),
        g.gameVersion(), g.creationTimestamp(), g.lastChanged(), g.moves(), g.text(), g.extraTags());
  }

  private static GameDto withGameTag(GameDto g, GameTagDto tag) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(), g.blackPlayer(),
        g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(),
        g.round(), g.subRound(), g.board(), g.lineEvaluation(), g.timeControl(), g.tournament(), g.source(), g.annotator(),
        tag, g.medals(), g.deleted(), g.topGame(), g.setupPosition(), g.variant(), g.noMoves(),
        g.notation(), g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(), g.gameVersion(),
        g.creationTimestamp(), g.lastChanged(), g.moves(), g.text(), g.extraTags());
  }

  private static GameDto withTeams(GameDto g, TeamDto white, TeamDto black) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(), g.blackPlayer(),
        g.blackElo(), g.blackEloType(), white, black, g.result(), g.date(), g.eco(), g.round(), g.subRound(),
        g.board(), g.lineEvaluation(), g.timeControl(), g.tournament(), g.source(), g.annotator(), g.gameTag(), g.medals(),
        g.deleted(), g.topGame(), g.setupPosition(), g.variant(), g.noMoves(), g.notation(),
        g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(), g.gameVersion(), g.creationTimestamp(),
        g.lastChanged(), g.moves(), g.text(), g.extraTags());
  }

  private static GameDto withSource(GameDto g, SourceDto source) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(), g.blackPlayer(),
        g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(), g.round(),
        g.subRound(), g.board(), g.lineEvaluation(), g.timeControl(), g.tournament(), source, g.annotator(), g.gameTag(),
        g.medals(), g.deleted(), g.topGame(), g.setupPosition(), g.variant(), g.noMoves(), g.notation(),
        g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(), g.gameVersion(), g.creationTimestamp(),
        g.lastChanged(), g.moves(), g.text(), g.extraTags());
  }

  private static GameDto withTournament(GameDto g, TournamentDto tournament) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(),
        g.blackPlayer(), g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(), g.round(),
        g.subRound(), g.board(), g.lineEvaluation(), g.timeControl(), tournament, g.source(), g.annotator(),
        g.gameTag(), g.medals(), g.deleted(), g.topGame(), g.setupPosition(), g.variant(),
        g.noMoves(), g.notation(), g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(),
        g.gameVersion(), g.creationTimestamp(), g.lastChanged(), g.moves(), g.text(),
        g.extraTags());
  }

  private static GameDto withBoard(GameDto g, Integer board) {
    return new GameDto(
        g.id(), g.type(), g.textTitle(), g.whitePlayer(), g.whiteElo(), g.whiteEloType(),
        g.blackPlayer(), g.blackElo(), g.blackEloType(), g.whiteTeam(), g.blackTeam(), g.result(), g.date(), g.eco(), g.round(),
        g.subRound(), board, g.lineEvaluation(), g.timeControl(), g.tournament(), g.source(), g.annotator(),
        g.gameTag(), g.medals(), g.deleted(), g.topGame(), g.setupPosition(), g.variant(),
        g.noMoves(), g.notation(), g.variationMoves(), g.ait(), g.vcs(), g.finalMaterial(),
        g.gameVersion(), g.creationTimestamp(), g.lastChanged(), g.moves(), g.text(),
        g.extraTags());
  }

  /** A game with its entity ids dropped, so its entities are found or created by name. */
  private static GameDto unbind(GameDto g) {
    return new GameDto(
        null, g.type(), g.textTitle(),
        g.whitePlayer() == null ? null : new PlayerDto(null, g.whitePlayer().lastName(), g.whitePlayer().firstName(), null, null, null),
        g.whiteElo(),
        g.whiteEloType(),
        g.blackPlayer() == null ? null : new PlayerDto(null, g.blackPlayer().lastName(), g.blackPlayer().firstName(), null, null, null),
        g.blackElo(), g.blackEloType(), null, null, g.result(), g.date(), g.eco(), g.round(), g.subRound(),
        g.board(), g.lineEvaluation(), g.timeControl(),
        g.tournament() == null ? null : new TournamentDto(null, g.tournament().title(), g.tournament().startDate(), g.tournament().endDate(), g.tournament().place(), g.tournament().nation(), g.tournament().category(), null, g.tournament().rounds(), g.tournament().type(), g.tournament().timeControl(), null, null, null, null, null, null, null),
        null, null, null, g.medals(), null, null, g.setupPosition(), g.variant(), null, null, null,
        null, null, null, null, null, null, g.moves(), g.text(),
        null);
  }
}
