package se.yarin.morphy.boosters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.metrics.*;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.file.StandardOpenOption.*;

public class GameEntityIndex implements MetricsProvider {
  private final @NotNull ItemStorage<IndexHeader, IndexItem> citStorage;
  private final @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage;

  private final @NotNull DatabaseContext context;

  private final Map<EntityType, Integer> citOrder;

  // The order of the entity types in the .cit file
  public static final List<EntityType> PRIMARY_TYPES =
      List.of(
          EntityType.PLAYER,
          EntityType.TOURNAMENT,
          EntityType.TEAM,
          EntityType.SOURCE,
          EntityType.ANNOTATOR);

  // The order of the entity types in the .cit2 file
  public static final List<EntityType> SECONDARY_TYPES = List.of(EntityType.GAME_TAG);

  private final @Nullable MetricsRef<ItemMetrics> tableMetricsRef, blockMetricsRef;

  public DatabaseContext context() {
    return context;
  }

  public Set<EntityType> entityTypes() {
    return citOrder.keySet();
  }

  public GameEntityIndex(@NotNull List<EntityType> entityTypes) {
    this(entityTypes, null);
  }

  public GameEntityIndex(@NotNull List<EntityType> entityTypes, @Nullable DatabaseContext context) {
    this(
        entityTypes,
        new InMemoryItemStorage<>(
            context, "IndexTable", IndexHeader.emptyCIT(), IndexItem.emptyCIT(entityTypes.size())),
        new InMemoryItemStorage<>(
            context, "IndexBlock", IndexBlockHeader.empty(), IndexBlockItem.empty()),
        context);
  }

  public GameEntityIndex(
      @NotNull List<EntityType> entityTypes,
      @NotNull ItemStorage<IndexHeader, IndexItem> citStorage,
      @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage,
      @Nullable DatabaseContext context) {
    this.citOrder = new HashMap<>();
    for (int i = 0; i < entityTypes.size(); i++) {
      this.citOrder.put(entityTypes.get(i), i);
    }
    this.citStorage = citStorage;
    this.cibStorage = cibStorage;
    this.context = context == null ? new DatabaseContext() : context;
    tableMetricsRef = null;
    blockMetricsRef = null;
  }

  protected GameEntityIndex(
      @NotNull File citFile,
      @NotNull File cibFile,
      @NotNull Set<OpenOption> options,
      @Nullable DatabaseContext context)
      throws IOException {
    this.context = context == null ? new DatabaseContext() : context;
    this.citOrder = entityTypeOrder(resolveEntityTypesFromFileName(citFile));

    String suffix = CBUtil.getExtension(citFile).substring(4);

    Instrumentation instrumentation = this.context.instrumentation();
    MetricsRef<ItemMetrics> tableMetricsRef =
        ItemMetrics.register(instrumentation, "IndexTable" + suffix);
    MetricsRef<ItemMetrics> blockMetricsRef =
        ItemMetrics.register(instrumentation, "IndexBlock" + suffix);
    this.citStorage =
        new FileItemStorage<>(
            citFile,
            this.context,
            "IndexTable" + suffix,
            new IndexSerializer(this.citOrder.size(), tableMetricsRef),
            IndexHeader.emptyCIT(),
            options);
    this.cibStorage =
        new FileItemStorage<>(
            cibFile,
            this.context,
            "IndexBlock" + suffix,
            new IndexBlockSerializer(blockMetricsRef),
            IndexBlockHeader.empty(),
            options);

    this.tableMetricsRef = tableMetricsRef;
    this.blockMetricsRef = blockMetricsRef;

    if (options.contains(WRITE)) {
      if (citStorage.getHeader().unknown1() != 0 || citStorage.getHeader().unknown2() != 0) {
        throw new MorphyNotSupportedException(
            String.format(
                "Unknown values in cit header were not 0 (%d, %d), writing not allowed",
                citStorage.getHeader().unknown1(), citStorage.getHeader().unknown2()));
      }
    }
  }

  public static GameEntityIndex create(
      @NotNull File citFile, @NotNull File cibFile, @Nullable DatabaseContext context)
      throws IOException {
    return new GameEntityIndex(citFile, cibFile, Set.of(READ, WRITE, CREATE_NEW), context);
  }

