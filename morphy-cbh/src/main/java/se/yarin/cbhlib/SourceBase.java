package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.cbhlib.entities.transaction.EntityStorage;
import se.yarin.cbhlib.entities.transaction.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SourceBase extends EntityBase<SourceEntity> {
    private int SERIALIZED_SOURCE_SIZE = 59;

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
        return new SourceBase(EntityStorageImpl.create(file, new SourceBase()));
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
