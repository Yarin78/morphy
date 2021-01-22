package se.yarin.cbhlib.annotations;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.storage.BlobSizeRetriever;
import se.yarin.cbhlib.storage.BlobStorage;
import se.yarin.cbhlib.storage.FileBlobStorage;
import se.yarin.cbhlib.storage.InMemoryBlobStorage;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.chess.GameMovesModel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class AnnotationBase implements BlobSizeRetriever {
    private static final Logger log = LoggerFactory.getLogger(AnnotationBase.class);

    private final BlobStorage storage;

    /**
     * Creates a new annotation base that is initially empty.
     */
    public AnnotationBase() {
        this.storage = new InMemoryBlobStorage(this);
    }

    private AnnotationBase(@NonNull BlobStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates an in-memory annotation base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory annotation base
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
        return new AnnotationBase(new FileBlobStorage(file, new AnnotationBase()));
    }

    /**
     * Creates a new moves database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened moves database
     * @throws IOException if something went wrong when creating the database
     */
    public static AnnotationBase create(@NonNull File file) throws IOException {
        FileBlobStorage.createEmptyStorage(file);
        return open(file);
    }

    /**
     * Loads an annotation database from file into an in-memory storage.
     * Any writes to the database will not be persisted to disk.
     * @param file the file to populate the in-memory database with
     * @return an open in-memory storage
     */
    protected static BlobStorage loadInMemoryStorage(@NonNull File file) throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.read(buf);
        channel.close();
        buf.flip();
        return new InMemoryBlobStorage(buf, new AnnotationBase());
    }

    @Override
    public int getBlobSize(ByteBuffer buf) {
        int oldPos = buf.position();
        buf.position(oldPos + 10);
        int size = ByteBufferUtil.getIntB(buf);
        buf.position(oldPos);
        return size;
    }

    /**
     * Decorates a game with annotations from the annotation database
     * @param model the game to decorate with annotations
     * @param ofs the offset in the database where the annotation data is stored
     * @throws ChessBaseIOException if there was some IO errors when reading the annotations
     */
    public void getAnnotations(@NonNull GameMovesModel model, long ofs) {
        if (ofs > 0) {
            ByteBuffer blob = storage.readBlob(ofs);
            AnnotationsSerializer.deserializeAnnotations(blob, model);
        }
    }

    /**
     * Stores annotations for a game in the annotation database.
     * @param gameId the id of the game to store annotations for
     * @param ofs the old offset where annotations of this game was stored,
     *            or 0 if no annotations were stored for this game before
     * @param model the game with annotations to store
     * @return The offset where the annotation was stored. 0 if the game contained no annotations.
     * @throws ChessBaseIOException if there was some IO errors when storing the annotations
     */
    public long putAnnotations(int gameId, long ofs, GameMovesModel model) {
        if (model.countAnnotations() == 0) {
            return 0;
        }
        ByteBuffer buf = AnnotationsSerializer.serializeAnnotations(gameId, model);
        if (ofs > 0) {
            storage.writeBlob(ofs, buf);
            return ofs;
        }
        return storage.writeBlob(buf);
    }

    public int preparePutBlob(long currentAnnotationOffset, long targetAnnotationOffset, GameMovesModel model) {
        if (model.countAnnotations() == 0 || targetAnnotationOffset == 0) {
            return 0;
        }
        ByteBuffer buf = AnnotationsSerializer.serializeAnnotations(0, model);
        int newAnnotationSize = getBlobSize(buf);
        if (currentAnnotationOffset == 0) {
            storage.insert(targetAnnotationOffset, newAnnotationSize);
            return newAnnotationSize;
        } else {
            assert currentAnnotationOffset == targetAnnotationOffset;
            int oldAnnotationSize = getBlobSize(storage.readBlob(currentAnnotationOffset));
            if (newAnnotationSize <= oldAnnotationSize) {
                return 0;
            }
            int delta = newAnnotationSize - oldAnnotationSize;
            storage.insert(currentAnnotationOffset, delta);
            return delta;
        }
    }

    public void close() throws IOException {
        storage.close();
    }

    public FileBlobStorage getStorage() {
        return (FileBlobStorage) storage;
    }
}
