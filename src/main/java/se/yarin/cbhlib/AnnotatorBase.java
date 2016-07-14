package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.cbhlib.entities.EntityStorage;
import se.yarin.cbhlib.entities.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AnnotatorBase extends EntityBase<AnnotatorEntity> {
    
    private int SERIALIZED_ANNOTATOR_SIZE = 53;

    /**
     * Creates a new in-memory annotator database that is initially empty.
     */
    public AnnotatorBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private AnnotatorBase(@NonNull EntityStorage<AnnotatorEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory annotator base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory annotator base
     */
    public static AnnotatorBase openInMemory(@NonNull File file) throws IOException {
        return new AnnotatorBase(loadInMemoryStorage(file, new AnnotatorBase()));
    }

    /**
     * Opens a annotator database from disk
     * @param file the annotator databases to open
     * @return the opened annotator database
     * @throws IOException if something went wrong when opening the database
     */
    public static AnnotatorBase open(@NonNull File file) throws IOException {
        return new AnnotatorBase(EntityStorageImpl.open(file, new AnnotatorBase()));
    }

    /**
     * Creates a new annotator database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened annotator database
     * @throws IOException if something went wrong when creating the database
     */
    public static AnnotatorBase create(@NonNull File file) throws IOException {
        return new AnnotatorBase(EntityStorageImpl.create(file, new AnnotatorBase()));
    }

    public ByteBuffer serialize(@NonNull AnnotatorEntity annotator) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_ANNOTATOR_SIZE);
        ByteBufferUtil.putByteString(buf, annotator.getName(), 45);
        ByteBufferUtil.putIntL(buf, annotator.getCount());
        ByteBufferUtil.putIntL(buf, annotator.getFirstGameId());
        return buf;
    }

    public AnnotatorEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        String name = ByteBufferUtil.getFixedSizeByteString(buf, 45);

        AnnotatorEntity annotator = new AnnotatorEntity(entityId, name);
        annotator.setCount(ByteBufferUtil.getIntL(buf));
        annotator.setFirstGameId(ByteBufferUtil.getIntL(buf));

        return annotator;
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_ANNOTATOR_SIZE;
    }
}
