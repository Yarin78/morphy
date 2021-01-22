package se.yarin.cbhlib.moves;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.exceptions.ChessBaseUnsupportedException;
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

public class MovesBase implements BlobSizeRetriever {
    private static final Logger log = LoggerFactory.getLogger(MovesBase.class);

    private final BlobStorage storage;
    private int overrideEncodingMode = -1; // The encoding mode to use when writing games, -1 = default based on type

    private boolean validateDecodedMoves = true; // If true, validate all moves when decoding

    public void setEncodingMode(int encodingMode) {
        this.overrideEncodingMode = encodingMode;
    }

    public void setValidateDecodedMoves(boolean validateDecodedMoves) { this.validateDecodedMoves = validateDecodedMoves; }

    /**
     * Creates a new moves base that is initially empty.
     */
    public MovesBase() {
        this.storage = new InMemoryBlobStorage(this);
    }

    private MovesBase(@NonNull BlobStorage storage) {
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
        return new MovesBase(new FileBlobStorage(file, new MovesBase()));
    }

    /**
     * Creates a new moves database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened moves database
     * @throws IOException if something went wrong when creating the database
     */
    public static MovesBase create(@NonNull File file) throws IOException {
        FileBlobStorage.createEmptyStorage(file);
        return open(file);
    }

    /**
     * Loads an moves database from file into an in-memory storage.
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
        return new InMemoryBlobStorage(buf, new MovesBase());
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
     * @param gameId the id of the game to load; only used in logging statements
     * @return a model of the game
     * @throws ChessBaseIOException if there was some IO errors when reading the moves
     */
    public GameMovesModel getMoves(long ofs, int gameId)
            throws ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer blob = storage.readBlob(ofs);
        return MovesSerializer.deserializeMoves(blob, validateDecodedMoves, gameId);
    }

    /**
     * Puts the moves of a game into the moves database
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the database
     * @param model the game to store
     * @return The offset where the game moves was stored
     * @throws ChessBaseIOException if there was some IO errors when storing the moves
     */
    public long putMoves(long ofs, GameMovesModel model) {
        ByteBuffer buf = MovesSerializer.serializeMoves(model, resolveEncodingMode(model));
        if (ofs > 0) {
            storage.writeBlob(ofs, buf);
            return ofs;
        }
        return storage.writeBlob(buf);
    }

    public int preparePutBlob(long ofs, GameMovesModel model) {
        ByteBuffer buf = MovesSerializer.serializeMoves(model, resolveEncodingMode(model));
        int oldGameSize = getBlobSize(storage.readBlob(ofs));
        int newGameSize = getBlobSize(buf);
        if (newGameSize <= oldGameSize) {
            return 0;
        }
        int delta = newGameSize - oldGameSize;
        storage.insert(ofs, delta);
        return delta;
    }

    private int resolveEncodingMode(GameMovesModel model) {
        if (overrideEncodingMode >= 0) {
            return overrideEncodingMode;
        }
        if (model.root().position().isRegularChess()) {
            return 0;
        }
        return 10;
    }

    public void close() throws IOException {
        storage.close();
    }

    public FileBlobStorage getStorage() {
        return (FileBlobStorage) storage;
    }
}
