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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameEntityIndex {
    private final @NotNull ItemStorage<IndexHeader, IndexItem> citStorage;
    private final @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage;

    private final @NotNull ItemStorage<IndexHeader, IndexItem> cit2Storage;
    private final @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cib2Storage;

    private final @NotNull DatabaseContext context;

    private static final Map<EntityType, Integer> CIT_OFFSET = Map.of(
            EntityType.PLAYER, 0,
            EntityType.TOURNAMENT, 2,
            EntityType.ANNOTATOR, 8,
            EntityType.SOURCE, 6,
            EntityType.TEAM, 4,
            EntityType.GAME_TAG, 0); // cit2

    public GameEntityIndex() {
        this(null);
    }

    public GameEntityIndex(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(IndexHeader.emptyCIT(), IndexItem.emptyCIT()),
            new InMemoryItemStorage<>(IndexBlockHeader.empty(), IndexBlockItem.empty()),
            new InMemoryItemStorage<>(IndexHeader.emptyCIT2(), IndexItem.emptyCIT2()),
            new InMemoryItemStorage<>(IndexBlockHeader.empty(), IndexBlockItem.empty()),
            context);
    }

    public GameEntityIndex(
            @NotNull ItemStorage<IndexHeader, IndexItem> citStorage,
            @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage,
            @NotNull ItemStorage<IndexHeader, IndexItem> cit2Storage,
            @NotNull ItemStorage<IndexBlockHeader, IndexBlockItem> cib2Storage,
            @Nullable DatabaseContext context) {
        this.citStorage = citStorage;
        this.cibStorage = cibStorage;
        this.cit2Storage = cit2Storage;
        this.cib2Storage = cib2Storage;
        this.context = context == null ? new DatabaseContext() : context;
    }

    public static @NotNull GameEntityIndex open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        File cibFile = CBUtil.fileWithExtension(file, ".cib");
        File cib2File = CBUtil.fileWithExtension(file, ".cib2");
        File citFile = CBUtil.fileWithExtension(file, ".cit");
        File cit2File = CBUtil.fileWithExtension(file, ".cit2");
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
                citFile, context, new IndexSerializer(instrumentation.serializationStats("IndexTable")), IndexHeader.emptyCIT(), mode.openOptions());
        FileItemStorage<IndexBlockHeader, IndexBlockItem> cibStorage = new FileItemStorage<>(
                cibFile, context, new IndexBlockSerializer(instrumentation.serializationStats("IndexBlock")), IndexBlockHeader.empty(), mode.openOptions());
        FileItemStorage<IndexHeader, IndexItem> cit2Storage = new FileItemStorage<>(
                cit2File, context, new IndexSerializer(instrumentation.serializationStats("IndexTable2")), IndexHeader.emptyCIT2(), mode.openOptions());
        FileItemStorage<IndexBlockHeader, IndexBlockItem> cib2Storage = new FileItemStorage<>(
                cib2File, context, new IndexBlockSerializer(instrumentation.serializationStats("IndexBlock2")), IndexBlockHeader.empty(), mode.openOptions());

        // TODO: Validate storage
        return new GameEntityIndex(citStorage, cibStorage, cit2Storage, cib2Storage, context);
    }

    public static GameEntityIndex create(File file, DatabaseContext context) {
        // TODO
        return null;
    }

    public @NotNull List<Integer> getGameIds(int entityId, @NotNull EntityType type) {
        int currentBlock = getHead(entityId, type);
        ArrayList<Integer> gameIds = new ArrayList<>();
        int lastId = 0;
        while (currentBlock != -1) {
            IndexBlockItem block = getBlock(currentBlock, type);
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

    private int getHead(int entityId, @NotNull EntityType type) {
        IndexItem indexItem = (type != EntityType.GAME_TAG ? citStorage : cit2Storage).getItem(entityId);
        return indexItem.headTails()[CIT_OFFSET.get(type)];
    }

    private int getTail(int entityId, @NotNull EntityType type) {
        IndexItem indexItem = (type != EntityType.GAME_TAG ? citStorage : cit2Storage).getItem(entityId);
        return indexItem.headTails()[CIT_OFFSET.get(type) + 1];
    }

    private @NotNull IndexBlockItem getBlock(int blockId, @NotNull EntityType type) {
        return (type != EntityType.GAME_TAG ? cibStorage : cib2Storage).getItem(blockId);
    }

    public void close() {
        citStorage.close();
        cibStorage.close();
        cit2Storage.close();
        cib2Storage.close();
    }
}
