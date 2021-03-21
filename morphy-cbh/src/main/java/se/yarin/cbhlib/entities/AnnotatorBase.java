package se.yarin.cbhlib.entities;

import lombok.NonNull;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AnnotatorBase extends EntityBase<AnnotatorEntity> {
    
    private static final int SERIALIZED_ANNOTATOR_SIZE = 53;

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
        return create(file, false);
    }

    /**
     * Creates a new annotator database on disk, optionally keeping everything in memory until the base is closed.
     * This is much more performant if adding a lot of entities immediately after creating the database.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @param createOnClose if true, the base is not persisted to disk until it's closed
     * @return the opened annotator database
     * @throws IOException if something went wrong when creating the database
     */
    public static AnnotatorBase create(@NonNull File file, boolean createOnClose) throws IOException {
        if (!createOnClose) {
            return new AnnotatorBase(EntityStorageImpl.create(file, new AnnotatorBase()));
        } else {
            if (file.exists()) throw new IllegalArgumentException("The file " + file + " already exists");
            AnnotatorBase annotatorBase = new AnnotatorBase();
            annotatorBase.addOnCloseHandler(closingBase -> closingBase.duplicate(file));
            return annotatorBase;
        }
    }

    /**
     * Creates a clone of this annotator database on disk
     * @param targetFile the file of the new annotator database to create
     * @return the opened, cloned, annotator database
     * @throws IOException if something went wrong went cloning the database
     */
    @Override
    public AnnotatorBase duplicate(@NonNull File targetFile) throws IOException {
        return new AnnotatorBase(getStorage().duplicate(targetFile, new AnnotatorBase()));
    }

    public ByteBuffer serialize(@NonNull AnnotatorEntity annotator) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_ANNOTATOR_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, annotator.getName(), 45);
        ByteBufferUtil.putIntL(buf, annotator.getCount());
        ByteBufferUtil.putIntL(buf, annotator.getFirstGameId());
        return buf;
    }

    public AnnotatorEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        return AnnotatorEntity.builder()
                .id(entityId)
                .name(ByteBufferUtil.getFixedSizeByteString(buf, 45))
                .count(ByteBufferUtil.getIntL(buf))
                .firstGameId(ByteBufferUtil.getIntL(buf))
                .build();
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_ANNOTATOR_SIZE;
    }
}
