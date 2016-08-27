package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class MovesBase implements BlobSizeRetriever {
    private static final Logger log = LoggerFactory.getLogger(MovesBase.class);

    private final DynamicBlobStorage storage;

    /**
     * Creates a new moves base that is initially empty.
     */
    public MovesBase() {
        this.storage = new InMemoryDynamicBlobStorage(this);
    }

    private MovesBase(@NonNull DynamicBlobStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates an in-memory moves base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory moves base
     */
    public static MovesBase openInMemory(@NonNull File file) throws IOException {
        return new MovesBase(loadInMemoryStorage(file));
    }

    /**
     * Opens a moves database from disk
     * @param file the moves databases to open
     * @return the opened moves database
     * @throws IOException if something went wrong when opening the database
     */
    public static MovesBase open(@NonNull File file) throws IOException {
        return new MovesBase(new FileDynamicBlobStorage(file, new MovesBase()));
    }

    /**
     * Creates a new moves database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened moves database
     * @throws IOException if something went wrong when creating the database
     */
    public static MovesBase create(@NonNull File file) throws IOException {
        FileDynamicBlobStorage.createEmptyStorage(file);
        return open(file);
    }

    /**
     * Loads an moves database from file into an in-memory storage.
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
        return new InMemoryDynamicBlobStorage(buf, new MovesBase());
    }

    @Override
    public int getBlobSize(ByteBuffer buf) {
        int size = ByteBufferUtil.getIntB(buf) & 0xFFFFFF;
        buf.position(buf.position() - 4);
        return size;
    }

    /**
     * Gets the moves of a game from the moves database
     * @param ofs the offset in the database where the game moves data is stored
     * @return a model of the game
     * @throws IOException if there was some IO errors when reading the moves
     */
    public GameMovesModel getMoves(int ofs)
            throws IOException, ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer blob = storage.getBlob(ofs);
        return MovesSerializer.parseMoveData(blob);
    }

    /**
     * Puts the moves of a game into the moves database
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the database
     * @param model the game to store
     * @return The offset where the game moves was stored
     * @throws IOException if there was some IO errors when storing the moves
     */
    public int putMoves(int ofs, GameMovesModel model) throws IOException {
        ByteBuffer buf = MovesSerializer.serializeMoves(model);
        if (ofs > 0) {
            storage.forcePutBlob(ofs, buf);
            return ofs;
        }
        return storage.addBlob(buf);
    }

    int preparePutBlob(int ofs, GameMovesModel model) throws IOException {
        ByteBuffer buf = MovesSerializer.serializeMoves(model);
        int oldGameSize = getBlobSize(storage.getBlob(ofs));
        int newGameSize = getBlobSize(buf);
        if (newGameSize <= oldGameSize) {
            return 0;
        }
        int delta = newGameSize - oldGameSize;
        storage.insert(ofs, delta);
        return delta;
    }

    public void close() throws IOException {
        storage.close();
    }
}
