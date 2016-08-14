package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class AnnotationBase implements BlobSizeRetriever {
    private static final Logger log = LoggerFactory.getLogger(AnnotationBase.class);

    private final DynamicBlobStorage storage;

    private AnnotationBase() {
        this.storage = new InMemoryDynamicBlobStorage(this);
    }

    private AnnotationBase(@NonNull DynamicBlobStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates an in-memory annotation base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory annotaiton base
     */
    public static AnnotationBase openInMemory(@NonNull File file) throws IOException {
        return new AnnotationBase(loadInMemoryStorage(file));
    }

    /**
     * Opens a moves database from disk
     * @param file the moves databases to open
     * @return the opened moves database
     * @throws IOException if something went wrong when opening the database
     */
    public static AnnotationBase open(@NonNull File file) throws IOException {
        return new AnnotationBase(new FileDynamicBlobStorage(file, new AnnotationBase()));
    }

    /**
     * Creates a new moves database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened moves database
     * @throws IOException if something went wrong when creating the database
     */
    public static AnnotationBase create(@NonNull File file) throws IOException {
        FileDynamicBlobStorage.createEmptyStorage(file);
        return open(file);
    }

    /**
     * Loads an annotation database from file into an in-memory storage.
     * Any writes to the database will not be persisted to disk.
     * @param file the file to populate the in-memory database with
     * @return an open in-memory storage
     */
    protected static DynamicBlobStorage loadInMemoryStorage(@NonNull File file) throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.read(buf);
        channel.close();
        buf.flip();
        return new InMemoryDynamicBlobStorage(buf, new AnnotationBase());
    }

    @Override
    public int getBlobSize(ByteBuffer buf) {
        int size = ByteBufferUtil.getIntB(buf) & 0xFFFFFF;
        buf.position(buf.position() - 4);
        return size;
    }

    public Map<Integer, Annotations> getAnnotations(int ofs) throws IOException {
        ByteBuffer blob = storage.getBlob(ofs);
        return AnnotationParser.parseGameAnnotations(blob);
    }

    public void putAnnotations(int ofs, GameMovesModel moves) {
        // TODO: Implement this
        throw new UnsupportedOperationException();
    }
}
