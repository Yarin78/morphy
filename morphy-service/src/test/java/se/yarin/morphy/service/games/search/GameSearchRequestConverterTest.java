package se.yarin.morphy.service.games.search;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.games.filters.RatingRangeFilter.RatingColor;
import se.yarin.morphy.queries.GameEntityJoin;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.service.games.dto.GameSearchRequest;

class GameSearchRequestConverterTest {

  private Database database;
  private GameSearchRequestConverter queryBuilder;

  @BeforeEach
  void setUp() throws IOException {
    database = new Database();
    queryBuilder = new GameSearchRequestConverter();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (database != null) {
      database.close();
    }
  }

  /**
   * Helper class for building test requests with a fluent API. Makes tests more readable by
   * allowing named parameters.
   */
  private static class TestRequestBuilder {
    private Integer offset;
    private Integer limit;
    private String sortBy;
    private Boolean includeMoves;
    private Boolean includeText;
    private String filter;
    private String result;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String ecoCode;
    private Integer round;
    private Integer ratingMin;
    private Integer ratingMax;
    private String ratingMode;
    private Integer playerId;
    private String playerPosition;
    private Integer tournamentId;
    private Integer annotatorId;
    private Integer sourceId;
    private Integer teamId;
    private String teamPosition;
    private Integer gameTagId;
    private Boolean debugQueryPlans;
    private Boolean debugExecuteAllPlans;

    TestRequestBuilder offset(Integer offset) {
      this.offset = offset;
      return this;
    }

    TestRequestBuilder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    TestRequestBuilder sortBy(String sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    TestRequestBuilder includeMoves(Boolean includeMoves) {
      this.includeMoves = includeMoves;
      return this;
    }

    TestRequestBuilder includeText(Boolean includeText) {
      this.includeText = includeText;
      return this;
    }

    TestRequestBuilder filter(String filter) {
      this.filter = filter;
      return this;
    }

    TestRequestBuilder result(String result) {
      this.result = result;
      return this;
    }

    TestRequestBuilder dateFrom(LocalDate dateFrom) {
      this.dateFrom = dateFrom;
      return this;
    }

    TestRequestBuilder dateTo(LocalDate dateTo) {
      this.dateTo = dateTo;
      return this;
    }

    TestRequestBuilder ecoCode(String ecoCode) {
      this.ecoCode = ecoCode;
      return this;
    }

    TestRequestBuilder round(Integer round) {
      this.round = round;
      return this;
    }

    TestRequestBuilder ratingMin(Integer ratingMin) {
      this.ratingMin = ratingMin;
      return this;
    }

    TestRequestBuilder ratingMax(Integer ratingMax) {
      this.ratingMax = ratingMax;
      return this;
    }

    TestRequestBuilder ratingMode(String ratingMode) {
      this.ratingMode = ratingMode;
      return this;
    }

    TestRequestBuilder playerId(Integer playerId) {
      this.playerId = playerId;
      return this;
    }

    TestRequestBuilder playerPosition(String playerPosition) {
      this.playerPosition = playerPosition;
      return this;
    }

    TestRequestBuilder tournamentId(Integer tournamentId) {
      this.tournamentId = tournamentId;
      return this;
    }

    TestRequestBuilder annotatorId(Integer annotatorId) {
      this.annotatorId = annotatorId;
      return this;
    }

    TestRequestBuilder sourceId(Integer sourceId) {
      this.sourceId = sourceId;
      return this;
    }

    TestRequestBuilder teamId(Integer teamId) {
      this.teamId = teamId;
      return this;
    }

    TestRequestBuilder teamPosition(String teamPosition) {
      this.teamPosition = teamPosition;
      return this;
    }

    TestRequestBuilder gameTagId(Integer gameTagId) {
      this.gameTagId = gameTagId;
      return this;
    }

    TestRequestBuilder debugQueryPlans(Boolean debugQueryPlans) {
      this.debugQueryPlans = debugQueryPlans;
      return this;
    }

    TestRequestBuilder debugExecuteAllPlans(Boolean debugExecuteAllPlans) {
      this.debugExecuteAllPlans = debugExecuteAllPlans;
      return this;
    }

    GameSearchRequest build() {
      return new GameSearchRequest(
          offset,
          limit,
          sortBy,
          includeMoves,
          includeText,
          filter,
          result,
          dateFrom,
          dateTo,
          ecoCode,
          round,
          ratingMin,
          ratingMax,
          ratingMode,
          playerId,
          playerPosition,
          tournamentId,
          annotatorId,
          sourceId,
          teamId,
          teamPosition,
          gameTagId,
          debugQueryPlans,
          debugExecuteAllPlans,
          null);
    }
  }

