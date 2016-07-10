package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SourceBase extends EntityBase<SourceEntity> {
    private int SERIALIZED_SOURCE_SIZE = 59;

    /**
     * Creates a new in-memory source database that is initially empty.
     */
    public SourceBase() {
        super(new OrderedEntityStorageImpl<>(new InMemoryEntityStorage<>()));
    }

    private SourceBase(@NonNull OrderedEntityStorageImpl<SourceEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory source base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory source base
     */
    public static SourceBase openInMemory(@NonNull File file) throws IOException {
        OrderedEntityStorageImpl<SourceEntity> outputStorage = loadInMemoryStorage(file, new SourceBase());
        return new SourceBase(outputStorage);
    }

    /**
     * Opens a source database from disk
     * @param file the source databases to open
     * @return the opened source database
     * @throws IOException if something went wrong when opening the database
     */
    public static SourceBase open(@NonNull File file) throws IOException {
        FileEntityStorage<SourceEntity> storage = FileEntityStorage.open(file, new SourceBase());
        return new SourceBase(new OrderedEntityStorageImpl<>(storage));
    }

    /**
     * Creates a new source database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened source database
     * @throws IOException if something went wrong when creating the database
     */
    public static SourceBase create(@NonNull File file) throws IOException {
        FileEntityStorage<SourceEntity> storage = FileEntityStorage.create(file, new SourceBase());
        return new SourceBase(new OrderedEntityStorageImpl<>(storage));
    }

    public ByteBuffer serialize(@NonNull SourceEntity source) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SOURCE_SIZE);
        ByteBufferUtil.putByteString(buf, source.getTitle(), 25);
        ByteBufferUtil.putByteString(buf, source.getPublisher(), 16);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.getPublication()));
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.getDate()));
        ByteBufferUtil.putByte(buf, source.getVersion());
        ByteBufferUtil.putByte(buf, source.getQuality().ordinal());
        ByteBufferUtil.putIntL(buf, source.getCount());
        ByteBufferUtil.putIntL(buf, source.getFirstGameId());

        return buf;
    }

    public SourceEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        String title = ByteBufferUtil.getFixedSizeByteString(buf, 25);

        SourceEntity source = new SourceEntity(entityId, title);
        source.setPublisher(ByteBufferUtil.getFixedSizeByteString(buf, 16));
        source.setPublication(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));
        source.setDate(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));
        source.setVersion(ByteBufferUtil.getUnsignedByte(buf));
        source.setQuality(SourceQuality.values()[ByteBufferUtil.getUnsignedByte(buf)]);
        source.setCount(ByteBufferUtil.getIntL(buf));
        source.setFirstGameId(ByteBufferUtil.getIntL(buf));

        return source;
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_SOURCE_SIZE;
    }
}
