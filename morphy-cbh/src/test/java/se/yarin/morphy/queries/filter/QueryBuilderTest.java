package se.yarin.morphy.queries.filter;

import static org.junit.Assert.*;

import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.queries.EntityQuery;

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