  public static @NotNull GameEntityIndex open(
      @NotNull File citFile, @NotNull File cibFile, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return open(citFile, cibFile, DatabaseMode.READ_WRITE, context);
  }

  public static @NotNull GameEntityIndex open(
      @NotNull File citFile,
      @NotNull File cibFile,
      @NotNull DatabaseMode mode,
      @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    if (mode == DatabaseMode.IN_MEMORY) {
      GameEntityIndex source = open(citFile, cibFile, DatabaseMode.READ_ONLY, context);
      GameEntityIndex target =
          new GameEntityIndex(resolveEntityTypesFromFileName(citFile), context);
      source.copyEntities(target);
      return target;
    }
    return new GameEntityIndex(citFile, cibFile, mode.openOptions(), context);
  }

  private static List<EntityType> resolveEntityTypesFromFileName(@NotNull File citFile) {
    List<EntityType> entityTypes;
    if (CBUtil.getExtension(citFile).equalsIgnoreCase(".cit")) {
      entityTypes = GameEntityIndex.PRIMARY_TYPES;
    } else if (CBUtil.getExtension(citFile).equalsIgnoreCase(".cit2")) {
      entityTypes = GameEntityIndex.SECONDARY_TYPES;
    } else {
      throw new IllegalArgumentException("The cit extension must be either .cit or .cit2");
    }
    return entityTypes;
  }

  private static Map<EntityType, Integer> entityTypeOrder(List<EntityType> entityTypes) {
    HashMap<EntityType, Integer> citOrder = new HashMap<>();
    for (int i = 0; i < entityTypes.size(); i++) {
      citOrder.put(entityTypes.get(i), i);
    }
    return citOrder;
  }

  public void copyEntities(@NotNull GameEntityIndex targetStorage) {
    // Low level copy of all entities from one storage to a new empty storage
    if (!targetStorage.citStorage.isEmpty()
        || !targetStorage.cibStorage.isEmpty()
        || targetStorage.getNumBlocks() != 0) {
      throw new IllegalStateException("The target storage must be empty");
    }

    // TODO: Lot of duplicate code in all copyEntities, put in base class?
    int batchSize = 1000, capacity = citStorage.count(), currentIndex = 0;
    for (int i = 0; i < capacity; i += batchSize) {
      List<IndexItem> items = citStorage.getItems(i, Math.min(i + batchSize, capacity));
      for (IndexItem item : items) {
        targetStorage.citStorage.putItem(currentIndex, item);
        currentIndex += 1;
      }
    }

    capacity = cibStorage.count();
    currentIndex = 0;
    for (int i = 0; i < capacity; i += batchSize) {
      List<IndexBlockItem> items = cibStorage.getItems(i, Math.min(i + batchSize, capacity));
      for (IndexBlockItem item : items) {
        targetStorage.cibStorage.putItem(currentIndex, item);
        currentIndex += 1;
      }
    }

    targetStorage.citStorage.putHeader(citStorage.getHeader());
    targetStorage.cibStorage.putHeader(cibStorage.getHeader());
  }

  /**
   * Gets a list of game ids for a given entity and type
   *
   * @param entityId the id of the entity to get games for
   * @param type the type of entity
   * @param includeDuplicates if true, duplicate games are included multiple times in the output
   * @return a list of game ids in sorted order.
   */
  public @NotNull List<Integer> getGameIds(
      int entityId, @NotNull EntityType type, boolean includeDuplicates) {
    int currentBlock = getHead(entityId, type);
    ArrayList<Integer> gameIds = new ArrayList<>();
    HashSet<Integer> seenBlocks = new HashSet<>(); // avoiding infinite loop in case of bad data
    int lastId = 0;
    while (currentBlock != -1) {
      if (seenBlocks.contains(currentBlock)) {
        throw new MorphyInvalidDataException(
            String.format(
                "GameEntityIndex contains an infinite loop for %s with id %d",
                type.nameSingular(), entityId));
      }
      seenBlocks.add(currentBlock);
      IndexBlockItem block = cibStorage.getItem(currentBlock);
      for (int id : block.gameIds()) {
        if (id > lastId || includeDuplicates) {
          // Remove duplicates
          gameIds.add(id);
        }
        lastId = id;
      }
      currentBlock = block.nextBlockId();
    }
    return gameIds;
  }