  private static TestRequestBuilder request() {
    return new TestRequestBuilder();
  }

  @Nested
  @DisplayName("Simple Typed Parameter Tests")
  class SimpleTypedParameterTests {

    @Test
    @DisplayName("should convert result parameter to ResultsFilter")
    void testResultParameter() {
      GameSearchRequest request = request().result("1-0").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new ResultsFilter("1-0"), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert date range to DateRangeFilter")
    void testDateRangeParameter() {
      GameSearchRequest request =
          request()
              .dateFrom(LocalDate.of(2020, 1, 1))
              .dateTo(LocalDate.of(2020, 12, 31))
              .build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new DateRangeFilter(new Date(2020, 1, 1), new Date(2020, 12, 31)),
          query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert partial date range (dateFrom only)")
    void testPartialDateRangeFrom() {
      GameSearchRequest request = request().dateFrom(LocalDate.of(2020, 1, 1)).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new DateRangeFilter(new Date(2020, 1, 1), Date.unset()), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert partial date range (dateTo only)")
    void testPartialDateRangeTo() {
      GameSearchRequest request = request().dateTo(LocalDate.of(2020, 12, 31)).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new DateRangeFilter(Date.unset(), new Date(2020, 12, 31)), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert ECO code to EcoFilter")
    void testEcoParameter() {
      GameSearchRequest request = request().ecoCode("B90").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new EcoFilter("B90"), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert ECO wildcard to EcoFilter")
    void testEcoWildcardParameter() {
      GameSearchRequest request = request().ecoCode("B9*").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new EcoFilter("B9*"), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert round to RoundFilter")
    void testRoundParameter() {
      GameSearchRequest request = request().round(5).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new RoundFilter(5), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert rating range to RatingRangeFilter")
    void testRatingRangeParameter() {
      GameSearchRequest request = request().ratingMin(2600).ratingMax(2800).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new RatingRangeFilter(2600, 2800, RatingColor.ANY), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert rating min only with default max")
    void testRatingMinOnly() {
      GameSearchRequest request = request().ratingMin(2600).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new RatingRangeFilter(2600, 9999, RatingColor.ANY), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert rating max only with default min")
    void testRatingMaxOnly() {
      GameSearchRequest request = request().ratingMax(2800).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new RatingRangeFilter(0, 2800, RatingColor.ANY), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert rating with mode parameter")
    void testRatingWithMode() {
      GameSearchRequest request =
          request().ratingMin(2600).ratingMax(2800).ratingMode("white").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new RatingRangeFilter(2600, 2800, RatingColor.WHITE), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert playerId to PlayerFilter")
    void testPlayerIdParameter() {
      GameSearchRequest request = request().playerId(123).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new PlayerFilter(123, GameEntityJoinCondition.ANY), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert playerId with position to PlayerFilter")
    void testPlayerIdWithPosition() {
      GameSearchRequest request = request().playerId(123).playerPosition("white").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new PlayerFilter(123, GameEntityJoinCondition.WHITE), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert tournamentId to TournamentFilter")
    void testTournamentIdParameter() {
      GameSearchRequest request = request().tournamentId(456).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new TournamentFilter(456), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert annotatorId to AnnotatorFilter")
    void testAnnotatorIdParameter() {
      GameSearchRequest request = request().annotatorId(789).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new AnnotatorFilter(789), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert sourceId to SourceFilter")
    void testSourceIdParameter() {
      GameSearchRequest request = request().sourceId(321).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new SourceFilter(321), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert teamId to TeamFilter")
    void testTeamIdParameter() {
      GameSearchRequest request = request().teamId(654).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new TeamFilter(654, GameEntityJoinCondition.ANY), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should convert teamId with position to TeamFilter")
    void testTeamIdWithPosition() {
      GameSearchRequest request = request().teamId(654).teamPosition("black").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new TeamFilter(654, GameEntityJoinCondition.BLACK), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should convert gameTagId to GameTagFilter")
    void testGameTagIdParameter() {
      GameSearchRequest request = request().gameTagId(987).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new GameTagFilter(987), query.gameFilters().get(0));
      assertEquals(0, query.entityJoins().size());
    }
  }

  @Nested
  @DisplayName("Query Language Filter Tests")
  class QueryLanguageFilterTests {

    @Test
    @DisplayName("should parse simple result filter from query language")
    void testQueryLanguageResult() {
      GameSearchRequest request = request().filter("result:1-0").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new ResultsFilter("1-0"), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse single rating value from query language")
    void testQueryLanguageSingleRating() {
      GameSearchRequest request = request().filter("rating:2600").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new RatingRangeFilter(2600, 2600, RatingColor.ANY), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse multiple filters with AND")
    void testQueryLanguageMultipleFilters() {
      GameSearchRequest request = request().filter("result:1-0 AND eco:B90").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(2, query.gameFilters().size());
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new ResultsFilter("1-0"))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new EcoFilter("B90"))));
    }

    @Test
    @DisplayName("should parse ECO filter from query language")
    void testQueryLanguageEco() {
      GameSearchRequest request = request().filter("eco:B90").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(new EcoFilter("B90"), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse single date from query language with partial date expansion")
    void testQueryLanguageSingleDateWithExpansion() {
      GameSearchRequest request = request().filter("date:2020-06").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new DateRangeFilter(new Date(2020, 6, 1), new Date(2020, 6, 30)),
          query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse single date (year) from query language")
    void testQueryLanguageSingleDate() {
      GameSearchRequest request = request().filter("date:2020").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new DateRangeFilter(new Date(2020, 1, 1), new Date(2020, 12, 31)),
          query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse playerId from query language")
    void testQueryLanguagePlayerId() {
      GameSearchRequest request = request().filter("playerId:123").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new PlayerFilter(123, GameEntityJoinCondition.ANY), query.gameFilters().get(0));
    }

    @Test
    @DisplayName("should parse player shorthand as name search, not ID lookup")
    void testQueryLanguagePlayerShorthand() {
      GameSearchRequest request = request().filter("player:123").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      // player:123 is rewritten to player.name:123 (name search, not ID lookup)
      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      assertEquals(EntityType.PLAYER, query.entityJoins().get(0).getEntityType());
    }
  }

  @Nested
  @DisplayName("Entity Property Filter Tests")
  class EntityPropertyFilterTests {

    @Test
    @DisplayName("should create GameEntityJoin for player.name filter")
    void testPlayerNameFilter() {
      GameSearchRequest request = request().filter("player.name:Carlsen").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.PLAYER, join.getEntityType());
      assertEquals(GameEntityJoinCondition.ANY, join.joinCondition());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for player.name with position modifier")
    void testPlayerNameFilterWithPosition() {
      GameSearchRequest request = request().filter("player.name:Carlsen,position=white").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.PLAYER, join.getEntityType());
      assertEquals(GameEntityJoinCondition.WHITE, join.joinCondition());
    }

    @Test
    @DisplayName("should create GameEntityJoin for tournament.title filter")
    void testTournamentTitleFilter() {
      GameSearchRequest request = request().filter("tournament.title:Candidates").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.TOURNAMENT, join.getEntityType());
      assertEquals(GameEntityJoinCondition.ANY, join.joinCondition());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for tournament.place filter")
    void testTournamentPlaceFilter() {
      GameSearchRequest request = request().filter("tournament.place:London").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.TOURNAMENT, join.getEntityType());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for tournament.date filter")
    void testTournamentDateFilter() {
      GameSearchRequest request = request().filter("tournament.date:2024").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.TOURNAMENT, join.getEntityType());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for annotator.name filter")
    void testAnnotatorNameFilter() {
      GameSearchRequest request = request().filter("annotator.name:Kasparov").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.ANNOTATOR, join.getEntityType());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for source.title filter")
    void testSourceTitleFilter() {
      GameSearchRequest request = request().filter("source.title:ChessBase").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.SOURCE, join.getEntityType());
      assertEquals(1, join.entityQuery().filters().size());
    }


    @Test
    @DisplayName("should create GameEntityJoin for team.title filter")
    void testTeamTitleFilter() {
      GameSearchRequest request = request().filter("team.title:Norway").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.TEAM, join.getEntityType());
      assertEquals(GameEntityJoinCondition.ANY, join.joinCondition());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should create GameEntityJoin for team.name with position")
    void testTeamNameWithPosition() {
      GameSearchRequest request = request().filter("team.name:Norway,position=white").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.TEAM, join.getEntityType());
      assertEquals(GameEntityJoinCondition.WHITE, join.joinCondition());
    }

    @Test
    @DisplayName("should create GameEntityJoin for gametag.title filter")
    void testGameTagTitleFilter() {
      GameSearchRequest request = request().filter("gametag.title:Brilliant").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(1, query.entityJoins().size());
      GameEntityJoin<?> join = query.entityJoins().get(0);
      assertEquals(EntityType.GAME_TAG, join.getEntityType());
      assertEquals(1, join.entityQuery().filters().size());
    }

    @Test
    @DisplayName("should throw exception for unknown player property")
    void testUnknownPlayerProperty() {
      GameSearchRequest request = request().filter("player.rating:2700").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for unknown tournament property")
    void testUnknownTournamentProperty() {
      GameSearchRequest request = request().filter("tournament.round:5").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for unknown entity type")
    void testUnknownEntityType() {
      GameSearchRequest request = request().filter("unknown.property:value").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }
  }

  @Nested
  @DisplayName("Complex Combination Tests")
  class ComplexCombinationTests {

    @Test
    @DisplayName("should combine typed parameters and query language filters")
    void testTypedAndQueryLanguageCombination() {
      GameSearchRequest request =
          request().result("1-0").ratingMin(2600).filter("eco:B90 AND date:2024").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(4, query.gameFilters().size());
      assertTrue(
          query.gameFilters().stream().anyMatch(f -> f.equals(new ResultsFilter("1-0"))));
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(f -> f.equals(new RatingRangeFilter(2600, 9999, RatingColor.ANY))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new EcoFilter("B90"))));
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(
                  f -> f.equals(new DateRangeFilter(new Date(2024, 1, 1), new Date(2024, 12, 31)))));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should combine game filters and entity joins")
    void testGameFiltersAndEntityJoins() {
      GameSearchRequest request =
          request().result("1-0").filter("player.name:Carlsen AND eco:B90").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(2, query.gameFilters().size());
      assertTrue(
          query.gameFilters().stream().anyMatch(f -> f.equals(new ResultsFilter("1-0"))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new EcoFilter("B90"))));
      assertEquals(1, query.entityJoins().size());
      assertEquals(EntityType.PLAYER, query.entityJoins().get(0).getEntityType());
    }

    @Test
    @DisplayName("should handle multiple entity joins")
    void testMultipleEntityJoins() {
      GameSearchRequest request =
          request()
              .filter("player.name:Carlsen AND tournament.title:Candidates AND annotator.name:Kasparov")
              .build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(3, query.entityJoins().size());
      assertTrue(
          query.entityJoins().stream().anyMatch(j -> j.getEntityType() == EntityType.PLAYER));
      assertTrue(
          query.entityJoins().stream().anyMatch(j -> j.getEntityType() == EntityType.TOURNAMENT));
      assertTrue(
          query.entityJoins().stream().anyMatch(j -> j.getEntityType() == EntityType.ANNOTATOR));
    }

    @Test
    @DisplayName("should handle mix of ID filters and property filters for same entity")
    void testIdAndPropertyFiltersMix() {
      GameSearchRequest request = request().playerId(123).filter("player.name:Carlsen").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(1, query.gameFilters().size());
      assertEquals(
          new PlayerFilter(123, GameEntityJoinCondition.ANY), query.gameFilters().get(0));
      assertEquals(1, query.entityJoins().size());
      assertEquals(EntityType.PLAYER, query.entityJoins().get(0).getEntityType());
    }

    @Test
    @DisplayName("should handle all entity ID filters")
    void testAllEntityIdFilters() {
      GameSearchRequest request =
          request()
              .playerId(1)
              .tournamentId(2)
              .annotatorId(3)
              .sourceId(4)
              .teamId(5)
              .gameTagId(6)
              .build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(6, query.gameFilters().size());
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(f -> f.equals(new PlayerFilter(1, GameEntityJoinCondition.ANY))));
      assertTrue(
          query.gameFilters().stream().anyMatch(f -> f.equals(new TournamentFilter(2))));
      assertTrue(
          query.gameFilters().stream().anyMatch(f -> f.equals(new AnnotatorFilter(3))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new SourceFilter(4))));
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(f -> f.equals(new TeamFilter(5, GameEntityJoinCondition.ANY))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new GameTagFilter(6))));
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should handle all game property filters")
    void testAllGamePropertyFilters() {
      GameSearchRequest request =
          request()
              .result("1-0")
              .dateFrom(LocalDate.of(2020, 1, 1))
              .dateTo(LocalDate.of(2020, 12, 31))
              .ecoCode("B90")
              .round(5)
              .ratingMin(2600)
              .ratingMax(2800)
              .build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(5, query.gameFilters().size());
      assertTrue(
          query.gameFilters().stream().anyMatch(f -> f.equals(new ResultsFilter("1-0"))));
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(
                  f ->
                      f.equals(
                          new DateRangeFilter(new Date(2020, 1, 1), new Date(2020, 12, 31)))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new EcoFilter("B90"))));
      assertTrue(query.gameFilters().stream().anyMatch(f -> f.equals(new RoundFilter(5))));
      assertTrue(
          query
              .gameFilters()
              .stream()
              .anyMatch(f -> f.equals(new RatingRangeFilter(2600, 2800, RatingColor.ANY))));
      assertEquals(0, query.entityJoins().size());
    }
  }

  @Nested
  @DisplayName("Sorting Tests")
  class SortingTests {

    @Test
    @DisplayName("should create ID sort order ascending")
    void testSortById() {
      GameSearchRequest request = request().sortBy("+id").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertNotNull(query.sortOrder());
      assertFalse(query.sortOrder().isNone());
    }

    @Test
    @DisplayName("should create date sort order ascending")
    void testSortByDateAsc() {
      GameSearchRequest request = request().sortBy("+date").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertNotNull(query.sortOrder());
      assertFalse(query.sortOrder().isNone());
    }

    @Test
    @DisplayName("should create date sort order descending")
    void testSortByDateDesc() {
      GameSearchRequest request = request().sortBy("-date").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertNotNull(query.sortOrder());
      assertFalse(query.sortOrder().isNone());
    }

    @Test
    @DisplayName("should create no sort order for unsupported sort field")
    void testUnsupportedSortField() {
      GameSearchRequest request = request().sortBy("-whiteEloMissing").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertNotNull(query.sortOrder());
      assertTrue(query.sortOrder().isNone());
    }

    @Test
    @DisplayName("should use default sort (ID) when sortBy is not specified")
    void testDefaultSort() {
      GameSearchRequest request = request().build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertNotNull(query.sortOrder());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle empty request")
    void testEmptyRequest() {
      GameSearchRequest request = request().build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(0, query.entityJoins().size());
      assertNotNull(query.sortOrder());
    }

    @Test
    @DisplayName("should handle null filter string")
    void testNullFilterString() {
      GameSearchRequest request = request().filter(null).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should handle empty filter string")
    void testEmptyFilterString() {
      GameSearchRequest request = request().filter("").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should handle blank filter string")
    void testBlankFilterString() {
      GameSearchRequest request = request().filter("   ").build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.gameFilters().size());
      assertEquals(0, query.entityJoins().size());
    }

    @Test
    @DisplayName("should throw exception for invalid result value")
    void testInvalidResultValue() {
      GameSearchRequest request = request().result("invalid").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for invalid ECO code")
    void testInvalidEcoCode() {
      GameSearchRequest request = request().ecoCode("Z99").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for invalid player position")
    void testInvalidPlayerPosition() {
      GameSearchRequest request = request().playerId(123).playerPosition("invalid").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for invalid team position")
    void testInvalidTeamPosition() {
      GameSearchRequest request = request().teamId(456).teamPosition("invalid").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should throw exception for invalid rating mode")
    void testInvalidRatingMode() {
      GameSearchRequest request =
          request().ratingMin(2600).ratingMode("invalid").build();

      assertThrows(IllegalArgumentException.class, () -> queryBuilder.buildQuery(database, request));
    }

    @Test
    @DisplayName("should handle all player positions")
    void testAllPlayerPositions() {
      var positionMap =
          java.util.Map.of(
              "any", GameEntityJoinCondition.ANY,
              "both", GameEntityJoinCondition.BOTH,
              "white", GameEntityJoinCondition.WHITE,
              "black", GameEntityJoinCondition.BLACK,
              "winner", GameEntityJoinCondition.WINNER,
              "loser", GameEntityJoinCondition.LOSER);

      for (var entry : positionMap.entrySet()) {
        GameSearchRequest request = request().playerId(123).playerPosition(entry.getKey()).build();
        GameQuery query = queryBuilder.buildQuery(database, request);
        assertEquals(1, query.gameFilters().size());
        assertEquals(new PlayerFilter(123, entry.getValue()), query.gameFilters().get(0));
      }
    }

    @Test
    @DisplayName("should handle all team positions")
    void testAllTeamPositions() {
      var positionMap =
          java.util.Map.of(
              "any",
              GameEntityJoinCondition.ANY,
              "white",
              GameEntityJoinCondition.WHITE,
              "black",
              GameEntityJoinCondition.BLACK);

      for (var entry : positionMap.entrySet()) {
        GameSearchRequest request = request().teamId(456).teamPosition(entry.getKey()).build();
        GameQuery query = queryBuilder.buildQuery(database, request);
        assertEquals(1, query.gameFilters().size());
        assertEquals(new TeamFilter(456, entry.getValue()), query.gameFilters().get(0));
      }
    }

    @Test
    @DisplayName("should handle all rating modes")
    void testAllRatingModes() {
      var modeMap =
          java.util.Map.of(
              "any", RatingColor.ANY,
              "both", RatingColor.BOTH,
              "white", RatingColor.WHITE,
              "black", RatingColor.BLACK,
              "average", RatingColor.AVERAGE,
              "difference", RatingColor.DIFFERENCE);

      for (var entry : modeMap.entrySet()) {
        GameSearchRequest request = request().ratingMin(2600).ratingMode(entry.getKey()).build();
        GameQuery query = queryBuilder.buildQuery(database, request);
        assertEquals(1, query.gameFilters().size());
        assertEquals(new RatingRangeFilter(2600, 9999, entry.getValue()), query.gameFilters().get(0));
      }
    }

    @Test
    @DisplayName("should use limit of 0 at query level (pagination handled elsewhere)")
    void testQueryLimit() {
      GameSearchRequest request = request().limit(100).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(0, query.limit());
    }

    @Test
    @DisplayName("should handle very long filter query")
    void testLongFilterQuery() {
      String longFilter =
          "result:1-0 AND eco:B90 AND date:2024 AND round:5 AND "
              + "player.name:Carlsen AND tournament.title:Candidates AND "
              + "annotator.name:Kasparov AND source.title:ChessBase";
      GameSearchRequest request = request().filter(longFilter).build();

      GameQuery query = queryBuilder.buildQuery(database, request);

      assertEquals(4, query.gameFilters().size());
      assertEquals(4, query.entityJoins().size());
    }
  }
}
