package se.yarin.morphy.queries.filter;

import static org.junit.Assert.*;

import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.PlayerFilter;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.GameEntityJoin;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.queries.GameQuery;

public class QueryBuilderTest {

  private final Database db = new Database();

  // --- TournamentQueryBuilder ---

  @Test
  public void tournamentByName() {
    EntityQuery<Tournament> query = new TournamentQueryBuilder().buildQuery(db, "name:Candidates");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof TournamentTitleFilter);
  }

  @Test
  public void tournamentByBareTerm() {
    EntityQuery<Tournament> query = new TournamentQueryBuilder().buildQuery(db, "Candidates");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof TournamentTitleFilter);
  }

  @Test
  public void tournamentByYear() {
    EntityQuery<Tournament> query = new TournamentQueryBuilder().buildQuery(db, "year:2024");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof TournamentStartDateFilter);
  }

  @Test
  public void tournamentByNameAndYear() {
    EntityQuery<Tournament> query =
        new TournamentQueryBuilder().buildQuery(db, "Candidates year:2024");

    assertEquals(2, query.filters().size());
    assertTrue(query.filters().get(0) instanceof TournamentTitleFilter);
    assertTrue(query.filters().get(1) instanceof TournamentStartDateFilter);
  }

  @Test
  public void tournamentEmptyFilter() {
    assertTrue(new TournamentQueryBuilder().buildQuery(db, "").filters().isEmpty());
  }

  @Test
  public void tournamentNullFilter() {
    assertTrue(new TournamentQueryBuilder().buildQuery(db, (String) null).filters().isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void tournamentUnknownField() {
    new TournamentQueryBuilder().buildQuery(db, "unknown:value");
  }

  // --- PlayerQueryBuilder ---

  @Test
  public void playerByName() {
    EntityQuery<Player> query = new PlayerQueryBuilder().buildQuery(db, "name:Carlsen");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof PlayerNameFilter);
  }

  @Test
  public void playerByBareTerm() {
    EntityQuery<Player> query = new PlayerQueryBuilder().buildQuery(db, "Carlsen");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof PlayerNameFilter);
  }

  @Test
  public void playerByPipeSyntax() {
    EntityQuery<Player> query = new PlayerQueryBuilder().buildQuery(db, "name:Carlsen|Caruana");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof MultiPlayerNameFilter);
  }

  @Test
  public void playerByFirstName() {
    EntityQuery<Player> query = new PlayerQueryBuilder().buildQuery(db, "firstname:Magnus");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof PlayerNameFilter);
    PlayerNameFilter filter = (PlayerNameFilter) query.filters().get(0);
    assertEquals("magnus", filter.firstName());
    assertEquals("", filter.lastName());
  }

  @Test
  public void playerByLastName() {
    EntityQuery<Player> query = new PlayerQueryBuilder().buildQuery(db, "lastname:Carlsen");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof PlayerNameFilter);
    PlayerNameFilter filter = (PlayerNameFilter) query.filters().get(0);
    assertEquals("", filter.firstName());
    assertEquals("carlsen", filter.lastName());
  }

  @Test
  public void playerByFirstAndLastName() {
    EntityQuery<Player> query =
        new PlayerQueryBuilder().buildQuery(db, "firstname:Magnus lastname:Carlsen");

    assertEquals(2, query.filters().size());
    assertTrue(query.filters().get(0) instanceof PlayerNameFilter);
    assertTrue(query.filters().get(1) instanceof PlayerNameFilter);
  }

  // --- GameQueryBuilder: player position aliases ---

  @Test
  public void gameByWhitePlayerName() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "white.name:Carlsen");

    assertEquals(0, query.gameFilters().size());
    assertEquals(1, query.entityJoins().size());
    GameEntityJoin<?> join = query.entityJoins().get(0);
    assertEquals(EntityType.PLAYER, join.getEntityType());
    assertEquals(GameEntityJoinCondition.WHITE, join.joinCondition());
    assertEquals(1, join.entityQuery().filters().size());
    assertTrue(join.entityQuery().filters().get(0) instanceof PlayerNameFilter);
  }

  @Test
  public void gameByBlackPlayerName() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "black.name:Carlsen");

    assertEquals(1, query.entityJoins().size());
    assertEquals(GameEntityJoinCondition.BLACK, query.entityJoins().get(0).joinCondition());
  }

  @Test
  public void gameByWinnerName() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "winner.name:Carlsen");

    assertEquals(1, query.entityJoins().size());
    assertEquals(GameEntityJoinCondition.WINNER, query.entityJoins().get(0).joinCondition());
  }

  @Test
  public void gameByLoserName() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "loser.name:Carlsen");

    assertEquals(1, query.entityJoins().size());
    assertEquals(GameEntityJoinCondition.LOSER, query.entityJoins().get(0).joinCondition());
  }

  @Test
  public void gameByWhitePlayerId() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "white:42");

    assertEquals(1, query.gameFilters().size());
    assertTrue(query.gameFilters().get(0) instanceof PlayerFilter);
  }

  @Test
  public void gameByWinnerFirstName() {
    GameQueryBuilder builder = new GameQueryBuilder();
    GameQuery query = builder.buildQuery(db, "winner.firstname:Magnus");

    assertEquals(1, query.entityJoins().size());
    GameEntityJoin<?> join = query.entityJoins().get(0);
    assertEquals(GameEntityJoinCondition.WINNER, join.joinCondition());
    assertTrue(join.entityQuery().filters().get(0) instanceof PlayerNameFilter);
    PlayerNameFilter filter = (PlayerNameFilter) join.entityQuery().filters().get(0);
    assertEquals("magnus", filter.firstName());
    assertEquals("", filter.lastName());
  }

  // --- AnnotatorQueryBuilder ---

  @Test
  public void annotatorByName() {
    EntityQuery<Annotator> query = new AnnotatorQueryBuilder().buildQuery(db, "Fischer");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof AnnotatorNameFilter);
  }

  @Test(expected = IllegalArgumentException.class)
  public void annotatorUnknownField() {
    new AnnotatorQueryBuilder().buildQuery(db, "title:foo");
  }

  // --- SourceQueryBuilder ---

  @Test
  public void sourceByTitle() {
    EntityQuery<Source> query = new SourceQueryBuilder().buildQuery(db, "ChessBase");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof SourceTitleFilter);
  }

  @Test
  public void sourceByNameAlias() {
    EntityQuery<Source> query = new SourceQueryBuilder().buildQuery(db, "name:Mega");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof SourceTitleFilter);
  }

  // --- TeamQueryBuilder ---

  @Test
  public void teamByTitle() {
    EntityQuery<Team> query = new TeamQueryBuilder().buildQuery(db, "Norway");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof TeamTitleFilter);
  }

  // --- GameTagQueryBuilder ---

  @Test
  public void gameTagByName() {
    EntityQuery<GameTag> query = new GameTagQueryBuilder().buildQuery(db, "Endgame");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof GameTagTitleFilter);
  }

  @Test
  public void gameTagByTitleAlias() {
    EntityQuery<GameTag> query = new GameTagQueryBuilder().buildQuery(db, "title:Tactics");

    assertEquals(1, query.filters().size());
    assertTrue(query.filters().get(0) instanceof GameTagTitleFilter);
  }
}
