package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
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

import static java.nio.file.StandardOpenOption.*;

public class MoveRepository implements BlobSizeRetriever {

    private static final Logger log = LoggerFactory.getLogger(MoveRepository.class);

    private final @NotNull BlobStorage storage;
    private final @NotNull DatabaseContext context;
    private final @NotNull MoveSerializer moveSerializer;
    private int overrideEncodingMode = -1; // The encoding mode to use when writing games, -1 = default based on type

    private boolean validateDecodedMoves = true; // If true, validate all moves when decoding

    public @NotNull BlobStorage getStorage() { return storage; }

    public @NotNull MoveSerializer getMoveSerializer() { return moveSerializer; }

    public @NotNull DatabaseContext context() {
        return context;
    }

    public void setEncodingMode(int encodingMode) {
        this.overrideEncodingMode = encodingMode;
    }

    public void setValidateDecodedMoves(boolean validateDecodedMoves) { this.validateDecodedMoves = validateDecodedMoves; }

    /**
     * Creates a new in-memory move repository that is initially empty.
     */
    public MoveRepository() {
        this(null);
    }

    /**
     * Creates a new in-memory move repository that is initially empty.
     */
    public MoveRepository(@Nullable DatabaseContext context) {
        this.storage = new InMemoryBlobStorage(this);
        this.moveSerializer = new MoveSerializer();
        this.context = context == null ? new DatabaseContext() : context;
    }

    private MoveRepository(@NotNull BlobStorage storage, @Nullable DatabaseContext context) {
        this.storage = storage;
        this.moveSerializer = new MoveSerializer();
        this.context = context == null ? new DatabaseContext() : context;
    }

    private MoveRepository(@NotNull File file, @NotNull Set<OpenOption> openOptions, @Nullable DatabaseContext context) throws IOException {
        this.storage = new FileBlobStorage(file, this, openOptions);
        this.moveSerializer = new MoveSerializer();
        this.context = context == null ? new DatabaseContext() : context;
    }

    public static MoveRepository create(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return new MoveRepository(file, Set.of(READ, WRITE, CREATE_NEW), context);
    }

    /**
     * Opens a move repository from disk for read-write
     * @param file the move repository to open
     * @return the opened repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static MoveRepository open(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    /**
     * Opens a move repository from disk
     * @param file the move repository to open
     * @param mode basic operations mode (typically read-only or read-write)
     * @return the opened repository
     * @throws IOException if something went wrong when opening the repository
     */
    public static MoveRepository open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context) throws IOException {
        return mode == DatabaseMode.IN_MEMORY
                ? new MoveRepository(loadInMemoryStorage(file, context), context)
                : new MoveRepository(file, mode.openOptions(), context);
    }

    /**
     * Loads a move repository from file into an in-memory repository.
     * Any writes to the repository will not be persisted to disk.
     * @param file the file to populate the in-memory repository with
     * @return an open in-memory repository
     */
    protected static BlobStorage loadInMemoryStorage(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), READ);
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.read(buf);
        channel.close();
        buf.flip();
        return new InMemoryBlobStorage(buf, new MoveRepository(context));
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
     * @param offset the offset in the repository where the game moves (or text) data is stored
     * @return a read only byte buffer containing the game moves
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when reading the moves
     */
    public @NotNull ByteBuffer getMovesBlob(long offset) {
        ByteBuffer blob = storage.getBlob(offset);
        return blob.asReadOnlyBuffer();
    }

    /**
     * Gets the size of the moves blob that make up the moves of the game.
     * @param offset the offset in the repository where the game moves (or text) data are stored
     * @return the size of the blob
     */
    public int getMovesBlobSize(long offset) {
        return storage.getBlobSize(offset);
    }

    /**
     * Gets the moves of a game from the move repository
     * @param offset the offset in the repository where the game moves data is stored
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
     * Gets the contents of a text entry from the move repository
     * @param offset the offset in the repository where the text contents are stored
     * @param gameId the id of the text to load; only used in logging statements
     * @return a model of the text entry
     */
    public TextContentsModel getText(long offset, int gameId) throws MorphyMoveDecodingException {
        ByteBuffer blob = storage.getBlob(offset);
        return TextContentsModel.deserialize(gameId, blob);
    }

    /**
     * Puts the moves of a game into the move repository
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the repository
     * @param model the game to store
     * @return The offset where the game moves was stored
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the moves
     */
    public long putMoves(long ofs, GameMovesModel model) {
        ByteBuffer buf = moveSerializer.serializeMoves(model, resolveEncodingMode(model));
        return putMovesBlob(ofs, buf);
    }

    /**
     * Puts the contents of a text entry into the move repository
     * @param ofs the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the repository
     * @param model the text to store
     * @return The offset where the game moves was stored
     * @throws se.yarin.morphy.exceptions.MorphyIOException if there was some IO errors when storing the moves
     */
    public long putText(long ofs, TextContentsModel model) {
        ByteBuffer buf = model.serialize();
        return putMovesBlob(ofs, buf);
    }

    /**
     * Puts the moves of a game into the move repository
     * @param offset the old offset where moves of this game was stored,
     *            or 0 if this is a new game in the repository
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

    /**
     * Removes a moves blob
     * @param offset the offset of the blob
     * @return the size of the removed blob
     */
    public int removeMovesBlob(long offset) {
        if (offset > 0) {
            return storage.removeBlob(offset);
        }
        return 0;
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

    public void insert(long offset, int delta) {
        storage.insert(offset, delta);
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
}