  public Iterable<Integer> iterable(int entityId, @NotNull EntityType type) {
    return iterable(entityId, type, false);
  }

  public Iterable<Integer> iterable(
      int entityId, @NotNull EntityType type, boolean includeDuplicates) {
    // TODO: This is currently not exposed in a transaction only, which the counterpart in
    // EntityIndex and GameIndex are. Fix this?
    return () -> new EntityGameIterator(entityId, type, includeDuplicates);
  }

  public @NotNull Stream<Integer> stream(int entityId, @NotNull EntityType type) {
    return stream(entityId, type, false);
  }

  public @NotNull Stream<Integer> stream(
      int entityId, @NotNull EntityType type, boolean includeDuplicates) {
    return StreamSupport.stream(iterable(entityId, type, includeDuplicates).spliterator(), false);
  }

  @Override
  public @NotNull List<MetricsKey> getMetricsKeys() {
    ArrayList<MetricsKey> metricsKeys = new ArrayList<>();
    if (tableMetricsRef != null) {
      metricsKeys.add(tableMetricsRef.metricsKey());
    }
    if (blockMetricsRef != null) {
      metricsKeys.add(blockMetricsRef.metricsKey());
    }
    if (citStorage instanceof MetricsProvider mp) {
      metricsKeys.addAll(mp.getMetricsKeys());
    }
    if (cibStorage instanceof MetricsProvider mp) {
      metricsKeys.addAll(mp.getMetricsKeys());
    }
    return metricsKeys;
  }

  public class EntityGameIterator implements Iterator<Integer> {
    private final @NotNull EntityType type;
    private final int entityId;
    private final boolean includeDuplicates;
    private int nextBlockId;
    private int nextGameId;
    private int batchPos;
    private final HashSet<Integer> seenBlocks =
        new HashSet<>(); // avoiding infinite loop in case of bad data
    private @Nullable List<Integer> batch = new ArrayList<>();

    public EntityGameIterator(int entityId, @NotNull EntityType type, boolean includeDuplicates) {
      this.type = type;
      this.entityId = entityId;
      this.includeDuplicates = includeDuplicates;
      this.nextBlockId = getHead(entityId, type);
      findNextGame();
    }

    private void findNextGame() {
      // After this, either batch is null (end of iteration reached),
      // or nextGameId equals the game after the current one (if includeDuplicates is false)
      while (batch != null) {
        while (batchPos < batch.size()
            && (nextGameId == batch.get(batchPos) && !includeDuplicates)) {
          batchPos += 1;
        }
        if (batchPos == batch.size()) {
          getNextBatch();
        } else {
          nextGameId = batch.get(batchPos++);
          return;
        }
      }
    }

    private void getNextBatch() {
      if (nextBlockId == -1) {
        batch = null;
        return;
      }
      if (seenBlocks.contains(nextBlockId)) {
        throw new MorphyInvalidDataException(
            String.format(
                "GameEntityIndex contains an infinite loop for %s with id %d",
                type.nameSingular(), entityId));
      }
      seenBlocks.add(nextBlockId);
      IndexBlockItem block = cibStorage.getItem(nextBlockId);
      batch = block.gameIds();
      batchPos = 0;
      nextBlockId = block.nextBlockId();
    }

    @Override
    public boolean hasNext() {
      return batch != null;
    }

    @Override
    public Integer next() {
      if (!hasNext()) {
        throw new NoSuchElementException("End of game iteration reached");
      }
      int gameId = nextGameId;
      findNextGame();
      return gameId;
    }
  }

