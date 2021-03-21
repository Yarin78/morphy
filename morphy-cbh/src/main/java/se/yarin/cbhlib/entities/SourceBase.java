package se.yarin.cbhlib.entities;

import lombok.NonNull;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SourceBase extends EntityBase<SourceEntity> {
    private static final int SERIALIZED_SOURCE_SIZE = 59;

    /**
     * Creates a new in-memory source database that is initially empty.
     */
    public SourceBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private SourceBase(@NonNull EntityStorage<SourceEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory source base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory source base
     */
    public static SourceBase openInMemory(@NonNull File file) throws IOException {
        return new SourceBase(loadInMemoryStorage(file, new SourceBase()));
    }

    /**
     * Opens a source database from disk
     * @param file the source databases to open
     * @return the opened source database
     * @throws IOException if something went wrong when opening the database
     */
    public static SourceBase open(@NonNull File file) throws IOException {
        return new SourceBase(EntityStorageImpl.open(file, new SourceBase()));
    }

    /**
     * Creates a new source database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened source database
     * @throws IOException if something went wrong when creating the database
     */
    public static SourceBase create(@NonNull File file) throws IOException {
        return create(file, false);
    }

    /**
     * Creates a new source database on disk, optionally keeping everything in memory until the base is closed.
     * This is much more performant if adding a lot of entities immediately after creating the database.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @param createOnClose if true, the base is not persisted to disk until it's closed
     * @return the opened source database
     * @throws IOException if something went wrong when creating the database
     */
    public static SourceBase create(@NonNull File file, boolean createOnClose) throws IOException {
        if (!createOnClose) {
            return new SourceBase(EntityStorageImpl.create(file, new SourceBase()));
        } else {
            if (file.exists()) throw new IllegalArgumentException("The file " + file + " already exists");
            SourceBase sourceBase = new SourceBase();
            sourceBase.addOnCloseHandler(closingBase -> closingBase.duplicate(file));
            return sourceBase;
        }
    }

    /**
     * Creates a clone of this source database on disk
     * @param targetFile the file of the new source database to create
     * @return the opened, cloned, source database
     * @throws IOException if something went wrong went cloning the database
     */
    @Override
    public SourceBase duplicate(@NonNull File targetFile) throws IOException {
        return new SourceBase(getStorage().duplicate(targetFile, new SourceBase()));
    }

    public ByteBuffer serialize(@NonNull SourceEntity source) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SOURCE_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, source.getTitle(), 25);
        ByteBufferUtil.putFixedSizeByteString(buf, source.getPublisher(), 16);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.getPublication()));
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.getDate()));
        ByteBufferUtil.putByte(buf, source.getVersion());
        ByteBufferUtil.putByte(buf, source.getQuality().ordinal());
        ByteBufferUtil.putIntL(buf, source.getCount());
        ByteBufferUtil.putIntL(buf, source.getFirstGameId());

        return buf;
    }

    public SourceEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        return SourceEntity.builder()
                .id(entityId)
                .title(ByteBufferUtil.getFixedSizeByteString(buf, 25))
                .publisher(ByteBufferUtil.getFixedSizeByteString(buf, 16))
                .publication(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)))
                .date(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)))
                .version(ByteBufferUtil.getUnsignedByte(buf))
                .quality(SourceQuality.values()[ByteBufferUtil.getUnsignedByte(buf)])
                .count(ByteBufferUtil.getIntL(buf))
                .firstGameId(ByteBufferUtil.getIntL(buf))
                .build();
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_SOURCE_SIZE;
    }
}
