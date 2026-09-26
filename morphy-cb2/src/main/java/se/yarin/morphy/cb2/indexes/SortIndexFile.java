package se.yarin.morphy.cb2.indexes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.storage.ByteStore;

/**
 * The {@code .2lcd} file: a sort order for each kind of entity, as an AVL tree per index. See
 * format/v2/5-indexes.md.
 *
 * <p>The file is a row of 4096-byte pages: a header, a catalog of up to 32 indexes, and node and
 * directory pages. A node's number is the id of its entity; node {@code n} is at slot {@code n mod
 * 102} of node page {@code n / 102}, found through the index's levels of directory pages. The
 * header and catalog are big-endian, the other pages little-endian.
 */
public final class SortIndexFile implements AutoCloseable {

  public static final int PAGE_SIZE = 4096;
  public static final int NODE_SIZE = 40;
  public static final int NODES_PER_PAGE = PAGE_SIZE / NODE_SIZE;
  public static final int PAGES_PER_DIRECTORY = PAGE_SIZE / 4;
  private static final int CATALOG_PAGE = 1;
  private static final int CATALOG_ENTRY_SIZE = 128;
  private static final int CATALOG_ENTRIES = PAGE_SIZE / CATALOG_ENTRY_SIZE;

  /**
   * One node of a tree.
   *
   * @param id the entity id, which is also the node's number
   * @param left the left child, -1 if none
   * @param right the right child, -1 if none
   * @param parent the parent, -1 for the root
   * @param key the first bytes of the entity's sort fields, compared unsigned; 0 if none
   * @param balance the height of the right subtree minus that of the left
   * @param slot the node's slot in its page, or -1
   */
  public record Node(long id, long left, long right, long parent, long key, int balance, int slot) {
    Node withLinks(long left, long right, long parent, int balance) {
      return new Node(id, left, right, parent, key, balance, slot);
    }
  }

  /** The indexes ChessBase creates in a new database, in catalog order. */
  public enum StandardIndex {
    PLAYERS("Default Spieler", EntityType.PLAYER, 1),
    TOURNAMENTS("Default Turnier", EntityType.TOURNAMENT, 2),
    SOURCES("Default Quelle", EntityType.SOURCE, 3),
    ANNOTATORS("Default Kommentator", EntityType.PLAYER, 4),
    TEAMS("Default Mannschaften", EntityType.TEAM, 5),
    GAME_TAGS("Default Partietitle", EntityType.GAME_TAG, 6),
    TEXT_TITLES("Texttitel", EntityType.GAME_TAG, 6),
    ANALYSIS_TITLES("Analysen", EntityType.GAME_TAG, 6);

    private final String defaultName;
    private final EntityType type;
    private final int number;

    StandardIndex(String defaultName, EntityType type, int number) {
      this.defaultName = defaultName;
      this.type = type;
      this.number = number;
    }

    public @NotNull String defaultName() {
      return defaultName;
    }

    public @NotNull EntityType type() {
      return type;
    }
  }

  private final @NotNull ByteStore store;
  private final @NotNull String name;
  private final List<Index> indexes = new ArrayList<>();
  private int pageCount;