  /**
   * Updates the index for an entity by specifying games where the count has been updated. Game ids
   * not mentioned in gameCount will be not be update.
   *
   * @param entityId the id of the entity to update
   * @param type the type of entity
   * @param gameCount a sorted map from game id to number of occurrences of the entity in that game
   *     (typically 0 and 1, sometimes 2).
   */
  public void updateEntity(
      int entityId, @NotNull EntityType type, @NotNull Map<Integer, Integer> gameCount) {
    // Read the old data and combine with new data in a single, sorted in-memory ArrayList
    ArrayList<Integer> data = new ArrayList<>();

    for (Map.Entry<Integer, Integer> entry : gameCount.entrySet()) {
      for (int i = 0; i < entry.getValue(); i++) {
        data.add(entry.getKey());
      }
    }

    // Check if we can start at the tail block (saves a lot of time if we're only appending new
    // games to the db)
    int oldHeadBlockId = -1, oldTailBlockId = -1;
    if (entityId < getNumEntities()) {
      oldHeadBlockId = getHead(entityId, type);
      oldTailBlockId = getTail(entityId, type);
    }
    int currentReadBlockId = oldHeadBlockId;
    int newHeadBlockId = -1, newTailBlockId = -1;

    if (oldTailBlockId >= 0 && oldHeadBlockId != oldTailBlockId) {
      // Skipping blocks is a special case
      newHeadBlockId = oldHeadBlockId;
      newTailBlockId = oldHeadBlockId;
      IndexBlockItem block = cibStorage.getItem(oldTailBlockId);
      // Need to compare < to ensure at least one game in this block is still left
      if (block.gameIds().get(0) < Collections.min(gameCount.keySet())) {
        currentReadBlockId = oldTailBlockId;
      }
    }

    LinkedList<Integer> oldBlocks = new LinkedList<>();

    while (currentReadBlockId != -1) {
      oldBlocks.add(currentReadBlockId);
      IndexBlockItem block = cibStorage.getItem(currentReadBlockId);
      for (int gameId : block.gameIds()) {
        if (!gameCount.containsKey(gameId)) {
          data.add(gameId);
        }
      }
      currentReadBlockId = block.nextBlockId();
    }

    Collections.sort(data);

    int chunkSize = gamesPerBlock();
    int nextNewBlockId = getNumBlocks();
    int nextDeletedId = cibStorage.getHeader().deletedBlockId();

    for (int cur = 0; cur < data.size(); cur += chunkSize) {
      List<Integer> blockGames = data.subList(cur, Math.min(data.size(), cur + chunkSize));

      int currentBlockId;
      if (!oldBlocks.isEmpty()) {
        currentBlockId = oldBlocks.pollFirst();
      } else if (nextDeletedId > 0) {
        currentBlockId = nextDeletedId;
        nextDeletedId = cibStorage.getItem(nextDeletedId).nextBlockId();
      } else {
        currentBlockId = nextNewBlockId++;
      }

      int nextBlockId = -1;
      if (cur + chunkSize < data.size()) {
        if (!oldBlocks.isEmpty()) {
          nextBlockId = oldBlocks.peekFirst();
        } else if (nextDeletedId > 0) {
          nextBlockId = nextDeletedId;
        } else {
          nextBlockId = nextNewBlockId;
        }
      }

      IndexBlockItem block =
          ImmutableIndexBlockItem.builder()
              .gameIds(Collections.unmodifiableList(blockGames))
              .nextBlockId(nextBlockId)
              .unknown(0)
              .build();
      cibStorage.putItem(currentBlockId, block);

      if (newHeadBlockId < 0) {
        newHeadBlockId = currentBlockId;
      }
      newTailBlockId = currentBlockId;
    }

    updateHeadTail(entityId, type, newHeadBlockId, newTailBlockId);

    while (!oldBlocks.isEmpty()) {
      int blockId = oldBlocks.pollFirst();
      IndexBlockItem block =
          ImmutableIndexBlockItem.builder()
              .gameIds(List.of())
              .nextBlockId(nextDeletedId)
              .unknown(0)
              .build();
      cibStorage.putItem(blockId, block);
      nextDeletedId = blockId;
    }

    cibStorage.putHeader(
        ImmutableIndexBlockHeader.builder()
            .from(cibStorage.getHeader())
            .numBlocks(nextNewBlockId)
            .deletedBlockId(nextDeletedId)
            .build());
  }

