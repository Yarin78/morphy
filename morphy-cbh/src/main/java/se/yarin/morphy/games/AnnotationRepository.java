package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.games.annotations.AnnotationsSerializer;
import se.yarin.morphy.storage.BlobSizeRetriever;
import se.yarin.morphy.storage.BlobStorage;
import se.yarin.morphy.storage.FileBlobStorage;
import se.yarin.morphy.storage.InMemoryBlobStorage;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class AnnotationRepository implements BlobSizeRetriever {
    private static final Logger log = LoggerFactory.getLogger(AnnotationRepository.class);

    private final @NotNull BlobStorage storage;

    /**
     * Creates a new in-memory annotation repository that is initially empty.
     */
    public AnnotationRepository() {
        this.storage = new InMemoryBlobStorage(this);
    }

    private AnnotationRepository(@NotNull BlobStorage storage) {
        this.storage = storage;
    }

    private AnnotationRepository(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this.storage = new FileBlobStorage(file, this, openOptions);
    }


    public @NotNull BlobStorage getStorage() { return storage; }

    public static AnnotationRepository create(@NotNull File file) throws IOException {
        return new AnnotationRepository(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    /**
     * Opens an annotation repository from disk for read-write
     * @param file the annotation repository to open
     * @return the opened annotation repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static AnnotationRepository open(@NotNull File file) throws IOException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    /**
     * Opens an annotation repository from disk
     * @param file the annotation repository to open
     * @param mode basic operations mode (typically read-only or read-write)
     * @return the opened annotation repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static AnnotationRepository open(@NotNull File file, @NotNull DatabaseMode mode) throws IOException {
        return mode == DatabaseMode.IN_MEMORY
                ? new AnnotationRepository(loadInMemoryStorage(file))
                : new AnnotationRepository(file, mode.openOptions());
    }

    /**
     * Loads an annotation repository from file into an in-memory storage.
     * Any writes to the repository will not be persisted to disk.
     * @param file the file to populate the in-memory repository with
     * @return an open in-memory repository
     */
    protected static BlobStorage loadInMemoryStorage(@NotNull File file) throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), READ);
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.read(buf);
        channel.close();
        buf.flip();
        return new InMemoryBlobStorage(buf, new AnnotationRepository());
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
     * Decorates a game with annotations from the annotation repository
     * @param model the game to decorate with annotations
     * @param ofs the offset in the repository where the annotation data is stored
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when reading the annotations
     */
    public void getAnnotations(@NotNull GameMovesModel model, long ofs) {
        if (ofs > 0) {
            ByteBuffer blob = storage.getBlob(ofs);
            AnnotationsSerializer.deserializeAnnotations(blob, model);
        }
    }

    /**
     * Gets the serialized bytes that make up the annotations of the game.
     * @param ofs the offset in the repository where the annotations data are stored
     * @return a read only byte buffer containing the annotations
     * @throws IllegalArgumentException if ofs is not set
     */
    public ByteBuffer getAnnotationsBlob(long ofs) {
        if (ofs <= 0) {
            throw new IllegalArgumentException("There are no annotations in this game");
        }
        return storage.getBlob(ofs).asReadOnlyBuffer();
    }

    /**
     * Gets the size of the annotation blob that make up the annotations of the game.
     * @param ofs the offset in the repository where the annotations data are stored
     * @return the size of the blob
     * @throws IllegalArgumentException if ofs is not set
     */
    public int getAnnotationsBlobSize(long ofs) {
        if (ofs <= 0) {
            throw new IllegalArgumentException("There are no annotations in this game");
        }
        return storage.getBlobSize(ofs);
    }

    /**
     * Stores annotations for a game in the annotation repository.
     * @param gameId the id of the game to store annotations for
     * @param ofs the old offset where annotations of this game was stored,
     *            or 0 if no annotations were stored for this game before
     * @param model the game with annotations to store
     * @return The offset where the annotation was stored. 0 if the game contained no annotations.
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the annotations
     */
    public long putAnnotations(int gameId, long ofs, GameMovesModel model) {
        if (model.countAnnotations() == 0) {
            return 0;
        }
        ByteBuffer buf = AnnotationsSerializer.serializeAnnotations(gameId, model);
        return putAnnotationsBlob(ofs, buf);
    }

    /**
     * Stores annotations for a game in the annotation repository.
     * @param ofs the old offset where annotations of this game was stored,
     *            or 0 if no annotations were stored for this game before
     * @param blob the serialized annotations to store
     * @return The offset where the annotation was stored. 0 if the game contained no annotations.
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the annotations
     */
    public long putAnnotationsBlob(long ofs, ByteBuffer blob) {
        if (ofs > 0) {
            storage.putBlob(ofs, blob);
            return ofs;
        }
        return storage.appendBlob(blob);
    }

    /**
     * Removes an annotation blob
     * @param offset the offset of the blob; if 0, nothing is done
     * @return the size of the removed blob
     */
    public int removeAnnotationsBlob(long offset) {
        if (offset > 0) {
            return storage.removeBlob(offset);
        }
        return 0;
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
            int oldAnnotationSize = getBlobSize(storage.getBlob(currentAnnotationOffset));
            if (newAnnotationSize <= oldAnnotationSize) {
                return 0;
            }
            int delta = newAnnotationSize - oldAnnotationSize;
            storage.insert(currentAnnotationOffset, delta);
            return delta;
        }
    }

    public void insert(long offset, int delta) {
        storage.insert(offset, delta);
    }

    public void close() throws IOException {
        storage.close();
    }

}
