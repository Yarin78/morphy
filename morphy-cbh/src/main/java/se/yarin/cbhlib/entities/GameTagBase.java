package se.yarin.cbhlib.entities;

import lombok.NonNull;
import se.yarin.cbhlib.storage.TreePath;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

public class GameTagBase extends EntityBase<GameTagEntity> {
    private static final int SERIALIZED_GAME_TAG_SIZE = 1608;

    /**
     * Creates a new in-memory game tag database that is initially empty.
     */
    public GameTagBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private GameTagBase(@NonNull EntityStorage<GameTagEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory game tag base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory game tag base
     */
    public static GameTagBase openInMemory(@NonNull File file) throws IOException {
        if (!file.exists()) {
            return new GameTagBase();
        }
        return new GameTagBase(loadInMemoryStorage(file, new GameTagBase()));
    }

    /**
     * Opens a game tag database from disk
     * @param file the game tag databases to open
     * @return the opened game tag database
     * @throws IOException if something went wrong when opening the database
     */
    public static GameTagBase open(@NonNull File file) throws IOException {
        return new GameTagBase(EntityStorageImpl.open(file, new GameTagBase()));
    }

    /**
     * Creates a new game tag database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened game tag database
     * @throws IOException if something went wrong when creating the database
     */
    public static GameTagBase create(@NonNull File file) throws IOException {
        return create(file, false);
    }

    /**
     * Creates a new game tag database on disk, optionally keeping everything in memory until the base is closed.
     * This is much more performant if adding a lot of entities immediately after creating the database.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @param createOnClose if true, the base is not persisted to disk until it's closed
     * @return the opened game tag database
     * @throws IOException if something went wrong when creating the database
     */
    public static GameTagBase create(@NonNull File file, boolean createOnClose) throws IOException {
        if (!createOnClose) {
            return new GameTagBase(EntityStorageImpl.create(file, new GameTagBase()));
        } else {
            if (file.exists()) throw new IllegalArgumentException("The file " + file + " already exists");
            GameTagBase gameTagBase = new GameTagBase();
            gameTagBase.addOnCloseHandler(closingBase -> closingBase.duplicate(file));
            return gameTagBase;
        }
    }

    /**
     * Creates a clone of this game tag database on disk
     * @param targetFile the file of the new game tag database to create
     * @return the opened, cloned, game tag database
     * @throws IOException if something went wrong went cloning the database
     */
    @Override
    public GameTagBase duplicate(@NonNull File targetFile) throws IOException {
        return new GameTagBase(getStorage().duplicate(targetFile, new GameTagBase()));
    }

    /**
     * Searches for game tags using a case sensitive prefix search.
     * @param title a prefix of the game tag
     * @return a stream of matching game tags
     */
    public Stream<GameTagEntity> prefixSearch(@NonNull String title) {
        GameTagEntity startKey = new GameTagEntity(title);
        GameTagEntity endKey = new GameTagEntity(title + "zzz");

        TreePath<GameTagEntity> start = getStorage().lowerBound(startKey);
        TreePath<GameTagEntity> end = getStorage().upperBound(endKey);
        return getStorage().streamOrderedAscending(start, end);
    }

    public ByteBuffer serialize(@NonNull GameTagEntity gameTag) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_GAME_TAG_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getEnglishTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getGermanTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getFrenchTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getSpanishTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getItalianTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getDutchTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getSlovenianTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.getResTitle(), 200);
        ByteBufferUtil.putIntL(buf, gameTag.getCount());
        ByteBufferUtil.putIntL(buf, gameTag.getFirstGameId());

        return buf;
    }

    public GameTagEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        return GameTagEntity.builder()
                .id(entityId)
                .englishTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .germanTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .frenchTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .spanishTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .italianTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .dutchTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .slovenianTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .resTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .count(ByteBufferUtil.getIntL(buf))
                .firstGameId(ByteBufferUtil.getIntL(buf))
                .build();
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_GAME_TAG_SIZE;
    }
}
