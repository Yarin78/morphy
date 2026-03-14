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
              null,
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

    var scan = new GameEntityIndexScan(gei, EntityType.PLAYER);

    List<QueryData<Void>> results =
        scan.streamRange(player.id(), player.id() + 1).toList();
    assertEquals(player.count(), results.size());
  }

  // --- DataFilter in TableScan tests ---

  @Test
  public void tableScanDataFilterReducesResults() {
    EcoFilter ecoFilter = new EcoFilter("C*");
    var scan = TableScan.gameHeaders(db, ecoFilter::matches);
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
    var scan = TableScan.gameHeaders(db, 3, db.count() + 1, null);
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
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);
    Player player = db.getPlayer(10);

    var scan = new GameEntityIndexScan(gei, EntityType.PLAYER);

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
    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);
    Player player10 = db.getPlayer(10);
    Player player11 = db.getPlayer(11);

    var scan = new GameEntityIndexScan(gei, EntityType.PLAYER);

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
}