  public SortIndexFile(@NotNull ByteStore store, @NotNull String name) {
    this.store = store;
    this.name = name;
    if (store.size() < 2 * PAGE_SIZE || store.size() % PAGE_SIZE != 0) {
      throw new InvalidDataException(name + " doesn't hold whole pages");
    }
    ByteBuffer header = store.read(0, 12).order(ByteOrder.BIG_ENDIAN);
    this.pageCount = header.getInt(4);
    if (header.getInt(8) != PAGE_SIZE || (long) pageCount * PAGE_SIZE != store.size()) {
      throw new InvalidDataException("Unexpected page size or count in " + name);
    }
    ByteBuffer catalog = store.read((long) CATALOG_PAGE * PAGE_SIZE, PAGE_SIZE);
    catalog.order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < CATALOG_ENTRIES; i++) {
      int at = i * CATALOG_ENTRY_SIZE;
      int nameLength = catalog.getShort(at + 0x1a) & 0xFFFF;
      if (nameLength == 0) {
        continue;
      }
      byte[] indexName = new byte[nameLength];
      catalog.get(at + 0x1c, indexName);
      indexes.add(
          new Index(
              i,
              new String(indexName, StandardCharsets.ISO_8859_1),
              EntityType.of((catalog.get(at + 0x16) & 0xFF) - 1),
              catalog.getShort(at),
              catalog.getInt(at + 2),
              catalog.getLong(at + 6),
              catalog.getLong(at + 0x0e)));
    }
  }

  /** Initialises a {@code .2lcd} file with the standard indexes, all empty. */
  public static void create(@NotNull ByteStore store) {
    StandardIndex[] standard = StandardIndex.values();
    int pages = 2 + standard.length;
    store.setSize(0);
    store.setSize((long) pages * PAGE_SIZE);
    ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
    header.putInt(1).putInt(pages).putInt(PAGE_SIZE).flip();
    store.write(0, header);
    ByteBuffer catalog = ByteBuffer.allocate(PAGE_SIZE).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < standard.length; i++) {
      int at = i * CATALOG_ENTRY_SIZE;
      catalog.putShort(at, (short) 0);
      catalog.putInt(at + 2, 2 + i);
      catalog.putLong(at + 6, -1);
      catalog.putLong(at + 0x0e, 0);
      catalog.put(at + 0x16, (byte) (standard[i].type.index() + 1));
      catalog.put(at + 0x17, (byte) 3);
      catalog.put(at + 0x18, (byte) standard[i].number);
      catalog.put(at + 0x19, (byte) 1);
      byte[] indexName = standard[i].defaultName.getBytes(StandardCharsets.ISO_8859_1);
      catalog.putShort(at + 0x1a, (short) indexName.length);
      catalog.put(at + 0x1c, indexName);
    }
    store.write(PAGE_SIZE, catalog);
  }

  public @NotNull ByteStore store() {
    return store;
  }

  /** The indexes, in catalog order. */
  public @NotNull List<Index> indexes() {
    return indexes;
  }

  /**
   * The index at a position of the catalog, which for a database created by ChessBase is the
   * {@link StandardIndex} of that ordinal.
   */
  public @NotNull Index index(@NotNull StandardIndex standard) {
    for (Index index : indexes) {
      if (index.catalogSlot == standard.ordinal()) {
        return index;
      }
    }
    throw new InvalidDataException("The index " + standard.defaultName() + " is missing in " + name);
  }

  private int allocatePage() {
    int page = pageCount++;
    store.setSize((long) pageCount * PAGE_SIZE);
    ByteBuffer count = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(pageCount).flip();
    store.write(4, count);
    return page;
  }

  /** One sort order: an AVL tree over the entities of one type. */
  public final class Index implements Iterable<Node> {
    private final int catalogSlot;
    private final @NotNull String indexName;
    private final @NotNull EntityType type;
    private int depth;
    private int topPage;
    private long root;
    private long count;
    private @Nullable BitSet members;

    private Index(
        int catalogSlot,
        @NotNull String indexName,
        @NotNull EntityType type,
        int depth,
        int topPage,
        long root,
        long count) {
      this.catalogSlot = catalogSlot;
      this.indexName = indexName;
      this.type = type;
      this.depth = depth;
      this.topPage = topPage;
      this.root = root;
      this.count = count;
    }

    public @NotNull String name() {
      return indexName;
    }

    public @NotNull EntityType type() {
      return type;
    }

    /** The number of entities in the index. */
    public long count() {
      return count;
    }

    /** The root node, -1 if the index is empty. */
    public long root() {
      return root;
    }

    /** The number of levels of directory pages above the node pages. */
    public int depth() {
      return depth;
    }

    /** Whether an entity is in the index. */
    public boolean contains(long id) {
      return id >= 0 && members().get((int) id);
    }

    private BitSet members() {
      if (members == null) {
        BitSet set = new BitSet();
        for (Node node : this) {
          set.set((int) node.id());
        }
        members = set;
      }
      return members;
    }

    /** The page holding a node, or -1 if no page has been allocated for it. */
    private int nodePage(long id) {
      long position = id / NODES_PER_PAGE;
      int page = topPage;
      for (int level = depth - 1; level >= 0; level--) {
        long span = pow(PAGES_PER_DIRECTORY, level);
        if (position / span >= PAGES_PER_DIRECTORY) {
          return -1;
        }
        page = directoryEntry(page, (int) (position / span));
        if (page == 0) {
          return -1;
        }
        position %= span;
      }
      return depth == 0 && position != 0 ? -1 : page;
    }

    /** Reads a node. The node's data is only meaningful if the entity is in the index. */
    public @NotNull Node node(long id) {
      int page = nodePage(id);
      if (page < 0) {
        throw new InvalidDataException("Node " + id + " of " + indexName + " has no page");
      }
      ByteBuffer buf =
          store.read((long) page * PAGE_SIZE + (id % NODES_PER_PAGE) * NODE_SIZE, NODE_SIZE);
      return new Node(
          id,
          buf.getLong(0),
          buf.getLong(8),
          buf.getLong(16),
          buf.getLong(24),
          buf.getShort(32),
          buf.getInt(36));
    }

    private void writeNode(Node node) {
      int page = ensureNodePage(node.id());
      ByteBuffer buf = ByteStore.allocate(NODE_SIZE);
      buf.putLong(node.left()).putLong(node.right()).putLong(node.parent()).putLong(node.key());
      buf.putShort((short) node.balance()).putShort((short) 0).putInt(node.slot()).flip();
      store.write((long) page * PAGE_SIZE + (node.id() % NODES_PER_PAGE) * NODE_SIZE, buf);
    }

    /** The page for a node, allocating it and any directory pages leading to it. */
    private int ensureNodePage(long id) {
      long position = id / NODES_PER_PAGE;
      // Add levels of directories until the id is within reach
      while (position >= pow(PAGES_PER_DIRECTORY, depth)) {
        int directory = allocatePage();
        setDirectoryEntry(directory, 0, topPage);
        topPage = directory;
        depth++;
        writeCatalog();
      }
      int page = topPage;
      for (int level = depth - 1; level >= 0; level--) {
        long span = pow(PAGES_PER_DIRECTORY, level);
        int entry = (int) (position / span);
        int next = directoryEntry(page, entry);
        if (next == 0) {
          next = allocatePage();
          setDirectoryEntry(page, entry, next);
        }
        page = next;
        position %= span;
      }
      return page;
    }

    private int directoryEntry(int page, int entry) {
      return store.read((long) page * PAGE_SIZE + 4L * entry, 4).getInt();
    }

    private void setDirectoryEntry(int page, int entry, int value) {
      store.write((long) page * PAGE_SIZE + 4L * entry, ByteStore.allocate(4).putInt(value).flip());
    }

    private void writeCatalog() {
      ByteBuffer buf = ByteBuffer.allocate(0x16).order(ByteOrder.BIG_ENDIAN);
      buf.putShort((short) depth).putInt(topPage).putLong(root).putLong(count).flip();
      store.write((long) CATALOG_PAGE * PAGE_SIZE + (long) catalogSlot * CATALOG_ENTRY_SIZE, buf);
    }

    /** The nodes in sort order. */
    @Override
    public @NotNull Iterator<Node> iterator() {
      return new Iterator<>() {
        private final Deque<Node> stack = new ArrayDeque<>();
        private long next = root;

        @Override
        public boolean hasNext() {
          return !stack.isEmpty() || next != -1;
        }

        @Override
        public Node next() {
          while (next != -1) {
            Node node = node(next);
            stack.push(node);
            next = node.left();
            if (stack.size() > 256) {
              throw new InvalidDataException("The tree of " + indexName + " is too deep");
            }
          }
          if (stack.isEmpty()) {
            throw new NoSuchElementException();
          }
          Node node = stack.pop();
          next = node.right();
          return node;
        }
      };
    }

    /**
     * Finds an entity by comparing a probe against the nodes.
     *
     * @param probe compares the entity sought with the entity of a node: negative if the sought
     *     one comes first
     * @return the id of the node comparing equal, or -1
     */
    public long find(@NotNull NodeProbe probe) {
      long id = root;
      while (id != -1) {
        Node node = node(id);
        int c = probe.compareTo(node);
        if (c == 0) {
          return id;
        }
        id = c < 0 ? node.left() : node.right();
      }
      return -1;
    }

    /**
     * Inserts an entity.
     *
     * @param id the entity
     * @param key its key
     * @param comparator orders two entities by id, with their keys
     * @throws IllegalArgumentException if the entity is already in the index
     */
    public void insert(long id, long key, @NotNull NodeComparator comparator) {
      if (contains(id)) {
        throw new IllegalArgumentException(type + " " + id + " is already in " + indexName);
      }
      Node fresh = new Node(id, -1, -1, -1, key, 0, (int) (id % NODES_PER_PAGE));
      if (root == -1) {
        writeNode(fresh);
        root = id;
      } else {
        long parentId = root;
        Node parent;
        boolean left;
        while (true) {
          parent = node(parentId);
          left = comparator.compare(fresh, parent) < 0;
          long child = left ? parent.left() : parent.right();
          if (child == -1) {
            break;
          }
          parentId = child;
        }
        writeNode(new Node(id, -1, -1, parentId, key, 0, fresh.slot()));
        writeNode(
            left
                ? parent.withLinks(id, parent.right(), parent.parent(), parent.balance())
                : parent.withLinks(parent.left(), id, parent.parent(), parent.balance()));
        rebalanceAfterInsert(id);
      }
      count++;
      members().set((int) id);
      writeCatalog();
    }

    private void rebalanceAfterInsert(long childId) {
      Node child = node(childId);
      while (child.parent() != -1) {
        Node parent = node(child.parent());
        int balance = parent.balance() + (parent.left() == child.id() ? -1 : 1);
        parent = parent.withLinks(parent.left(), parent.right(), parent.parent(), balance);
        writeNode(parent);
        if (balance == 0) {
          return;
        }
        if (balance == -2 || balance == 2) {
          rebalance(parent);
          return;
        }
        child = parent;
      }
    }

    /**
     * Removes an entity.
     *
     * @param id the entity
     * @throws IllegalArgumentException if the entity is not in the index
     */
    public void delete(long id) {
      if (!contains(id)) {
        throw new IllegalArgumentException(type + " " + id + " is not in " + indexName);
      }
      Node node = node(id);
      if (node.left() != -1 && node.right() != -1) {
        // Swap places with the in-order successor, which has no left child
        Node successor = node(node.right());
        while (successor.left() != -1) {
          successor = node(successor.left());
        }
        swap(node, successor);
        node = node(id);
      }
      // The node now has at most one child
      long childId = node.left() != -1 ? node.left() : node.right();
      long parentId = node.parent();
      if (childId != -1) {
        Node child = node(childId);
        writeNode(child.withLinks(child.left(), child.right(), parentId, child.balance()));
      }
      if (parentId == -1) {
        root = childId;
      } else {
        Node parent = node(parentId);
        boolean wasLeft = parent.left() == id;
        parent =
            wasLeft
                ? parent.withLinks(childId, parent.right(), parent.parent(), parent.balance())
                : parent.withLinks(parent.left(), childId, parent.parent(), parent.balance());
        writeNode(parent);
        rebalanceAfterDelete(parentId, wasLeft);
      }
      count--;
      members().clear((int) id);
      writeCatalog();
    }

    private void rebalanceAfterDelete(long parentId, boolean shrankLeft) {
      while (parentId != -1) {
        Node parent = node(parentId);
        int balance = parent.balance() + (shrankLeft ? 1 : -1);
        parent = parent.withLinks(parent.left(), parent.right(), parent.parent(), balance);
        writeNode(parent);
        Node top = parent;
        if (balance == 2 || balance == -2) {
          top = rebalance(parent);
          if (top.balance() != 0) {
            // The subtree kept its height
            return;
          }
        } else if (balance != 0) {
          // The subtree kept its height
          return;
        }
        if (top.parent() == -1) {
          return;
        }
        shrankLeft = node(top.parent()).left() == top.id();
        parentId = top.parent();
      }
    }

    /** Swaps a node's position in the tree with another's, keeping each one's id. */
    private void swap(Node a, Node b) {
      // b is a's in-order successor; it may be a's right child
      long aParent = a.parent(), bParent = b.parent();
      boolean adjacent = bParent == a.id();
      Node newA =
          new Node(
              a.id(),
              b.left(),
              b.right(),
              adjacent ? b.id() : bParent,
              a.key(),
              b.balance(),
              a.slot());
      Node newB =
          new Node(
              b.id(),
              a.left(),
              adjacent ? a.id() : a.right(),
              aParent,
              b.key(),
              a.balance(),
              b.slot());
      writeNode(newA);
      writeNode(newB);
      replaceChild(aParent, a.id(), b.id());
      if (!adjacent) {
        replaceChild(bParent, b.id(), a.id());
        setParent(a.right(), b.id());
      }
      setParent(a.left(), b.id());
      setParent(b.right(), a.id());
      if (aParent == -1) {
        root = b.id();
      }
    }

    private void replaceChild(long parentId, long oldChild, long newChild) {
      if (parentId == -1) {
        return;
      }
      Node p = node(parentId);
      if (p.left() == oldChild) {
        writeNode(p.withLinks(newChild, p.right(), p.parent(), p.balance()));
      } else if (p.right() == oldChild) {
        writeNode(p.withLinks(p.left(), newChild, p.parent(), p.balance()));
      }
    }

    private void setParent(long childId, long parentId) {
      if (childId == -1) {
        return;
      }
      Node c = node(childId);
      writeNode(c.withLinks(c.left(), c.right(), parentId, c.balance()));
    }

    /**
     * Rotates an unbalanced subtree (balance ±2) back into balance.
     *
     * @return the new top of the subtree
     */
    private Node rebalance(Node n) {
      if (n.balance() == 2) {
        Node r = node(n.right());
        if (r.balance() < 0) {
          rotateRight(r);
        }
        return rotateLeft(node(n.id()));
      } else {
        Node l = node(n.left());
        if (l.balance() > 0) {
          rotateLeft(l);
        }
        return rotateRight(node(n.id()));
      }
    }

    /** Rotates left around a node; returns its right child, the new top. */
    private Node rotateLeft(Node x) {
      Node y = node(x.right());
      long b = y.left();
      int xb = x.balance(), yb = y.balance();
      int newXb = xb - 1 - Math.max(yb, 0);
      int newYb = yb - 1 + Math.min(newXb, 0);
      Node newX = x.withLinks(x.left(), b, y.id(), newXb);
      Node newY = y.withLinks(x.id(), y.right(), x.parent(), newYb);
      writeNode(newX);
      writeNode(newY);
      setParent(b, x.id());
      linkToParent(x.parent(), x.id(), y.id());
      return newY;
    }

    /** Rotates right around a node; returns its left child, the new top. */
    private Node rotateRight(Node x) {
      Node y = node(x.left());
      long b = y.right();
      int xb = x.balance(), yb = y.balance();
      int newXb = xb + 1 - Math.min(yb, 0);
      int newYb = yb + 1 + Math.max(newXb, 0);
      Node newX = x.withLinks(b, x.right(), y.id(), newXb);
      Node newY = y.withLinks(y.left(), x.id(), x.parent(), newYb);
      writeNode(newX);
      writeNode(newY);
      setParent(b, x.id());
      linkToParent(x.parent(), x.id(), y.id());
      return newY;
    }

    private void linkToParent(long parentId, long oldChild, long newChild) {
      if (parentId == -1) {
        root = newChild;
      } else {
        replaceChild(parentId, oldChild, newChild);
      }
    }
  }

  /** Compares the entity sought with the entity of a node. */
  @FunctionalInterface
  public interface NodeProbe {
    int compareTo(@NotNull Node node);
  }

  /** Orders two nodes by their entities. */
  @FunctionalInterface
  public interface NodeComparator {
    int compare(@NotNull Node a, @NotNull Node b);
  }

  private static long pow(int base, int exponent) {
    long result = 1;
    for (int i = 0; i < exponent; i++) {
      result *= base;
    }
    return result;
  }

  @Override
  public void close() {
    store.close();
  }
}
