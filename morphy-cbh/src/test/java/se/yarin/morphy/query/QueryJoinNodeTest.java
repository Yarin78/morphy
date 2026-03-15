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
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityCountFilter;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.GameHeader;

public class QueryJoinNodeTest {

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

  // --- MergeJoin tests ---

  @Test
  public void mergeJoinMatchesById() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(4, "d")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.inner(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("b", results.get(0).data());
    assertEquals(3, results.get(1).id());
    assertEquals("c", results.get(1).data());
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(join.mayContainDuplicates());
  }

  @Test
  public void mergeJoinEmptyResult() {
    var left = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a"), new QueryData<>(3, "c")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(2, "b"), new QueryData<>(4, "d")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.inner(left, right);
    assertEquals(0, join.stream().toList().size());
  }

  @Test
  public void mergeJoinSemiEmitsOneLeftOnMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(3, "c"),
            new QueryData<>(4, "d")),
        SortOrder.byId(),
        true);

    var join = MergeJoin.semi(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("b", results.get(0).data());
    assertEquals(3, results.get(1).id());
    assertEquals("c", results.get(1).data());
  }

  @Test
  public void mergeJoinAntiEmitsLeftOnNoMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(4, "d")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.anti(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals("a", results.get(0).data());
    assertEquals(5, results.get(1).id());
    assertEquals("e", results.get(1).data());
  }

  @Test
  public void mergeJoinAntiWithNoMatchesEmitsAll() {
    var left = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a"), new QueryData<>(3, "c")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(2, "b"), new QueryData<>(4, "d")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.anti(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals(3, results.get(1).id());
  }

  @Test
  public void mergeJoinInnerWithDuplicatesOnRight() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        true);

    var join = MergeJoin.inner(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("b", results.get(0).data());
    assertEquals(2, results.get(1).id());
    assertEquals("b", results.get(1).data());
    assertTrue(join.mayContainDuplicates());
  }

  @Test
  public void mergeJoinInnerWithDuplicatesOnLeft() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b", "L1"),
            new QueryData<>(2, "b", "L2"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.inner(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(3, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("L1", results.get(0).extra(String.class));
    assertEquals(2, results.get(1).id());
    assertEquals("L2", results.get(1).extra(String.class));
    assertEquals(3, results.get(2).id());
    assertTrue(join.mayContainDuplicates());
  }

  @Test
  public void mergeJoinInnerWithDuplicatesOnBothSides() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b", "L1"),
            new QueryData<>(2, "b", "L2")),
        SortOrder.byId(),
        true);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b", "R1"),
            new QueryData<>(2, "b", "R2"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);

    var join = MergeJoin.inner(left, right);
    List<QueryData<String>> results = join.stream().toList();
    // 2 left × 2 right = 4 results (cartesian product for key 2)
    assertEquals(4, results.size());
    // L1 × R1, L1 × R2, L2 × R1, L2 × R2
    assertEquals(2, results.get(0).id());
    assertEquals("L1", results.get(0).extra(String.class));
    assertEquals(2, results.get(1).id());
    assertEquals("L1", results.get(1).extra(String.class));
    assertEquals(2, results.get(2).id());
    assertEquals("L2", results.get(2).extra(String.class));
    assertEquals(2, results.get(3).id());
    assertEquals("L2", results.get(3).extra(String.class));
  }

  @Test
  public void mergeJoinSemiWithDuplicatesOnLeft() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b", "L1"),
            new QueryData<>(2, "b", "L2"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(4, "d")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.semi(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("L1", results.get(0).extra(String.class));
    assertEquals(2, results.get(1).id());
    assertEquals("L2", results.get(1).extra(String.class));
  }

  @Test
  public void mergeJoinSemiWithDuplicatesOnBothSides() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b", "L1"),
            new QueryData<>(2, "b", "L2"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(2, "b"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        true);

    var join = MergeJoin.semi(left, right);
    List<QueryData<String>> results = join.stream().toList();
    // Both left rows with key 2 emitted once each, key 3 has no match
    assertEquals(2, results.size());
    assertEquals("L1", results.get(0).extra(String.class));
    assertEquals("L2", results.get(1).extra(String.class));
  }

  @Test
  public void mergeJoinAntiWithDuplicatesOnLeft() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a", "L1"),
            new QueryData<>(1, "a", "L2"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        true);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        false);

    var join = MergeJoin.anti(left, right);
    List<QueryData<String>> results = join.stream().toList();
    // Key 1 has no match (both rows emitted), key 2 has match (skipped), key 3 no match (emitted)
    assertEquals(3, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals("L1", results.get(0).extra(String.class));
    assertEquals(1, results.get(1).id());
    assertEquals("L2", results.get(1).extra(String.class));
    assertEquals(3, results.get(2).id());
    assertEquals("c", results.get(2).data());
  }

  @Test
  public void mergeJoinAntiWithDuplicatesOnRight() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(2, "b")),
        SortOrder.byId(),
        true);

    var join = MergeJoin.anti(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals("a", results.get(0).data());
    assertEquals("c", results.get(1).data());
  }

  // --- HashJoin tests ---

  @Test
  public void hashJoinMatchesById() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);

    var join = HashJoin.inner(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals(5, results.get(1).id());
    assertFalse(join.mayContainDuplicates());
  }

  @Test
  public void hashJoinPreservesLeftSortOrder() {
    var left = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")),
        SortOrder.none(),
        false);

    var join = HashJoin.inner(left, right);
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  @Test
  public void hashJoinSemiEmitsLeftOnMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);

    var join = HashJoin.semi(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(3, results.get(0).id());
    assertEquals("c", results.get(0).data());
    assertEquals(5, results.get(1).id());
    assertEquals("e", results.get(1).data());
  }

  @Test
  public void hashJoinAntiEmitsLeftOnNoMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "a"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(2, "b"),
            new QueryData<>(3, "c"),
            new QueryData<>(5, "e")),
        SortOrder.byId(),
        false);

    var join = HashJoin.anti(left, right);
    List<QueryData<String>> results = join.stream().toList();
    assertEquals(1, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals("a", results.get(0).data());
  }

  // --- LoopJoin tests ---

  @Test
  public void loopJoinInnerWithManualRight() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "game1"),
            new QueryData<>(2, "game2")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "inner-1"),
            new QueryData<>(2, "inner-2")),
        SortOrder.byId(),
        false);

    var join = LoopJoin.inner(left, right);

    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    // merger() prefers left data
    assertEquals("game1", results.get(0).data());
    assertEquals("game2", results.get(1).data());
    assertTrue(join.sortOrder().isNone());
    assertTrue(join.mayContainDuplicates());
  }

  @Test
  public void loopJoinSemiEmitsLeftOnMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "game1"),
            new QueryData<>(2, "game2"),
            new QueryData<>(3, "game3")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(2, "match")),
        SortOrder.byId(),
        false);

    var join = LoopJoin.semi(left, right);

    List<QueryData<String>> results = join.stream().toList();
    assertEquals(1, results.size());
    assertEquals(2, results.get(0).id());
    assertEquals("game2", results.get(0).data());
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
    assertFalse(join.mayContainDuplicates());
  }

  @Test
  public void loopJoinAntiEmitsLeftOnNoMatch() {
    var left = ManualQueryNode.verified(
        List.of(
            new QueryData<>(1, "game1"),
            new QueryData<>(2, "game2"),
            new QueryData<>(3, "game3")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(2, "match")),
        SortOrder.byId(),
        false);

    var join = LoopJoin.anti(left, right);

    List<QueryData<String>> results = join.stream().toList();
    assertEquals(2, results.size());
    assertEquals(1, results.get(0).id());
    assertEquals(3, results.get(1).id());
    assertTrue(join.sortOrder().isSameOrStronger(SortOrder.byId()));
  }

  // --- Join debugString tests ---

  @Test
  public void debugStringMergeJoinShowsBothBranches() {
    var left = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")),
        SortOrder.byId(),
        false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")),
        SortOrder.byId(),
        false);
    var join = MergeJoin.inner(left, right);

    String debug = join.debugString();
    assertTrue(debug.contains("MergeJoin"));
    assertTrue(debug.contains("Manual"));
    String[] lines = debug.split("\n");
    assertTrue(lines.length >= 3);
  }

  @Test
  public void debugStringForComplexQueryPlan() {
    var left = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")), SortOrder.byId(), false);
    var right = ManualQueryNode.verified(
        List.of(new QueryData<>(1, "a")), SortOrder.byId(), false);
    var join = MergeJoin.inner(left, right);
    var sort = new Sort<>(join, SortOrder.byId());
    var limit = new Limit<>(sort, 10);

    String debug = limit.debugString();
    assertTrue(debug.contains("Limit[10]"));
    assertTrue(debug.contains("Sort"));
    assertTrue(debug.contains("MergeJoin"));
    String[] lines = debug.split("\n");
    assertTrue(lines.length >= 4);
  }

  // --- Join integration tests ---

  @Test
  public void mergeJoinWithTableScansIntegration() {
    // Two scans with different ranges that overlap on games 20-29
    var left = TableScan.gameHeaders(txn, 10, 30, null);
    var right = TableScan.gameHeaders(txn, 20, 40, null);
    var join = MergeJoin.inner(left, right);

    List<QueryData<GameHeader>> results = join.stream().toList();
    assertEquals(10, results.size());
    assertEquals(20, results.get(0).id());
    assertEquals(29, results.get(9).id());
  }

  @Test
  public void gamesByPlayerAndTournamentViaMergeJoin() {
    // Find games where player 10 played in tournament 0
    Player player = db.getPlayer(10);
    Tournament tournament = db.tournamentIndex().get(0);

    var playerScan = new GameEntityIndexScan(txn, EntityType.PLAYER);
    var tournamentScan = new GameEntityIndexScan(txn, EntityType.TOURNAMENT);

    var gamesByPlayer = ManualQueryNode.verified(
        playerScan.streamRange(player.id(), player.id() + 1).toList(),
        SortOrder.byId(), false);
    var gamesByTournament = ManualQueryNode.verified(
        tournamentScan.streamRange(tournament.id(), tournament.id() + 1).toList(),
        SortOrder.byId(), false);

    var intersection = MergeJoin.inner(gamesByPlayer, gamesByTournament);

    List<QueryData<Void>> results = intersection.stream().toList();
    // Intersection should be <= min of both sets
    assertTrue(results.size() <= Math.min(player.count(), tournament.count()));
    for (var qd : results) {
      GameHeader gh = db.gameHeaderIndex().getGameHeader(qd.id());
      assertNotNull(gh);
      assertTrue(gh.whitePlayerId() == player.id() || gh.blackPlayerId() == player.id());
      assertEquals(tournament.id(), gh.tournamentId());
    }
  }

  @Test
  public void gameScanWithFilterAndEntityLookupViaHashJoin() {
    // Find all games, then hash-join with game IDs of prolific players (>= 50 games)
    var gameScan = TableScan.gameHeaders(txn);

    var prolificPlayers =
        new EntityIndexScan<>(
            txn.playerTransaction(), SortOrder.none(),
            new EntityCountFilter<>(EntityType.PLAYER, 50, Integer.MAX_VALUE),
            false);

    GameEntityIndex gei = db.gameEntityIndex(EntityType.PLAYER);
    assertNotNull(gei);

    // Collect game IDs for prolific players via flatMap on the entity index
    var gameIdsByProlificPlayers = ManualQueryNode.<Void>verified(
        prolificPlayers.stream()
            .flatMap(qd ->
                gei.stream(qd.id(), EntityType.PLAYER, false)
                    .map(gameId -> new QueryData<Void>(gameId)))
            .toList(),
        SortOrder.none(), true);

    var sorted = new Sort<>(gameIdsByProlificPlayers, SortOrder.byId());
    var distinct = new Distinct<>(sorted);

    var hashJoin = HashJoin.<GameHeader, Void>semi(
        gameScan, distinct, qd -> qd.id(), QueryData::id);

    List<QueryData<GameHeader>> results = hashJoin.stream().toList();
    assertTrue(!results.isEmpty());
    assertTrue(results.size() <= db.count());
    assertFalse(hashJoin.mayContainDuplicates());
  }

  @Test
  public void entityQueryWithGameSubquery() {
    // Find players who played in tournament 0
    Tournament tournament = db.tournamentIndex().get(0);

    // Step 1: Find game IDs in the tournament
    var tournamentScan = new GameEntityIndexScan(txn, EntityType.TOURNAMENT);
    var gamesInTournament = ManualQueryNode.verified(
        tournamentScan.streamRange(tournament.id(), tournament.id() + 1).toList(),
        SortOrder.byId(), false);

    // Step 2: Extract player IDs from game headers via flatMap
    var playerIds = ManualQueryNode.verified(
        gamesInTournament.stream()
            .flatMap(qd -> {
              GameHeader gh = db.gameHeaderIndex().getGameHeader(qd.id());
              return Stream.of(
                  new QueryData<Void>(gh.whitePlayerId()),
                  new QueryData<Void>(gh.blackPlayerId()));
            })
            .toList(),
        SortOrder.none(), true);

    // Step 3: Sort + Distinct to get unique player IDs
    var sorted = new Sort<>(playerIds, SortOrder.byId());
    var distinct = new Distinct<>(sorted);

    List<QueryData<Void>> results = distinct.stream().toList();
    // Tournament should have at least 2 players
    assertTrue(results.size() >= 2);
    // Should not exceed 2 * number of games in tournament
    assertTrue(results.size() <= 2 * tournament.count());
    assertFalse(distinct.mayContainDuplicates());

    for (var qd : results) {
      Player player = txn.playerTransaction().get(qd.id());
      assertNotNull(player);
      assertNotNull(player.lastName());
    }
  }

  @Test
  public void hashJoinGamesByWhitePlayerName() {
    // Find all games where the white player's last name starts with "Kasparov"
    var gameScan = TableScan.gameHeaders(txn);
    var playerScan = TableScan.entities(txn.playerTransaction(),
        player -> player.lastName().startsWith("Kasparov"));

    var join = HashJoin.<GameHeader, Player>semi(
        gameScan,
        playerScan,
        qd -> qd.data().whitePlayerId(),
        QueryData::id);

    List<QueryData<GameHeader>> results = join.stream().toList();
    assertTrue(!results.isEmpty());
    assertTrue(results.size() < db.count());
    for (var qd : results) {
      GameHeader gh = qd.data();
      assertNotNull(gh);
      Player whitePlayer = db.playerIndex().get(gh.whitePlayerId());
      assertTrue(whitePlayer.lastName().startsWith("Kasparov"));
    }
  }

  @Test
  public void mergeJoinTwoEntityIndexScansAndFilter() {
    // Find games involving both player 10 and player 11
    Player player10 = db.getPlayer(10);
    Player player11 = db.getPlayer(11);

    var scan = new GameEntityIndexScan(txn, EntityType.PLAYER);
    var gamesByP10 = ManualQueryNode.verified(
        scan.streamRange(player10.id(), player10.id() + 1).toList(),
        SortOrder.byId(), false);
    var gamesByP11 = ManualQueryNode.verified(
        scan.streamRange(player11.id(), player11.id() + 1).toList(),
        SortOrder.byId(), false);

    var intersection = MergeJoin.inner(gamesByP10, gamesByP11);

    List<QueryData<Void>> results = intersection.stream().toList();
    // Every result must involve both players
    for (var qd : results) {
      GameHeader gh = db.gameHeaderIndex().getGameHeader(qd.id());
      assertNotNull(gh);
      boolean hasP10 = gh.whitePlayerId() == player10.id() || gh.blackPlayerId() == player10.id();
      boolean hasP11 = gh.whitePlayerId() == player11.id() || gh.blackPlayerId() == player11.id();
      assertTrue(hasP10 && hasP11);
    }
  }
}
