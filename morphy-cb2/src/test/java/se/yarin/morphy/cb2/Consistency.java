package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import se.yarin.morphy.cb2.annotations.AnnotationBlockCodec;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityFile;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.games.AnalysisHeader;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.indexes.EntityOrder;
import se.yarin.morphy.cb2.indexes.GameListFile;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.cb2.indexes.SortIndexFile;
import se.yarin.morphy.cb2.indexes.SortIndexFile.Node;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.cb2.moves.GuidingTextCodec;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.RecordFile;

/** Checks that the files of a v2 database agree with each other. */
public final class Consistency {
  private Consistency() {}

  /**
   * Checks a database: the move and annotation records lie back to back and decode, the entities
   * the records refer to exist, the game lists hold exactly the records referring to each entity,
   * and each sort order is an ordered AVL tree of exactly the entities with games in its role.
   */
  public static void check(Database2Cbh db) {
    checkRecords(db);
    Map<Role, Map<Integer, List<Integer>>> expected = expectedLists(db.gameHeaderFile());
    checkEntities(db, expected);
    checkLists(db.gameListFile(), expected);
    checkSortOrders(db, expected);
  }

  private static void checkRecords(Database2Cbh db) {
    GameHeaderFile headers = db.gameHeaderFile();
    TreeMap<Long, Integer> moves = new TreeMap<>(), annotations = new TreeMap<>();
    for (int id = 1; id <= headers.count(); id++) {
      GameRecord record = headers.get(id);
      moves.put(record.movesOffset(), id);
      RecordFile.Record data = db.moveFile().read(record.movesOffset());
      if (record instanceof TextHeader) {
        assertEquals(RecordFile.TAG_TEXT, data.tag(), "tag of text " + id);
        GuidingTextCodec.decode(data.content());
      } else {
        MoveStreamCodec.decode(data.tag(), data.content());
        annotations.put(record.annotationOffset(), id);
        RecordFile.Record a = db.annotationFile().read(record.annotationOffset());
        assertEquals(RecordFile.TAG_ANNOTATIONS, a.tag(), "annotation tag of " + id);
        assertNotNull(AnnotationBlockCodec.read(a.content()), "annotations of " + id);
      }
    }
    backToBack(db.moveFile(), moves, "moves");
    backToBack(db.annotationFile(), annotations, "annotations");
  }

  private static void backToBack(RecordFile file, TreeMap<Long, Integer> offsets, String what) {
    long expected = RecordFile.HEADER_SIZE;
    for (Map.Entry<Long, Integer> e : offsets.entrySet()) {
      assertEquals(expected, e.getKey(), what + " record of game " + e.getValue() + " doesn't follow the one before");
      expected += file.recordLength(e.getKey());
    }
    assertEquals(expected, file.size(), what + " file doesn't end after its last record");
  }

  private static void checkEntities(Database2Cbh db, Map<Role, Map<Integer, List<Integer>>> expected) {
    EntityFile entities = db.entityFile();
    for (Map.Entry<Role, Map<Integer, List<Integer>>> e : expected.entrySet()) {
      EntityType type = typeOf(e.getKey());
      for (int id : e.getValue().keySet()) {
        assertNotNull(entities.get(type, id), type + " " + id + " is referred to but doesn't exist");
      }
    }
  }

  private static void checkLists(GameListFile lists, Map<Role, Map<Integer, List<Integer>>> expected) {
    for (Role role : Role.values()) {
      for (Map.Entry<Integer, List<Integer>> e : expected.get(role).entrySet()) {
        assertEquals(e.getValue(), lists.games(e.getKey(), role), role + " of " + e.getKey());
        assertEquals(e.getValue().size(), lists.count(e.getKey(), role), role + " count of " + e.getKey());
      }
      for (int id = 0; id < lists.recordCount(); id++) {
        if (!expected.get(role).containsKey(id)) {
          assertEquals(0, lists.count(id, role), role + " of " + id + " should be empty");
        }
      }
    }
  }

  private static void checkSortOrders(Database2Cbh db, Map<Role, Map<Integer, List<Integer>>> expected) {
    SortIndexFile sort = db.sortIndexFile();
    EntityFile entities = db.entityFile();
    for (StandardIndex standard : StandardIndex.values()) {
      SortIndexFile.Index index = sort.index(standard);
      List<Node> nodes = new ArrayList<>();
      index.forEach(nodes::add);
      assertEquals(index.count(), nodes.size(), standard + " count");
      if (index.root() != -1) {
        height(index, index.root(), -1);
      }
      BitSet members = new BitSet();
      for (int i = 0; i < nodes.size(); i++) {
        Node node = nodes.get(i);
        members.set((int) node.id());
        Entity entity = entities.get(index.type(), (int) node.id());
        assertNotNull(entity, standard + " holds a missing entity " + node.id());
        assertEquals(EntityOrder.key(entity), node.key(), standard + " key of " + node.id());
        if (i > 0) {
          Node p = nodes.get(i - 1);
          Entity previous = entities.get(index.type(), (int) p.id());
          assertTrue(
              EntityOrder.compare(previous, p.key(), p.id(), entity, node.key(), node.id()) < 0,
              standard + ": " + previous + " should come after " + entity);
        }
      }
      BitSet expectedMembers = new BitSet();
      for (int id : expected.get(roleOf(standard)).keySet()) {
        expectedMembers.set(id);
      }
      assertEquals(expectedMembers, members, standard + " members");
    }
  }

  private static int height(SortIndexFile.Index index, long id, long parent) {
    if (id == -1) {
      return 0;
    }
    Node node = index.node(id);
    assertEquals(parent, node.parent(), "parent of " + id);
    int left = height(index, node.left(), id), right = height(index, node.right(), id);
    assertEquals(right - left, node.balance(), "balance of " + id);
    assertTrue(Math.abs(right - left) <= 1, "unbalanced at " + id);
    return 1 + Math.max(left, right);
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

  static EntityType typeOf(Role role) {
    return switch (role) {
      case PLAYER, ANNOTATOR -> EntityType.PLAYER;
      case TOURNAMENT -> EntityType.TOURNAMENT;
      case SOURCE -> EntityType.SOURCE;
      case TEAM -> EntityType.TEAM;
      case GAME_TAG, TEXT_TITLE, ANALYSIS_TITLE -> EntityType.GAME_TAG;
    };
  }

  /** The records referring to each entity in each role, from the game records. */
  public static Map<Role, Map<Integer, List<Integer>>> expectedLists(GameHeaderFile headers) {
    Map<Role, Map<Integer, List<Integer>>> lists = new HashMap<>();
    for (Role role : Role.values()) {
      lists.put(role, new TreeMap<>());
    }
    for (int id = 1; id <= headers.count(); id++) {
      switch (headers.get(id)) {
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
