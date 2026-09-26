package se.yarin.morphy.cb2.indexes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.indexes.SortIndexFile.Node;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.cb2.storage.MemoryByteStore;

/** Inserting into and deleting from a sort order keeps it a valid, ordered AVL tree. */
class SortIndexTreeTest {

  @Test
  void randomInsertsAndDeletes() {
    MemoryByteStore store = new MemoryByteStore();
    SortIndexFile.create(store);
    SortIndexFile file = new SortIndexFile(store, "test");
    SortIndexFile.Index index = file.index(StandardIndex.PLAYERS);

    Random random = new Random(1);
    Map<Integer, Entity> players = new HashMap<>();
    SortIndexFile.NodeComparator comparator = EntityOrder.nodeComparator(players::get);
    List<Integer> ids = new ArrayList<>();
    for (int i = 0; i < 3000; i++) {
      // Sparse ids, to reach a second level of directory pages
      int id = i < 2900 ? i * 7 : 110_000 + i;
      players.put(id, Player.of(name(random), name(random)));
      ids.add(id);
    }
    Collections.shuffle(ids, random);
    for (int id : ids) {
      index.insert(id, EntityOrder.key(players.get(id)), comparator);
    }
    assertEquals(2, index.depth());
    verify(index, players, ids.size());

    Collections.shuffle(ids, random);
    List<Integer> removed = ids.subList(0, 2000);
    for (int id : removed) {
      index.delete(id);
    }
    verify(index, players, 1000);

    // Reopening reads the same tree
    SortIndexFile reopened = new SortIndexFile(store, "test");
    verify(reopened.index(StandardIndex.PLAYERS), players, 1000);
  }

  private static String name(Random random) {
    String[] parts = {"Karpov", "Kasparov", "Anand", "Carlsen", "Tal", "Ö", "van", "O'Neil", "de la"};
    return parts[random.nextInt(parts.length)] + (char) ('a' + random.nextInt(26));
  }

  private static void verify(SortIndexFile.Index index, Map<Integer, Entity> players, int expected) {
    List<Node> nodes = new ArrayList<>();
    index.forEach(nodes::add);
    assertEquals(expected, nodes.size());
    assertEquals(expected, index.count());
    for (int i = 1; i < nodes.size(); i++) {
      Node a = nodes.get(i - 1), b = nodes.get(i);
      assertTrue(
          EntityOrder.compare(
                  players.get((int) a.id()), a.key(), a.id(), players.get((int) b.id()), b.key(), b.id())
              < 0);
    }
    height(index, index.root(), -1);
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
}
