package se.yarin.morphy.boosters;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.storage.ItemStorageSerializer;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class GameEventStorage implements ItemStorageSerializer<GameEventStorage.Prolog, GameEvents> {
    private static final Logger log = LoggerFactory.getLogger(GameEventStorage.class);

    private final @NotNull ItemStorage<Prolog, GameEvents> storage;
    private final @NotNull DatabaseContext context;
    private final @NotNull Instrumentation.ItemStats itemStats;

    public GameEventStorage() {
        this(null);
    }

    public GameEventStorage(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(context, "GameEvents", GameEventStorage.Prolog.empty(), null, true), context);
    }

    private GameEventStorage(@NotNull ItemStorage<GameEventStorage.Prolog, GameEvents> storage, @Nullable DatabaseContext context) {
        this.storage = storage;
        this.context = context == null ? new DatabaseContext() : context;
        this.itemStats = this.context.instrumentation().itemStats("GameEvents");
    }

    private GameEventStorage(@NotNull File file, @NotNull Set<OpenOption> options, @Nullable DatabaseContext context) throws IOException {
        if (!CBUtil.extension(file).equals(".cbb")) {
            throw new IllegalArgumentException("The file extension of a GameEvent storage must be .cbb");
        }
        this.context = context == null ? new DatabaseContext() : context;
        this.itemStats = this.context.instrumentation().itemStats("GameEvents");
        this.storage = new FileItemStorage<>(file, this.context, "GameEvents", this, Prolog.empty(), options);

        if (options.contains(WRITE)) {
            if (storage.getHeader().version() != Prolog.DEFAULT_VERSION) {
                throw new MorphyNotSupportedException(String.format("Unsupported version of GameEvent storage (%d != %d)",
                        storage.getHeader().version(), Prolog.DEFAULT_VERSION));
            }
            if (storage.getHeader().serializedItemSize() != Prolog.DEFAULT_SERIALIZED_ITEM_SIZE) {
                // This shouldn't happen because of the version checks above, so this is mostly a sanity check
                // to ensure that same version doesn't have different record sizes.
                throw new MorphyNotSupportedException(String.format("Size of game event record was %d but expected %d; writing not possible.",
                        storage.getHeader().serializedItemSize(), Prolog.DEFAULT_SERIALIZED_ITEM_SIZE));
            }
        }
    }

    public static GameEventStorage create(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return new GameEventStorage(file, Set.of(READ, WRITE, CREATE_NEW), context);
    }

    public static GameEventStorage open(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    public static GameEventStorage open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context) throws IOException {
        if (mode == DatabaseMode.IN_MEMORY) {
            GameEventStorage source = open(file, DatabaseMode.READ_ONLY, context);
            GameEventStorage target = new GameEventStorage(context);
            source.copyGameEvents(target);
            return target;
        }
        return new GameEventStorage(file, mode.openOptions(), context);
    }

    public DatabaseContext context() {
        return context;
    }

    static Prolog peekProlog(@NotNull File file) throws IOException {
        GameEventStorage storage = open(file, DatabaseMode.READ_ONLY, null);
        GameEventStorage.Prolog prolog = storage.storage.getHeader();
        storage.close();
        return prolog;
    }

    public GameEventStorage.Prolog prolog() {
        return storage.getHeader();
    }

    /**
     * Gets the number of games in the storage
     * @return the number of games
     */
    public int count() {
        return storage.getHeader().numGames();
    }

    public void put(int gameId, @NotNull GameEvents gameEvents) {
        storage.putItem(gameId, gameEvents);
        if (gameId > count()) {
            storage.putHeader(Prolog.withCount(gameId));
        }
    }

    public @NotNull GameEvents get(int gameId) {
        return storage.getItem(gameId);
    }

    public void copyGameEvents(@NotNull GameEventStorage target) {
        if (target.count() != 0) {
            // Since this is a low-level copy, things will not work if there already are games in the target storage
            throw new IllegalStateException("Target storage must be empty");
        }
        for (int i = 1; i <= count(); i++) {
            target.storage.putItem(i, storage.getItem(i));
        }
        target.storage.putHeader(storage.getHeader());
    }

    @Value.Immutable
    public static abstract class Prolog {
        public static final int DEFAULT_VERSION = 1;
        public static final int DEFAULT_SERIALIZED_ITEM_SIZE = 52;
        public static final int SERIALIZED_HEADER_SIZE = 52;

        public abstract int numGames();

        public abstract int version();

        public abstract int serializedItemSize();

        public static @NotNull GameEventStorage.Prolog withCount(int numGames) {
            return ImmutableProlog.builder()
                    .numGames(numGames)
                    .version(DEFAULT_VERSION)
                    .serializedItemSize(DEFAULT_SERIALIZED_ITEM_SIZE)
                    .build();
        }

        public static @NotNull GameEventStorage.Prolog empty() {
            return withCount(0);
        }
    }

    @Override
    public int serializedHeaderSize() {
        return Prolog.SERIALIZED_HEADER_SIZE;
    }

    @Override
    public long itemOffset(@NotNull GameEventStorage.Prolog prolog, int index) {
        return Prolog.SERIALIZED_HEADER_SIZE + (long) (index - 1) * prolog.serializedItemSize();
    }

    @Override
    public int itemSize(@NotNull GameEventStorage.Prolog prolog) {
        return prolog.serializedItemSize();
    }

    @Override
    public int headerSize(@NotNull GameEventStorage.Prolog prolog) {
        return Prolog.SERIALIZED_HEADER_SIZE;
    }

    @Override
    public @NotNull GameEventStorage.Prolog deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException {
        ImmutableProlog prolog = ImmutableProlog.builder()
                .numGames(ByteBufferUtil.getIntB(buf))
                .version(ByteBufferUtil.getSignedShortB(buf))
                .serializedItemSize(ByteBufferUtil.getSignedShortB(buf))
                .build();
        for (int i = 0; i < 11; i++) {
            int fillerInt = ByteBufferUtil.getIntB(buf);
            if (fillerInt != 0) {
                log.warn(String.format("Unused prolog bytes in GameEventStorage was not 0 (int %d = %d)", i, fillerInt));
            }
        }
        return prolog;
    }

    @Override
    public @NotNull GameEvents deserializeItem(int id, @NotNull ByteBuffer buf, @NotNull GameEventStorage.Prolog prolog) {
        GameEvents gameEvents = new GameEvents(buf.slice(buf.position(), 52));
        buf.position(buf.position() + prolog.serializedItemSize());
        return gameEvents;
    }

    @Override
    public @NotNull GameEvents emptyItem(int id) {
        return new GameEvents();
    }

    @Override
    public void serializeHeader(@NotNull GameEventStorage.Prolog prolog, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putIntB(buf, prolog.numGames());
        ByteBufferUtil.putShortB(buf, prolog.version());
        ByteBufferUtil.putShortB(buf, prolog.serializedItemSize());
        for (int i = 0; i < 11; i++) {
            ByteBufferUtil.putIntB(buf, 0);
        }
    }

    @Override
    public void serializeItem(@NotNull GameEvents gameEvents, @NotNull ByteBuffer buf, @NotNull GameEventStorage.Prolog prolog) {
        byte[] bytes = gameEvents.getBytes();
        buf.put(bytes);
        for (int i = bytes.length; i < prolog.serializedItemSize(); i++) {
            buf.put((byte) 0);
        }
    }

    public void close() {
        storage.close();
    }
}
