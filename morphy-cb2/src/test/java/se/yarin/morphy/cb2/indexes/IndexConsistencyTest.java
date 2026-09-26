package se.yarin.morphy.cb2.indexes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityFile;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.games.AnalysisHeader;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.cb2.indexes.SortIndexFile.Node;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.cb2.storage.ByteStore;

/** The index files of the sample databases agree with the games and entities. */
class IndexConsistencyTest {

  @Test
  void sampleDatabase() {
    check(TestDatabases.wch2());
  }

  @Test
  void scratchDatabases() {
    for (String name : new String[] {"probe", "reveng1"}) {
      File file = new File("../test-databases/scratch/" + name + ".2cbh");
      Assumptions.assumeTrue(file.exists(), "scratch database not present");
      check(file);
    }
  }

  private static void check(File file) {
    try (GameHeaderFile headers =
            new GameHeaderFile(ByteStore.open(file, AccessMode.READ_ONLY), "2cbh");
        EntityFile entities =
            new EntityFile(
                ByteStore.open(TestDatabases.sibling(file, ".2lid"), AccessMode.READ_ONLY), "2lid");
        GameListFile lists =
            new GameListFile(
                ByteStore.open(TestDatabases.sibling(file, ".2lgd"), AccessMode.READ_ONLY), "2lgd");
        SortIndexFile sort =
            new SortIndexFile(
                ByteStore.open(TestDatabases.sibling(file, ".2lcd"), AccessMode.READ_ONLY),
                "2lcd")) {
      Map<Role, Map<Integer, List<Integer>>> expected = expectedLists(headers);
      for (Role role : Role.values()) {
        for (Map.Entry<Integer, List<Integer>> e : expected.get(role).entrySet()) {
          assertEquals(e.getValue(), lists.games(e.getKey(), role), role + " of " + e.getKey());
          assertEquals(e.getValue().size(), lists.count(e.getKey(), role));
        }
      }

      for (StandardIndex standard : StandardIndex.values()) {
        SortIndexFile.Index index = sort.index(standard);
        List<Node> nodes = new ArrayList<>();
        index.forEach(nodes::add);
        assertEquals(index.count(), nodes.size(), standard + " count");
        if (index.root() != -1) {
          height(index, index.root(), -1);
        }
        int wrongKeys = 0, wrongOrder = 0;
        Entity previous = null;
        Node previousNode = null;
        BitSet members = new BitSet();
        for (Node node : nodes) {
          members.set((int) node.id());
          Entity entity = entities.get(index.type(), (int) node.id());
          if (EntityOrder.key(entity) != node.key()) {
            wrongKeys++;
            System.out.printf(
                "%s key of %d %s: %016x, expected %016x%n",
                standard, node.id(), entity, node.key(), EntityOrder.key(entity));
          }
          if (previous != null
              && EntityOrder.compare(
                      previous, previousNode.key(), previousNode.id(), entity, node.key(), node.id())
                  > 0) {
            wrongOrder++;
            System.out.printf("%s order: %s before %s%n", standard, previous, entity);
          }
          previous = entity;
          previousNode = node;
        }
        BitSet expectedMembers = new BitSet();
        Role role = roleOf(standard);
        for (int id : expected.get(role).keySet()) {
          expectedMembers.set(id);
        }
        System.out.printf(
            "%s %s: %d nodes, depth %d, %d wrong keys, %d out of order; members %s%n",
            file.getName(), standard, nodes.size(), index.depth(), wrongKeys, wrongOrder,
            members.equals(expectedMembers) ? "as games" : "differ " + members + " vs " + expectedMembers);
        assertEquals(0, wrongKeys, standard + " keys");
        assertEquals(0, wrongOrder, standard + " order");
        assertEquals(expectedMembers, members, standard + " members");
      }
    }
  }

  static Role roleOf(StandardIndex standard) {
    return switch (standard) {
      case PLAYERS -> Role.PLAYER;
      case TOURNAMENTS -> Role.TOURNAMENT;
      case SOURCES -> Role.SOURCE;
      case ANNOTATORS -> Role.ANNOTATOR;
      case TEAMS -> Role.TEAM;
      case GAME_TAGS -> Role.GAME_TAG;
      case TEXT_TITLES -> Role.TEXT_TITLE;
      case ANALYSIS_TITLES -> Role.ANALYSIS_TITLE;
    };
  }

  /** Checks the AVL invariants below a node, returning its height. */
  private static int height(SortIndexFile.Index index, long id, long parent) {
    if (id == -1) {
      return 0;
    }
    Node node = index.node(id);
    assertEquals(parent, node.parent(), "parent of " + id);
    int left = height(index, node.left(), id), right = height(index, node.right(), id);
    assertEquals(right - left, node.balance(), "balance of " + id);
    return 1 + Math.max(left, right);
  }

  static Map<Role, Map<Integer, List<Integer>>> expectedLists(GameHeaderFile headers) {
    Map<Role, Map<Integer, List<Integer>>> lists = new HashMap<>();
    for (Role role : Role.values()) {
      lists.put(role, new TreeMap<>());
    }
    for (int id = 1; id <= headers.count(); id++) {
      GameRecord record = headers.get(id);
      switch (record) {
        case GameHeader g -> {
          add(lists, Role.PLAYER, g.whiteId(), id);
          add(lists, Role.PLAYER, g.blackId(), id);
          add(lists, Role.TOURNAMENT, g.tournamentId(), id);
          add(lists, Role.SOURCE, g.sourceId(), id);
          add(lists, Role.ANNOTATOR, g.annotatorId(), id);
          add(lists, Role.TEAM, g.whiteTeamId(), id);
          add(lists, Role.TEAM, g.blackTeamId(), id);
          add(lists, Role.GAME_TAG, g.gameTagId(), id);
        }
        case TextHeader t -> {
          add(lists, Role.TOURNAMENT, t.tournamentId(), id);
          add(lists, Role.SOURCE, t.sourceId(), id);
          add(lists, Role.ANNOTATOR, t.annotatorId(), id);
          add(lists, Role.TEXT_TITLE, t.titleId(), id);
        }
        case AnalysisHeader a -> {
          add(lists, Role.SOURCE, a.sourceId(), id);
          add(lists, Role.ANNOTATOR, a.annotatorId(), id);
          add(lists, Role.ANALYSIS_TITLE, a.titleId(), id);
        }
      }
    }
    return lists;
  }

  private static void add(Map<Role, Map<Integer, List<Integer>>> lists, Role role, long entity, int game) {
    if (entity >= 0) {
      lists.get(role).computeIfAbsent((int) entity, k -> new ArrayList<>()).add(game);
    }
  }
}
