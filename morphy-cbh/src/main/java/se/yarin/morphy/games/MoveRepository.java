package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.games.moves.MoveSerializer;
import se.yarin.morphy.storage.*;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class MoveRepository implements BlobSizeRetriever {

    private static final Logger log = LoggerFactory.getLogger(MoveRepository.class);

    private final BlobStorage storage;
    // @Getter
    private final MoveSerializer moveSerializer;
    private int overrideEncodingMode = -1; // The encoding mode to use when writing games, -1 = default based on type

    private boolean validateDecodedMoves = true; // If true, validate all moves when decoding

    public void setEncodingMode(int encodingMode) {
        this.overrideEncodingMode = encodingMode;
    }

    public void setValidateDecodedMoves(boolean validateDecodedMoves) { this.validateDecodedMoves = validateDecodedMoves; }

    /**
     * Creates a new moves base that is initially empty.
     */
    public MoveRepository() {
        this.storage = new InMemoryBlobStorage(this);
        this.moveSerializer = new MoveSerializer();
    }

    private MoveRepository(@NotNull BlobStorage storage) {
        this.storage = storage;
        this.moveSerializer = new MoveSerializer();
    }

    private MoveRepository(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this.storage = new FileBlobStorage(file, this, openOptions);
        this.moveSerializer = new MoveSerializer();
    }

    /**
     * Creates an in-memory moves base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory moves base
     */
    public static MoveRepository openInMemory(@NotNull File file) throws IOException {
        return new MoveRepository(loadInMemoryStorage(file));
    }

    /**
     * Opens a move repository from disk
     * @param file the moves databases to open
     * @return the opened repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static MoveRepository open(@NotNull File file) throws IOException {
        return new MoveRepository(file, Set.of(READ, WRITE));
    }

    /**
     * Opens a move repository from disk
     * @param file the moves databases to open
     * @param openOptions options specifying how the repository should be opened
     * @return the opened repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static MoveRepository open(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        return new MoveRepository(file, openOptions);
    }

    /**
     * Creates a new moves database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened moves database
     * @throws IOException if something went wrong when creating the database
     */
/*
    public static MoveRepository create(@NotNull File file) throws IOException {
        FileBlobStorage.createEmptyStorage(file);
        return open(file);
    }
*/
    /**
     * Loads an moves database from file into an in-memory storage.
     * Any writes to the database will not be persisted to disk.
     * @param file the file to populate the in-memory database with
     * @return an open in-memory storage
     */
    protected static BlobStorage loadInMemoryStorage(@NotNull File file) throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), READ);
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.read(buf);
        channel.close();
        buf.flip();
        return new InMemoryBlobStorage(buf, new MoveRepository());
    }

    @Override
    public int getBlobSize(ByteBuffer buf) {
        int size = ByteBufferUtil.getIntB(buf) & 0xFFFFFF;
        buf.position(buf.position() - 4);
        return size;
    }

    /**
     * Gets the serialized bytes that make up the moves of the game
     * or the text for guiding texts.
     * @param offset the offset in the database where the game moves (or text) data is stored
     * @return a read only byte buffer containing the game moves
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when reading the moves
     */
    public ByteBuffer getMovesBlob(long offset) {
        ByteBuffer blob = storage.getBlob(offset);
        return blob.asReadOnlyBuffer();
    }

    /**
     * Gets the moves of a game from the moves database
     * @param offset the offset in the database where the game moves data is stored
     * @param gameId the id of the game to load; only used in logging statements
     * @return a model of the game
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when reading the moves
     */
    public GameMovesModel getMoves(long offset, int gameId) throws MorphyInvalidDataException {
        ByteBuffer blob = storage.getBlob(offset);
        try {
            return moveSerializer.deserializeMoves(blob, validateDecodedMoves, gameId);
        } catch (MorphyMoveDecodingException e) {
            // If there was an error parsing the moves, returned what we got so far
            log.warn("Error decoding moves in game " + gameId + ": " + e.getMessage());
            return e.getModel();
        }
    }

    /**
     * Gets the contents of a text entry from the moves database
     * @param offset the offset in the database where the text contents are stored
     * @param gameId the id of the text to load; only used in logging statements
     * @return a model of the text entry
     */
    public TextContentsModel getText(long offset, int gameId) throws MorphyMoveDecodingException {
        ByteBuffer blob = storage.getBlob(offset);
        return TextContentsModel.deserialize(gameId, blob);
    }

    /**
     * Puts the moves of a game into the moves database
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the database
     * @param model the game to store
     * @return The offset where the game moves was stored
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the moves
     */
    public long putMoves(long ofs, GameMovesModel model) {
        ByteBuffer buf = moveSerializer.serializeMoves(model, resolveEncodingMode(model));
        return putMovesBlob(ofs, buf);
    }

    /**
     * Puts the contents of a text entry into the moves database
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the database
     * @param model the text to store
     * @return The offset where the game moves was stored
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the moves
     */
    public long putText(long ofs, TextContentsModel model) {
        ByteBuffer buf = model.serialize();
        return putMovesBlob(ofs, buf);
    }

    /**
     * Puts the moves of a game into the moves database
     * @param offset the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the database
     * @param blob the serialized moves to store
     * @return The offset where the game moves was stored
     */
    public long putMovesBlob(long offset, ByteBuffer blob) {
        if (offset > 0) {
            storage.putBlob(offset, blob);
            return offset;
        }
        return storage.appendBlob(blob);
    }

    public int preparePutBlob(long offset, GameMovesModel model) {
        ByteBuffer buf = moveSerializer.serializeMoves(model, resolveEncodingMode(model));
        int oldGameSize = getBlobSize(storage.getBlob(offset));
        int newGameSize = getBlobSize(buf);
        if (newGameSize <= oldGameSize) {
            return 0;
        }
        int delta = newGameSize - oldGameSize;
        storage.insert(offset, delta);
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
