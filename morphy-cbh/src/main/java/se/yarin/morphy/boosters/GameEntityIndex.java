package se.yarin.morphy.boosters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GameEntityIndex {
    private final @NotNull ItemStorage<IndexHeader, IndexItem> citStorage;
    private final @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage;

    private final @NotNull DatabaseContext context;

    private final Map<EntityType, Integer> citOrder;

    // The order of the entity types in the .cit file
    public static final List<EntityType> PRIMARY_TYPES = List.of(
            EntityType.PLAYER,
            EntityType.TOURNAMENT,
            EntityType.TEAM,
            EntityType.SOURCE,
            EntityType.ANNOTATOR);

    // The order of the entity types in the .cit2 file
    public static final List<EntityType> SECONDARY_TYPES = List.of(
            EntityType.GAME_TAG);

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
            new InMemoryItemStorage<>(IndexHeader.emptyCIT(), IndexItem.emptyCIT()),
            new InMemoryItemStorage<>(IndexBlockHeader.empty(), IndexBlockItem.empty()),
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

    public static @NotNull GameEntityIndex open(@NotNull File citFile, @NotNull File cibFile, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {

        List<EntityType> entityTypes = resolveEntityTypesFromFileName(citFile);
        String suffix = CBUtil.getExtension(citFile).substring(4);

        /*
        if (mode == DatabaseMode.IN_MEMORY) {
            GameEntityIndex source = open(file, DatabaseMode.READ_ONLY, context);
            GameEntityIndex target = new GameEntityIndex(context);
            source.copyEntities(target);
            return target;
        }

         */

        if (context == null) {
            context = new DatabaseContext();
        }
        Instrumentation instrumentation = context.instrumentation();
        FileItemStorage<IndexHeader, IndexItem> citStorage = new FileItemStorage<>(
                citFile, context, new IndexSerializer(instrumentation.serializationStats("IndexTable" + suffix)), IndexHeader.emptyCIT(), mode.openOptions());
        FileItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage = new FileItemStorage<>(
                cibFile, context, new IndexBlockSerializer(instrumentation.serializationStats("IndexBlock" + suffix)), IndexBlockHeader.empty(), mode.openOptions());

        // TODO: Validate storage
        return new GameEntityIndex(entityTypes, citStorage, cibStorage, context);
    }

    public static GameEntityIndex create(@NotNull File citFile, @NotNull File cibFile, DatabaseContext context) {
        // TODO
        return null;
    }

    public @NotNull List<Integer> getGameIds(int entityId, @NotNull EntityType type) {
        int currentBlock = getHead(entityId, type);
        ArrayList<Integer> gameIds = new ArrayList<>();
        int lastId = 0;
        while (currentBlock != -1) {
            IndexBlockItem block = cibStorage.getItem(currentBlock);
            for (int id : block.gameIds()) {
                if (id > lastId) {
                    // Remove duplicates
                    gameIds.add(id);
                }
                lastId = id;
            }
            currentBlock = block.nextBlockId();
        }
        return gameIds;
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
     * Get the index of all blocks used by an entity type.
     * For checking/testing purposes only
     * @param type the entity type
     * @param count number of entities to check (with index 0 to count - 1)
     * @return a set of blocks used
     * @throws IllegalStateException if the same block is used multiple times, or if head/tail doesn't match
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
                    throw new IllegalStateException(String.format("%s with id %d has invalid head/tail pointers in game entity index",
                            type.nameSingularCapitalized(), i));
                }
                continue;
            }
            int actualLastBlock = currentBlock;
            while (currentBlock >= 0) {
                if (!usedBlocks.add(currentBlock)) {
                    throw new IllegalStateException(String.format("Block %d is used more than once in game entity index", currentBlock));
                }
                IndexBlockItem block = cibStorage.getItem(currentBlock);
                actualLastBlock = currentBlock;
                currentBlock = block.nextBlockId();
            }
            if (actualLastBlock != tailBlock) {
                throw new IllegalStateException(String.format("%s with id %d has invalid head/tail pointers in game entity index",
                        type.nameSingularCapitalized(), i));
            }
        }
        return usedBlocks;
    }

    public int getNumBlocks() {
        return cibStorage.getHeader().numBlocks();
    }

    private int getHead(int entityId, @NotNull EntityType type) {
        Integer order = citOrder.get(type);
        if (order == null) {
            throw new IllegalArgumentException("Entity type " + type.nameSingularCapitalized() + " is not managed by this index");
        }
        return citStorage.getItem(entityId).headTails()[order * 2];
    }

    private int getTail(int entityId, @NotNull EntityType type) {
        Integer order = citOrder.get(type);
        if (order == null) {
            throw new IllegalArgumentException("Entity type " + type.nameSingularCapitalized() + " is not managed by this index");
        }
        return citStorage.getItem(entityId).headTails()[order * 2 + 1];
    }


    public void close() {
        citStorage.close();
        cibStorage.close();
    }
}
