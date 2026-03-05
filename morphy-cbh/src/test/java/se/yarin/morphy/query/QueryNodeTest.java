package se.yarin.morphy.query;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.filters.EcoFilter;

public class QueryNodeTest {

  private Database db;

  @Before
  public void setup() {
    db = ResourceLoader.openWorldChDatabase();
  }

  // --- TableScan tests ---

  @Test
  public void tableScanStreamsAllGames() {
    var scan = TableScan.gameHeaders(db);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertEquals(db.count(), results.size());
    assertEquals(1, results.get(0).id());
    assertEquals(db.count(), results.get(results.size() - 1).id());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void tableScanWithFilter() {
    // Game 10 is Steinitz from 1886, so white elo should be 0 (no ratings back then)
    var scan = TableScan.gameHeaders(db, gh -> gh.whiteElo() > 0);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertTrue(results.size() > 0);
    assertTrue(results.size() < db.count());
    for (var qd : results) {
      assertTrue(qd.data().whiteElo() > 0);
    }
  }

  @Test
  public void tableScanSortOrderIsByIdAscending() {
    var scan = TableScan.gameHeaders(db);
    assertFalse(scan.sortOrder().isNone());
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(scan.mayContainDuplicates());
  }

  @Test
  public void tableScanEmptyRange() {
    var scan = TableScan.gameHeaders(db, 3, 3, null);
    assertEquals(0, scan.stream().toList().size());
  }

  @Test
  public void tableScanEntities() {
    var scan = TableScan.entities(db.playerIndex());
    List<QueryData<Player>> results = scan.stream().toList();
    assertEquals(db.playerIndex().count(), results.size());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void tableScanEntitiesWithFilter() {
    var scan = TableScan.entities(db.playerIndex(), p -> p.count() >= 10);
    List<QueryData<Player>> results = scan.stream().toList();
    assertTrue(results.size() >= 1);
    for (var qd : results) {
      assertTrue(qd.data().count() >= 10);
    }
  }

  // --- EntityIndexScan tests ---

  @Test
  public void entityIndexScanStreamsPlayers() {
    try (var txn = new DatabaseReadTransaction(db)) {
      var playerTxn = txn.playerTransaction();
      var scan =
          new EntityIndexScan<>(
              playerTxn,
              SortOrder.none(),
              null,
              null,
              null,
              null,
              false);
      List<QueryData<Player>> results = scan.stream().toList();
      assertEquals(db.playerIndex().count(), results.size());
      assertNotNull(results.get(0).data());
    }
  }

  @Test
  public void entityIndexScanWithRange() {
    try (var txn = new DatabaseReadTransaction(db)) {
      var playerTxn = txn.playerTransaction();
      // Use a range that covers A-K in the player index
      Player start = Player.ofFullName("A");
      Player end = Player.ofFullName("K");
      var scan =
          new EntityIndexScan<>(
              playerTxn,
              SortOrder.none(),
              start,
              end,
              null,
              null,
              false);
      List<QueryData<Player>> results = scan.stream().toList();
      assertTrue(results.size() >= 1);
      // All results should be within range according to entity comparison
      int totalPlayers = db.playerIndex().count();
      assertTrue(results.size() < totalPlayers);
      for (var qd : results) {
        assertNotNull(qd.data());
      }
    }
  }

  @Test
  public void entityIndexScanWithPostFilter() {
    try (var txn = new DatabaseReadTransaction(db)) {
      var playerTxn = txn.playerTransaction();
      var scan =
          new EntityIndexScan<>(
              playerTxn,
              SortOrder.none(),
              null,
              null,
              null,
              player -> player.count() >= 50,
              false);
      List<QueryData<Player>> results = scan.stream().toList();
      assertTrue(results.size() >= 1);
      for (var qd : results) {
        assertNotNull(qd.data());
        assertTrue(qd.data().count() >= 50);
      }
    }
  }

  // --- GameEntityIndexScan tests ---

  @Test
  public void gameEntityIndexScanSingleEntity() {
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);
    // Player 10 is a known player in the World-ch database
    Player player = db.getPlayer(10);
    assertNotNull(player);

    var scan =
        new GameEntityIndexScan(
            gei, EntityType.PLAYER, player.id(), player.id() + 1);
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(scan.mayContainDuplicates());

    List<QueryData<Void>> results = scan.stream().toList();
    assertEquals(player.count(), results.size());
  }

  // --- Lookup tests ---

  @Test
  public void lookupConvertsType() {
    var idSource = TableScan.gameHeaders(db);
    var lookup =
        new Lookup<>(
            idSource,
            id -> {
              GameHeader gh = db.gameHeaderIndex().getGameHeader(id);
              return gh.whitePlayerId() + gh.blackPlayerId();
            });
    List<QueryData<Integer>> results = lookup.stream().toList();
    assertEquals(db.count(), results.size());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void lookupWithFilter() {
    var idSource = TableScan.gameHeaders(db);
    var lookup =
        new Lookup<>(
            idSource,
            id -> db.gameHeaderIndex().getGameHeader(id),
            gh -> !gh.guidingText());
    List<QueryData<GameHeader>> results = lookup.stream().toList();
    assertTrue(results.size() > 0);
    assertTrue(results.size() <= db.count());
  }

  @Test
  public void lookupPreservesSortOrder() {
    var idSource = TableScan.gameHeaders(db);
    var lookup =
        new Lookup<>(idSource, id -> db.gameHeaderIndex().getGameHeader(id));
    assertTrue(lookup.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  // --- DataFilter in TableScan tests ---

  @Test
  public void tableScanDataFilterReducesResults() {
    EcoFilter ecoFilter = new EcoFilter("C*");
    var scan = TableScan.gameHeaders(db, gh -> ecoFilter.matches(0, gh));
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertTrue(results.size() > 0);
    assertTrue(results.size() < db.count());
    for (var qd : results) {
      assertTrue(qd.data().eco().toString().startsWith("C"));
    }
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  // --- Sort tests ---

  @Test
  public void sortReordersStream() {
    var scan = TableScan.gameHeaders(db);
    SortField<GameHeader> descId =
        new SortField<>(
            java.util.Comparator.comparingInt(QueryData::id),
            "id",
            SortOrder.Direction.DESCENDING);
    SortOrder<GameHeader> descOrder =
        SortOrder.of(descId, SortOrder.Direction.DESCENDING);
    var sort = new Sort<>(scan, descOrder);
    List<QueryData<GameHeader>> results = sort.stream().toList();
    assertEquals(db.count(), results.size());
    assertEquals(db.count(), results.get(0).id());
    assertEquals(1, results.get(results.size() - 1).id());
  }

  // --- Distinct tests ---

  @Test
  public void distinctRemovesDuplicatesSequential() {
    var source = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);
    var distinct = new Distinct<>(source);
    List<QueryData<String>> results = distinct.stream().toList();
    assertEquals(3, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals(2, results.get(1).id());
    assertEquals(3, results.get(2).id());
    assertFalse(distinct.mayContainDuplicates());
  }

  @Test
  public void distinctRemovesDuplicatesHash() {
    var source = new TestQueryNode<>(
        List.of(
            new QueryData<>(3, "c"),
            new QueryData<>(1, "a"),
            new QueryData<>(3, "c"),
            new QueryData<>(2, "b"),
            new QueryData<>(1, "a")),
        SortOrder.none(),
        true);
    var distinct = new Distinct<>(source);
    List<QueryData<String>> results = distinct.stream().toList();
    assertEquals(3, results.size());
    assertFalse(distinct.mayContainDuplicates());
  }

  // --- Limit tests ---

  @Test
  public void limitTruncatesStream() {
    var scan = TableScan.gameHeaders(db);
    var limit = new Limit<>(scan, 3);
    List<QueryData<GameHeader>> results = limit.stream().toList();
    assertEquals(3, results.size());
    assertTrue(limit.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void limitRejectsZero() {
    var scan = TableScan.gameHeaders(db);
    new Limit<>(scan, 0);
  }

  // --- MergeJoin tests ---

  @Test
  public void mergeJoinMatchesById() {
    var left = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = new TestQueryNode<>(
        List.of(
            new QueryData<>(2, "B"),
            new QueryData<>(3, "C"),
            new QueryData<>(4, "D")),
        SortOrder.byId(),
        false);

    var join = new MergeJoin<>(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("b", results.get(0).data());
    assertEquals(3, results.get(1).id());
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(join.mayContainDuplicates());
  }

  @Test
  public void mergeJoinEmptyResult() {
    var left = new TestQueryNode<>(
        List.of(new QueryData<>(1, "a"), new QueryData<>(3, "c")),
        SortOrder.byId(),
        false);
    var right = new TestQueryNode<>(
        List.of(new QueryData<>(2, "B"), new QueryData<>(4, "D")),
        SortOrder.byId(),
        false);

    var join = new MergeJoin<>(left, right);
    assertEquals(0, join.stream().toList().size());
  }

  // --- HashJoin tests ---

  @Test
  public void hashJoinMatchesById() {
    var left = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = new TestQueryNode<>(
        List.of(
            new QueryData<>(2, "B"),
            new QueryData<>(3, "C"),
            new QueryData<>(5, "E")),
        SortOrder.byId(),
        false);

    var join = new HashJoin<>(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals(5, results.get(1).id());
    assertFalse(join.mayContainDuplicates());
  }

  @Test
  public void hashJoinPreservesLeftSortOrder() {
    var left = new TestQueryNode<>(
        List.of(new QueryData<>(1, "a")),
        SortOrder.byId(),
        false);
    var right = new TestQueryNode<>(
        List.of(new QueryData<>(1, "A")),
        SortOrder.none(),
        false);

    var join = new HashJoin<>(left, right);
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  // --- LoopJoin tests ---

  @Test
  public void loopJoinWithParameterizedInner() {
    var outer = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "game1"),
            new QueryData<>(2, "game2")),
        SortOrder.byId(),
        false);

    var join =
        new LoopJoin<>(
            outer,
            qd -> qd.id(),
            key -> Stream.of(new QueryData<>(key, "inner-" + key)),
            (outerRow, innerRow) -> outerRow.withExtra(innerRow.data()));

    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals("inner-1", results.get(0).extra(String.class));
    assertEquals("inner-2", results.get(1).extra(String.class));
    assertTrue(join.sortOrder().isNone());
    assertTrue(join.mayContainDuplicates());
  }

  @Test
  public void loopJoinResultMapperCanFilterByReturningNull() {
    var outer = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "game1"),
            new QueryData<>(2, "game2"),
            new QueryData<>(3, "game3")),
        SortOrder.byId(),
        false);

    var join =
        new LoopJoin<>(
            outer,
            qd -> qd.id(),
            key -> Stream.of(new QueryData<>(key, "inner-" + key)),
            (outerRow, innerRow) -> outerRow.id() % 2 == 0 ? outerRow : null);

    List<QueryData<String>> results = join.stream().toList();
    assertEquals(1, results.size());
    assertEquals(2, results.get(0).id());
  }

  // --- QueryData tests ---

  @Test
  public void queryDataIdOnly() {
    var qd = new QueryData<String>(5);
    assertEquals(5, qd.id());
    assertNull(qd.data());
    assertNull(qd.extra());
  }

  @Test
  public void queryDataWithData() {
    var qd = new QueryData<>(3, "hello");
    assertEquals(3, qd.id());
    assertEquals("hello", qd.data());
    assertNull(qd.extra());
  }

  @Test
  public void queryDataWithExtra() {
    var qd = new QueryData<>(1, "data", 42);
    assertEquals(1, qd.id());
    assertEquals("data", qd.data());
    assertEquals(Integer.valueOf(42), qd.extra(Integer.class));
    assertNull(qd.extra(String.class));
  }

  @Test
  public void queryDataCopyMethods() {
    var qd = new QueryData<>(1, "data");
    var withExtra = qd.withExtra("extra");
    assertEquals("extra", withExtra.extra(String.class));
    assertEquals("data", withExtra.data());

    var withNewData = qd.withData("newData");
    assertEquals("newData", withNewData.data());
    assertEquals(1, withNewData.id());
  }

  @Test(expected = IllegalArgumentException.class)
  public void queryDataRejectsNegativeId() {
    new QueryData<String>(-1);
  }

  @Test
  public void queryDataMergerPrefersLeftData() {
    var merger = QueryData.<String>merger();
    var left = new QueryData<>(1, "left");
    var right = new QueryData<>(1, "right");
    var merged = merger.apply(left, right);
    assertEquals("left", merged.data());
  }

  @Test
  public void queryDataMergerFallsBackToRightData() {
    var merger = QueryData.<String>merger();
    var left = new QueryData<String>(1);
    var right = new QueryData<>(1, "right");
    var merged = merger.apply(left, right);
    assertEquals("right", merged.data());
  }

  // --- SortOrder tests ---

  @Test
  public void sortOrderNoneIsNone() {
    assertTrue(SortOrder.none().isNone());
    assertFalse(SortOrder.byId().isNone());
  }

  @Test
  public void sortOrderIsSameOrStronger() {
    SortOrder<String> byId = SortOrder.byId();
    SortOrder<String> none = SortOrder.none();
    assertTrue(byId.isSameOrStronger(none));
    assertTrue(byId.isSameOrStronger(byId));
    assertFalse(none.isSameOrStronger(byId));
  }

  // --- debugString tests ---

  @Test
  public void debugStringShowsTree() {
    var scan = TableScan.gameHeaders(db);
    var sort = new Sort<>(scan, SortOrder.byId());
    var limit = new Limit<>(sort, 10);

    String debug = limit.debugString();
    assertTrue(debug.contains("Limit[10]"));
    assertTrue(debug.contains("Sort"));
    assertTrue(debug.contains("TableScan"));
    assertTrue(debug.contains("  Sort"));
    assertTrue(debug.contains("    TableScan"));
  }

  @Test
  public void debugStringMergeJoinShowsBothBranches() {
    var left = new TestQueryNode<>(
        List.of(new QueryData<>(1, "a")),
        SortOrder.byId(),
        false);
    var right = new TestQueryNode<>(
        List.of(new QueryData<>(1, "b")),
        SortOrder.byId(),
        false);
    var join = new MergeJoin<>(left, right);

    String debug = join.debugString();
    assertTrue(debug.contains("MergeJoin"));
    assertTrue(debug.contains("TestNode"));
    String[] lines = debug.split("\n");
    assertTrue(lines.length >= 3);
  }

  // --- Sort order propagation tests ---

  @Test
  public void sortOrderPropagatesThroughLookupAndLimit() {
    var scan = TableScan.gameHeaders(db);
    var lookup = new Lookup<>(scan, id -> db.gameHeaderIndex().getGameHeader(id));
    var limit = new Limit<>(lookup, 10);

    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertTrue(lookup.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertTrue(limit.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  @Test
  public void sortNodeChangesSortOrder() {
    var source = new TestQueryNode<>(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    SortField<String> nameSort =
        new SortField<>(
            java.util.Comparator.comparing(qd -> qd.data()),
            "name");
    SortOrder<String> byName = SortOrder.of(nameSort, SortOrder.Direction.ASCENDING);
    var sort = new Sort<>(source, byName);

    assertFalse(sort.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertTrue(sort.sortOrder().isSameOrStronger(byName));
  }

  // --- Integration tests ---

  @Test
  public void tableScanFilterLimitIntegration() {
    // Scan from game 3 onwards, limit to 2
    var scan = TableScan.gameHeaders(db, 3, db.count() + 1, null);
    var limit = new Limit<>(scan, 2);

    List<QueryData<GameHeader>> results = limit.stream().toList();
    assertEquals(2, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals(4, results.get(1).id());
  }

  @Test
  public void mergeJoinWithTableScansIntegration() {
    // Two scans with different ranges that overlap on games 20-29
    var left = TableScan.gameHeaders(db, 10, 30, null);
    var right = TableScan.gameHeaders(db, 20, 40, null);
    var join = new MergeJoin<>(left, right);

    List<QueryData<GameHeader>> results = join.stream().toList();
    assertEquals(10, results.size());
    assertEquals(20, results.get(0).id());
    assertEquals(29, results.get(9).id());
  }

  // --- Sample query composition tests ---

  @Test
  public void gamesBySinglePlayerViaEntityIndex() {
    // Find all games by player 10 using entity index scan + lookup
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);
    Player player = db.getPlayer(10);

    var gameIdsByPlayer =
        new GameEntityIndexScan(gei, EntityType.PLAYER, player.id(), player.id() + 1);
    var gameLookup =
        new Lookup<>(gameIdsByPlayer, id -> db.gameHeaderIndex().getGameHeader(id));

    List<QueryData<GameHeader>> results = gameLookup.stream().toList();
    assertEquals(player.count(), results.size());
    for (var qd : results) {
      GameHeader gh = qd.data();
      assertNotNull(gh);
      assertTrue(
          gh.whitePlayerId() == player.id() || gh.blackPlayerId() == player.id());
    }
    assertTrue(gameLookup.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(gameLookup.mayContainDuplicates());
  }

  @Test
  public void gamesByMultiplePlayersViaSortAndDistinct() {
    // Find all games by player 10 or player 11, union via sort + distinct + lookup
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);
    Player player10 = db.getPlayer(10);
    Player player11 = db.getPlayer(11);

    var gamesByP10 =
        new GameEntityIndexScan(gei, EntityType.PLAYER, player10.id(), player10.id() + 1);
    var gamesByP11 =
        new GameEntityIndexScan(gei, EntityType.PLAYER, player11.id(), player11.id() + 1);

    List<QueryData<Void>> combined = Stream.concat(
        gamesByP10.stream(), gamesByP11.stream()).toList();
    var unionSource = new TestQueryNode<>(combined, SortOrder.none(), true);

    var sorted = new Sort<>(unionSource, SortOrder.byId());
    var distinct = new Distinct<>(sorted);
    var gameLookup =
        new Lookup<>(distinct, id -> db.gameHeaderIndex().getGameHeader(id));

    List<QueryData<GameHeader>> results = gameLookup.stream().toList();
    // Union should have at least as many as the larger set, but no more than the sum
    assertTrue(results.size() >= Math.max(player10.count(), player11.count()));
    assertTrue(results.size() <= player10.count() + player11.count());
    assertFalse(gameLookup.mayContainDuplicates());

    // Verify sorted by id
    for (int i = 1; i < results.size(); i++) {
      assertTrue(results.get(i).id() > results.get(i - 1).id());
    }
  }

  @Test
  public void gamesByPlayerAndTournamentViaMergeJoin() {
    // Find games where player 10 played in tournament 0
    GameEntityIndex playerGei = db.gameEntityIndex(EntityType.PLAYER);
    GameEntityIndex tournamentGei = db.gameEntityIndex(EntityType.TOURNAMENT);
    assertNotNull(playerGei);
    assertNotNull(tournamentGei);

    Player player = db.getPlayer(10);
    Tournament tournament = db.tournamentIndex().get(0);

    var gamesByPlayer =
        new GameEntityIndexScan(playerGei, EntityType.PLAYER, player.id(), player.id() + 1);
    var gamesByTournament =
        new GameEntityIndexScan(
            tournamentGei, EntityType.TOURNAMENT, tournament.id(), tournament.id() + 1);

    var intersection = new MergeJoin<>(gamesByPlayer, gamesByTournament);
    var gameLookup =
        new Lookup<>(intersection, id -> db.gameHeaderIndex().getGameHeader(id));

    List<QueryData<GameHeader>> results = gameLookup.stream().toList();
    // Intersection should be <= min of both sets
    assertTrue(results.size() <= Math.min(player.count(), tournament.count()));
    for (var qd : results) {
      GameHeader gh = qd.data();
      assertNotNull(gh);
      assertTrue(gh.whitePlayerId() == player.id() || gh.blackPlayerId() == player.id());
      assertEquals(tournament.id(), gh.tournamentId());
    }
  }

  @Test
  public void gameScanWithFilterAndEntityLookupViaHashJoin() {
    // Find all games, then hash-join with game IDs of prolific players (>= 50 games)
    try (var txn = new DatabaseReadTransaction(db)) {
      var gameScan = TableScan.gameHeaders(db);

      var playerTxn = txn.playerTransaction();
      var prolificPlayers =
          new EntityIndexScan<>(
              playerTxn, SortOrder.none(), null, null, null,
              player -> player.count() >= 50,
              false);

      GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
      assertNotNull(gei);

      var gameIdsByProlificPlayers = new LoopJoin<>(
          prolificPlayers,
          qd -> qd.id(),
          playerId -> gei.stream(playerId, EntityType.PLAYER, false).map(QueryData::new),
          (outerRow, innerRow) -> new QueryData<>(innerRow.id(), outerRow.data()));

      var sorted = new Sort<>(gameIdsByProlificPlayers, SortOrder.byId());
      var distinct = new Distinct<>(sorted);

      var hashJoin = new HashJoin<>(gameScan, new Lookup<>(distinct,
          id -> db.gameHeaderIndex().getGameHeader(id)));

      List<QueryData<GameHeader>> results = hashJoin.stream().toList();
      assertTrue(results.size() > 0);
      assertTrue(results.size() <= db.count());
      assertFalse(hashJoin.mayContainDuplicates());
    }
  }

  @Test
  public void entityQueryWithGameSubquery() {
    // Find players who played in tournament 0
    GameEntityIndex tournamentGei = db.gameEntityIndex(EntityType.TOURNAMENT);
    assertNotNull(tournamentGei);

    Tournament tournament = db.tournamentIndex().get(0);

    try (var txn = new DatabaseReadTransaction(db)) {
      // Step 1: Find game IDs in the tournament
      var gamesInTournament =
          new GameEntityIndexScan(
              tournamentGei, EntityType.TOURNAMENT, tournament.id(), tournament.id() + 1);

      // Step 2: Extract player IDs from game headers via LoopJoin
      var playerIds = new LoopJoin<Void, GameHeader>(
          gamesInTournament,
          qd -> qd.id(),
          gameId -> {
            GameHeader gh = db.gameHeaderIndex().getGameHeader(gameId);
            return Stream.of(
                new QueryData<>(gh.whitePlayerId(), gh),
                new QueryData<>(gh.blackPlayerId(), gh));
          },
          (outerRow, innerRow) -> new QueryData<>(innerRow.id()));

      // Step 3: Sort + Distinct to get unique player IDs
      var sorted = new Sort<>(playerIds, SortOrder.byId());
      var distinct = new Distinct<>(sorted);

      // Step 4: Lookup player data
      var playerLookup = new Lookup<>(
          distinct,
          id -> txn.playerTransaction().get(id));

      List<QueryData<Player>> results = playerLookup.stream().toList();
      // Tournament should have at least 2 players
      assertTrue(results.size() >= 2);
      // Should not exceed 2 * number of games in tournament
      assertTrue(results.size() <= 2 * tournament.count());
      assertFalse(playerLookup.mayContainDuplicates());

      for (var qd : results) {
        assertNotNull(qd.data());
        assertNotNull(qd.data().lastName());
      }
    }
  }

  @Test
  public void sortFilterLimitTopNQuery() {
    // Top 5 games by id descending - "latest N games" query
    var scan = TableScan.gameHeaders(db);
    SortField<GameHeader> descId =
        new SortField<>(
            java.util.Comparator.comparingInt(QueryData::id),
            "id",
            SortOrder.Direction.DESCENDING);
    var sort = new Sort<>(scan, SortOrder.of(descId, SortOrder.Direction.DESCENDING));
    var limit = new Limit<>(sort, 5);

    List<QueryData<GameHeader>> results = limit.stream().toList();
    assertEquals(5, results.size());
    assertEquals(db.count(), results.get(0).id());
    assertEquals(db.count() - 4, results.get(4).id());
  }

  @Test
  public void mergeJoinTwoEntityIndexScansAndFilter() {
    // Find games involving both player 10 and player 11
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);

    Player player10 = db.getPlayer(10);
    Player player11 = db.getPlayer(11);

    var gamesByP10 =
        new GameEntityIndexScan(gei, EntityType.PLAYER, player10.id(), player10.id() + 1);
    var gamesByP11 =
        new GameEntityIndexScan(gei, EntityType.PLAYER, player11.id(), player11.id() + 1);

    var intersection = new MergeJoin<>(gamesByP10, gamesByP11);
    var gameLookup =
        new Lookup<>(intersection, id -> db.gameHeaderIndex().getGameHeader(id));

    List<QueryData<GameHeader>> results = gameLookup.stream().toList();
    // Every result must involve both players
    for (var qd : results) {
      GameHeader gh = qd.data();
      assertNotNull(gh);
      boolean hasP10 = gh.whitePlayerId() == player10.id() || gh.blackPlayerId() == player10.id();
      boolean hasP11 = gh.whitePlayerId() == player11.id() || gh.blackPlayerId() == player11.id();
      assertTrue(hasP10 && hasP11);
    }
  }

  @Test
  public void lookupFilterEquivalentToTableScanFilter() {
    EcoFilter ecoFilter = new EcoFilter("B*");
    var scan = TableScan.gameHeaders(db);
    var lookup = new Lookup<>(scan, id -> db.gameHeaderIndex().getGameHeader(id),
        gh -> ecoFilter.matches(0, gh));

    var filtered = TableScan.gameHeaders(db, gh -> ecoFilter.matches(0, gh));

    List<QueryData<GameHeader>> lookupResults = lookup.stream().toList();
    List<QueryData<GameHeader>> scanResults = filtered.stream().toList();

    assertEquals(scanResults.size(), lookupResults.size());
    for (int i = 0; i < lookupResults.size(); i++) {
      assertEquals(scanResults.get(i).id(), lookupResults.get(i).id());
    }
  }

  @Test
  public void debugStringForComplexQueryPlan() {
    var left = new TestQueryNode<>(
        List.of(new QueryData<>(1, "a")), SortOrder.byId(), false);
    var right = new TestQueryNode<>(
        List.of(new QueryData<>(1, "b")), SortOrder.byId(), false);
    var join = new MergeJoin<>(left, right);
    var sort = new Sort<>(join, SortOrder.byId());
    var limit = new Limit<>(sort, 10);

    String debug = limit.debugString();
    assertTrue(debug.contains("Limit[10]"));
    assertTrue(debug.contains("Sort"));
    assertTrue(debug.contains("MergeJoin"));
    String[] lines = debug.split("\n");
    assertTrue(lines.length >= 4);
  }

  // --- Test helper: in-memory query node ---

  private static class TestQueryNode<T> extends QueryNode<T> {
    private final List<QueryData<T>> data;
    private final SortOrder<T> sortOrder;
    private final boolean duplicates;

    TestQueryNode(List<QueryData<T>> data, SortOrder<T> sortOrder, boolean duplicates) {
      this.data = data;
      this.sortOrder = sortOrder;
      this.duplicates = duplicates;
    }

    @Override
    public @org.jetbrains.annotations.NotNull List<QueryNode<?>> sources() {
      return List.of();
    }

    @Override
    public @org.jetbrains.annotations.NotNull SortOrder<T> sortOrder() {
      return sortOrder;
    }

    @Override
    public boolean mayContainDuplicates() {
      return duplicates;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Stream<QueryData<T>> stream() {
      return data.stream();
    }

    @Override
    public String toString() {
      return "TestNode[" + data.size() + " rows]";
    }
  }
}