  public @NotNull List<Integer> getDeletedBlockIds() {
    ArrayList<Integer> result = new ArrayList<>();
    int current = cibStorage.getHeader().deletedBlockId();
    while (current != 0) {
      result.add(current);
      IndexBlockItem block = cibStorage.getItem(current);
      current = block.nextBlockId();
    }
    return result;
  }

  /**
   * Get the index of all blocks used by an entity type. For checking/testing purposes only
   *
   * @param type the entity type
   * @param count number of entities to check (with index 0 to count - 1)
   * @return a set of blocks used
   * @throws IllegalStateException if the same block is used multiple times, or if head/tail doesn't
   *     match
   */
  public @NotNull Set<Integer> getUsedBlockIds(@NotNull EntityType type, int count) {
    // For checking/testing purposes only
    HashSet<Integer> usedBlocks = new HashSet<>();
    for (int i = 0; i < count; i++) {
      int currentBlock = getHead(i, type);
      int tailBlock = getTail(i, type);
      if (currentBlock < 0) {
        // Entity is logically deleted
        if (tailBlock != -1) {
          throw new IllegalStateException(
              String.format(
                  "%s with id %d has invalid head/tail pointers in game entity index",
                  type.nameSingularCapitalized(), i));
        }
        continue;
      }
      int actualLastBlock = currentBlock;
      while (currentBlock >= 0) {
        if (!usedBlocks.add(currentBlock)) {
          throw new IllegalStateException(
              String.format("Block %d is used more than once in game entity index", currentBlock));
        }
        IndexBlockItem block = cibStorage.getItem(currentBlock);
        actualLastBlock = currentBlock;
        currentBlock = block.nextBlockId();
      }
      if (actualLastBlock != tailBlock) {
        throw new IllegalStateException(
            String.format(
                "%s with id %d has invalid head/tail pointers in game entity index",
                type.nameSingularCapitalized(), i));
      }
    }
    return usedBlocks;
  }

  public int getNumBlocks() {
    return cibStorage.getHeader().numBlocks();
  }

  public int getNumEntities() {
    return citStorage.count();
  }

  public int gamesPerBlock() {
    return (cibStorage.getHeader().itemSize() - 12) / 4;
  }

  public long numBlocksDiskPages() {
    if (cibStorage instanceof FileItemStorage<?, ?> fs) {
      return fs.numPages();
    } else {
      return 0;
    }
  }

  public long numTableDiskPages() {
    if (citStorage instanceof FileItemStorage<?, ?> fs) {
      return fs.numPages();
    } else {
      return 0;
    }
  }

  private int getHead(int entityId, @NotNull EntityType type) {
    Integer order = citOrder.get(type);
    if (order == null) {
      throw new IllegalArgumentException(
          "Entity type " + type.nameSingularCapitalized() + " is not managed by this index");
    }
    return citStorage.getItem(entityId).headTails()[order * 2];
  }

  private int getTail(int entityId, @NotNull EntityType type) {
    Integer order = citOrder.get(type);
    if (order == null) {
      throw new IllegalArgumentException(
          "Entity type " + type.nameSingularCapitalized() + " is not managed by this index");
    }
    return citStorage.getItem(entityId).headTails()[order * 2 + 1];
  }

  private void updateHeadTail(int entityId, EntityType type, int headBlock, int tailBlock) {
    Integer order = citOrder.get(type);
    if (order == null) {
      throw new IllegalArgumentException(
          "Entity type " + type.nameSingularCapitalized() + " is not managed by this index");
    }

    IndexItem item =
        entityId < getNumEntities()
            ? citStorage.getItem(entityId)
            : IndexItem.emptyCIT(citOrder.size());
    int[] newHeadTails = item.headTails().clone();
    newHeadTails[order * 2] = headBlock;
    newHeadTails[order * 2 + 1] = tailBlock;

    IndexItem newItem = ImmutableIndexItem.builder().headTails(newHeadTails).build();

    // We can't write beyond the end of the file, so make sure to fill up with empty index table
    // items
    for (int i = citStorage.count(); i < entityId; i++) {
      citStorage.putItem(i, IndexItem.emptyCIT(citOrder.size()));
    }
    citStorage.putItem(entityId, newItem);
  }

  public void close() {
    citStorage.close();
    cibStorage.close();
  }
}
