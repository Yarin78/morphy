package se.yarin.morphy.query;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityCountFilter;
import se.yarin.morphy.entities.TournamentExtra;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.filters.EcoFilter;

public class QueryNodeTest {

  private Database db;
  private DatabaseReadTransaction txn;

  @Before
  public void setup() {
    db = ResourceLoader.openWorldChDatabase();
    txn = new DatabaseReadTransaction(db);
  }

  @After
  public void teardown() {
    txn.close();
  }

  // --- TableScan tests ---

  @Test
  public void tableScanStreamsAllGames() {
    var scan = TableScan.gameHeaders(txn);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertEquals(db.count(), results.size());
    assertEquals(1, results.get(0).id());
    assertEquals(db.count(), results.get(results.size() - 1).id());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void tableScanWithFilter() {
    // Game 10 is Steinitz from 1886, so white elo should be 0 (no ratings back then)
    var scan = TableScan.gameHeaders(txn, gh -> gh.whiteElo() > 0);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertTrue(results.size() > 0);
    assertTrue(results.size() < db.count());
    for (var qd : results) {
      assertTrue(qd.data().whiteElo() > 0);
    }
  }

  @Test
  public void tableScanSortOrderIsByIdAscending() {
    var scan = TableScan.gameHeaders(txn);
    assertFalse(scan.sortOrder().isNone());
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(scan.mayContainDuplicates());
  }

  @Test
  public void tableScanEmptyRange() {
    var scan = TableScan.gameHeaders(txn, 3, 3, null);
    assertEquals(0, scan.stream().toList().size());
  }

  @Test
  public void tableScanEntities() {
    var scan = TableScan.entities(txn.playerTransaction());
    List<QueryData<Player>> results = scan.stream().toList();
    assertEquals(db.playerIndex().count(), results.size());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void tableScanEntitiesWithFilter() {
    var scan = TableScan.entities(txn.playerTransaction(), p -> p.count() >= 10);
    List<QueryData<Player>> results = scan.stream().toList();
    assertTrue(results.size() >= 1);
    for (var qd : results) {
      assertTrue(qd.data().count() >= 10);
    }
  }

  // --- EntityIndexScan tests ---

  @Test
  public void entityIndexScanStreamsPlayers() {
    var scan =
        new EntityIndexScan<>(
            txn.playerTransaction(),
            SortOrder.none(),
            null,
            false);
    List<QueryData<Player>> results = scan.stream().toList();
    assertEquals(db.playerIndex().count(), results.size());
    assertNotNull(results.get(0).data());
  }

  @Test
  public void entityIndexScanWithRange() {
    // Use a range that covers A-K in the player index
    Player start = Player.ofFullName("A");
    Player end = Player.ofFullName("K");
    var scan =
        new EntityIndexScan<>(
            txn.playerTransaction(),
            SortOrder.none(),
            null,
            false);
    List<QueryData<Player>> results = scan.streamRange(start, end).toList();
    assertTrue(results.size() >= 1);
    // All results should be within range according to entity comparison
    int totalPlayers = db.playerIndex().count();
    assertTrue(results.size() < totalPlayers);
    for (var qd : results) {
      assertNotNull(qd.data());
    }
  }

  @Test
  public void entityIndexScanWithFilter() {
    var scan =
        new EntityIndexScan<>(
            txn.playerTransaction(),
            SortOrder.none(),
            new EntityCountFilter<>(EntityType.PLAYER, 50, Integer.MAX_VALUE),
            false);
    List<QueryData<Player>> results = scan.stream().toList();
    assertTrue(results.size() >= 1);
    for (var qd : results) {
      assertNotNull(qd.data());
      assertTrue(qd.data().count() >= 50);
    }
  }

  // --- GameEntityIndexScan tests ---

  @Test
  public void gameEntityIndexScanSingleEntity() {
    // Player 10 is a known player in the World-ch database
    Player player = db.getPlayer(10);
    assertNotNull(player);

    var scan = new GameEntityIndexScan(txn, EntityType.PLAYER);

    List<QueryData<Void>> results =
        scan.streamRange(player.id(), player.id() + 1).toList();
    assertEquals(player.count(), results.size());
  }

  // --- DataFilter in TableScan tests ---

  @Test
  public void tableScanDataFilterReducesResults() {
    EcoFilter ecoFilter = new EcoFilter("C*");
    var scan = TableScan.gameHeaders(txn, ecoFilter::matches);
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
    var scan = TableScan.gameHeaders(txn);
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
    var source = ManualQueryNode.verified(
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
    var source = ManualQueryNode.verified(
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
    var scan = TableScan.gameHeaders(txn);
    var limit = new Limit<>(scan, 3);
    List<QueryData<GameHeader>> results = limit.stream().toList();
    assertEquals(3, results.size());
    assertTrue(limit.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void limitRejectsZero() {
    var scan = TableScan.gameHeaders(txn);
    new Limit<>(scan, 0);
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
  public void queryDataCombineSameTypePrefersLeftData() {
    var left = new QueryData<>(1, "left");
    var right = new QueryData<>(1, "right");
    var combined = QueryData.combine(left, right, true);
    assertEquals("left", combined.data());
  }

  @Test
  public void queryDataCombineSameTypeFallsBackToRightData() {
    var left = new QueryData<String>(1);
    var right = new QueryData<>(1, "right");
    var combined = QueryData.combine(left, right, true);
    assertEquals("right", combined.data());
  }

  @Test
  public void queryDataCombineDifferentTypeSetsExtra() {
    var left = new QueryData<>(1, "left");
    var right = new QueryData<>(1, 42);
    var combined = QueryData.combine(left, right, false);
    assertEquals("left", combined.data());
    assertEquals(42, (int) combined.extra(Integer.class));
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
    var scan = TableScan.gameHeaders(txn);
    var sort = new Sort<>(scan, SortOrder.byId());
    var limit = new Limit<>(sort, 10);

    String debug = limit.debugString();
    assertTrue(debug.contains("Limit[10]"));
    assertTrue(debug.contains("Sort"));
    assertTrue(debug.contains("TableScan"));
    assertTrue(debug.contains("  Sort"));
    assertTrue(debug.contains("    TableScan"));
  }

  // --- Sort order propagation tests ---

  @Test
  public void sortNodeChangesSortOrder() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    SortField<String> nameSort =
        new SortField<>(
            java.util.Comparator.comparing(QueryData::data),
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
    var scan = TableScan.gameHeaders(txn, 3, db.count() + 1, null);
    var limit = new Limit<>(scan, 2);

    List<QueryData<GameHeader>> results = limit.stream().toList();
    assertEquals(2, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals(4, results.get(1).id());
  }

  // --- Sample query composition tests ---

  @Test
  public void gamesBySinglePlayerViaEntityIndex() {
    // Find all games by player 10 using entity index scan + lookup
    Player player = db.getPlayer(10);

    var scan = new GameEntityIndexScan(txn, EntityType.PLAYER);

    List<QueryData<Void>> gameIds =
        scan.streamRange(player.id(), player.id() + 1).toList();
    assertEquals(player.count(), gameIds.size());
    for (var qd : gameIds) {
      GameHeader gh = db.gameHeaderIndex().getGameHeader(qd.id());
      assertNotNull(gh);
      assertTrue(
          gh.whitePlayerId() == player.id() || gh.blackPlayerId() == player.id());
    }
  }

  @Test
  public void gamesByMultiplePlayersViaSortAndDistinct() {
    // Find all games by player 10 or player 11, union via sort + distinct + lookup
    Player player10 = db.getPlayer(10);
    Player player11 = db.getPlayer(11);

    var scan = new GameEntityIndexScan(txn, EntityType.PLAYER);

    List<QueryData<Void>> combined = Stream.concat(
        scan.streamRange(player10.id(), player10.id() + 1),
        scan.streamRange(player11.id(), player11.id() + 1)).toList();
    var unionSource = ManualQueryNode.verified(combined, SortOrder.none(), true);

    var sorted = new Sort<>(unionSource, SortOrder.byId());
    var distinct = new Distinct<>(sorted);

    List<QueryData<Void>> results = distinct.stream().toList();
    // Union should have at least as many as the larger set, but no more than the sum
    assertTrue(results.size() >= Math.max(player10.count(), player11.count()));
    assertTrue(results.size() <= player10.count() + player11.count());
    assertFalse(distinct.mayContainDuplicates());

    // Verify sorted by id
    for (int i = 1; i < results.size(); i++) {
      assertTrue(results.get(i).id() > results.get(i - 1).id());
    }
  }

  // --- GameHeaderIdIndexScan tests ---

  @Test
  public void gameHeaderIdIndexScanStreamsAll() {
    var scan = new GameHeaderIdIndexScan(txn);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertEquals(db.count(), results.size());
    assertEquals(1, results.get(0).id());
    assertNotNull(results.get(0).data());
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(scan.mayContainDuplicates());
  }

  @Test
  public void gameHeaderIdIndexScanGetByKey() {
    var scan = new GameHeaderIdIndexScan(txn);
    QueryData<GameHeader> result = scan.getByKey(1);
    assertNotNull(result);
    assertEquals(1, result.id());
    assertNotNull(result.data());
  }

  @Test
  public void gameHeaderIdIndexScanGetByKeyOutOfRange() {
    var scan = new GameHeaderIdIndexScan(txn);
    assertNull(scan.getByKey(0));
    assertNull(scan.getByKey(db.count() + 1));
  }

  @Test
  public void gameHeaderIdIndexScanWithFilter() {
    var scan = new GameHeaderIdIndexScan(txn, gh -> gh.whiteElo() > 0);
    List<QueryData<GameHeader>> results = scan.stream().toList();
    assertTrue(results.size() > 0);
    assertTrue(results.size() < db.count());
    for (var qd : results) {
      assertTrue(qd.data().whiteElo() > 0);
    }
  }

  @Test
  public void gameHeaderIdIndexScanGetByKeyWithFilter() {
    // Game 10 is from 1886, no ratings
    var scan = new GameHeaderIdIndexScan(txn, gh -> gh.whiteElo() > 0);
    assertNull(scan.getByKey(10));
  }

  @Test
  public void gameHeaderIdIndexScanStreamRange() {
    var scan = new GameHeaderIdIndexScan(txn);
    List<QueryData<GameHeader>> results = scan.streamRange(3, 6).toList();
    assertEquals(3, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals(5, results.get(2).id());
  }

  // --- ExtendedGameHeaderIdIndexScan tests ---

  @Test
  public void extendedGameHeaderIdIndexScanStreamsAll() {
    var scan = new ExtendedGameHeaderIdIndexScan(txn);
    List<QueryData<ExtendedGameHeader>> results = scan.stream().toList();
    assertTrue(results.size() > 0);
    assertEquals(1, results.get(0).id());
    assertNotNull(results.get(0).data());
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  @Test
  public void extendedGameHeaderIdIndexScanGetByKey() {
    var scan = new ExtendedGameHeaderIdIndexScan(txn);
    QueryData<ExtendedGameHeader> result = scan.getByKey(1);
    assertNotNull(result);
    assertEquals(1, result.id());
    assertNotNull(result.data());
  }

  @Test
  public void extendedGameHeaderIdIndexScanGetByKeyOutOfRange() {
    var scan = new ExtendedGameHeaderIdIndexScan(txn);
    assertNull(scan.getByKey(0));
  }

  // --- TournamentExtraIndexScan tests ---

  @Test
  public void tournamentExtraIndexScanStreamsAll() {
    var scan = new TournamentExtraIndexScan(txn);
    List<QueryData<TournamentExtra>> results = scan.stream().toList();
    assertTrue(results.size() >= 0); // may be empty for some test databases
    assertTrue(scan.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(scan.mayContainDuplicates());
  }

  @Test
  public void tournamentExtraIndexScanGetByKey() {
    var scan = new TournamentExtraIndexScan(txn);
    // Should always return a result (TournamentExtraStorage returns empty for missing entries)
    QueryData<TournamentExtra> result = scan.getByKey(0);
    assertNotNull(result);
    assertEquals(0, result.id());
    assertNotNull(result.data());
  }

  // --- MapNode tests ---

  @Test
  public void mapNodeTransformsData() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "hello"),
            new QueryData<>(2, "world")),
        SortOrder.byId(),
        false);
    var map = new MapNode<>(source, qd -> new QueryData<>(qd.id(), qd.data().length()), false);
    List<QueryData<Integer>> results = map.stream().toList();
    assertEquals(2, results.size());
    assertEquals(Integer.valueOf(5), results.get(0).data());
    assertEquals(Integer.valueOf(5), results.get(1).data());
  }

  @Test
  public void mapNodePreservesSortOrderWhenFlagSet() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    var preserving = new MapNode<>(source, qd -> qd.withData(qd.data().toUpperCase()), true);
    assertTrue(preserving.sortOrder().isSameOrStronger(SortOrder.byId()));

    var notPreserving = new MapNode<>(source, qd -> new QueryData<>(qd.id(), qd.data().length()), false);
    assertTrue(notPreserving.sortOrder().isNone());
  }

  @Test
  public void mapNodePropagatesDuplicates() {
    var sourceWithDups = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        true);
    var map = new MapNode<>(sourceWithDups, qd -> qd.withData(qd.data().toUpperCase()), true);
    assertTrue(map.mayContainDuplicates());
  }

  @Test
  public void mapNodeDebugString() {
    var source = ManualQueryNode.verified(List.of(), SortOrder.none(), false);
    var map = new MapNode<>(source, qd -> qd, true);
    String debug = map.debugString();
    assertTrue(debug.contains("Map["));
    assertTrue(debug.contains("Manual["));
  }

  // --- FilterNode tests ---

  @Test
  public void filterNodeFiltersData() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, 10),
            new QueryData<>(2, 20),
            new QueryData<>(3, 5),
            new QueryData<>(4, 30)),
        SortOrder.byId(),
        false);
    var filter = new FilterNode<>(source, qd -> qd.data() > 10);
    List<QueryData<Integer>> results = filter.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals(4, results.get(1).id());
  }

  @Test
  public void filterNodePreservesSortOrder() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    var filter = new FilterNode<>(source, qd -> true);
    assertTrue(filter.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(filter.mayContainDuplicates());
  }

  @Test
  public void filterNodePreservesDuplicateFlag() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(1, "a")),
        SortOrder.byId(),
        true);
    var filter = new FilterNode<>(source, qd -> true);
    assertTrue(filter.mayContainDuplicates());
  }

  @Test
  public void filterNodeDebugString() {
    var source = ManualQueryNode.verified(List.of(), SortOrder.none(), false);
    var filter = new FilterNode<>(source, qd -> true);
    String debug = filter.debugString();
    assertTrue(debug.contains("Filter"));
    assertTrue(debug.contains("Manual["));
  }

  @Test
  public void filterNodeEmptyResult() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    var filter = new FilterNode<>(source, qd -> false);
    assertEquals(0, filter.stream().toList().size());
  }

  // --- Integration: MapNode + FilterNode composition ---

  @Test
  public void mapThenFilterComposition() {
    var source = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "hello"),
            new QueryData<>(2, "hi"),
            new QueryData<>(3, "greetings")),
        SortOrder.byId(),
        false);
    // Map strings to their lengths, then filter for length > 3
    var map = new MapNode<>(source, qd -> new QueryData<>(qd.id(), qd.data().length()), false);
    var filter = new FilterNode<>(map, qd -> qd.data() > 3);
    List<QueryData<Integer>> results = filter.stream().toList();
    assertEquals(2, results.size());
    assertEquals(1, results.get(0).id()); // "hello" = 5
    assertEquals(3, results.get(1).id()); // "greetings" = 9
  }

  @Test
  public void sortFilterLimitTopNQuery() {
    // Top 5 games by id descending - "latest N games" query
    var scan = TableScan.gameHeaders(txn);
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
}
