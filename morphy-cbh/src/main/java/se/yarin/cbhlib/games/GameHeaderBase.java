package se.yarin.cbhlib.games;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.*;
import se.yarin.chess.Date;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GameHeaderBase implements GameHeaderSerializer {

    // TODO: GameHeaderBase and GameHeaderStorageBase + implementations needs refactoring
    // It's quite messy right now, could be made cleaner
    private static final Logger log = LoggerFactory.getLogger(GameHeaderBase.class);

    private static final int DEFAULT_UNKNOWN_FLAGS = 0x2C;
    private static final int DEFAULT_SERIALIZED_GAME_HEADER_SIZE = 46;

    private final GameHeaderStorageBase storage;

    public String getStorageName() {
        return storage.getStorageName();
    }

    /**
     * Creates a new game header base that is initially empty.
     */
    public GameHeaderBase() {
        this.storage = new InMemoryGameHeaderStorage();
    }

    private GameHeaderBase(@NonNull GameHeaderStorageBase storage) {
        this.storage = storage;
    }

    static GameHeaderStorageMetadata emptyMetadata() {
        GameHeaderStorageMetadata metadata = new GameHeaderStorageMetadata();
        metadata.setNextGameId(1);
        metadata.setNextGameId2(1);
        metadata.setUnknownByte2(1);
        metadata.setUnknownFlags(DEFAULT_UNKNOWN_FLAGS);
        metadata.setSerializedHeaderSize(DEFAULT_SERIALIZED_GAME_HEADER_SIZE);
        return metadata;
    }

    /**
     * Creates an in-memory game header base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory game header base
     */
    public static GameHeaderBase openInMemory(@NonNull File file) throws IOException {
        GameHeaderBase source = open(file);
        GameHeaderBase target = new GameHeaderBase();
        for (int i = 0; i < source.size(); i++) {
            GameHeader gameHeader = source.getGameHeader(i + 1);
            target.add(gameHeader);
        }
        return target;
    }

    /**
     * Opens a game header database from disk
     * @param file the game header databases to open
     * @return the opened game header database
     * @throws IOException if something went wrong when opening the database
     */
    public static GameHeaderBase open(@NonNull File file) throws IOException {
        return new GameHeaderBase(PersistentGameHeaderStorage.open(file, new GameHeaderBase()));
    }

    /**
     * Creates a new game header database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened game header database
     * @throws IOException if something went wrong when creating the database
     */
    public static GameHeaderBase create(@NonNull File file) throws IOException {
        PersistentGameHeaderStorage.createEmptyStorage(file, emptyMetadata());
        return GameHeaderBase.open(file);
    }

    /**
     * Gets the number of game headers
     * @return the number of game headers
     */
    public int size() {
        // TODO: rename to getCount() to make it consistent with other bases?
        return getNextGameId() - 1;
    }

    /**
     * Gets the id of the next header that will be stored
     * @return the next game id
     */
    public int getNextGameId() {
        return storage.getMetadata().getNextGameId();
    }

    /**
     * Gets a game header from the base
     * @param gameHeaderId the id of the game header to get
     * @return a game header id, or null if there was no game header with the specified id
     */
    public GameHeader getGameHeader(int gameHeaderId) {
        return storage.get(gameHeaderId);
    }


    /**
     * Returns an Iterable over all game headers in the database
     * @return an iterable
     */
    public Iterable<GameHeader> iterable() {
        return iterable(1);
    }

    /**
     * Returns an Iterable over all game headers in the database
     * @param gameId the id to start the iterable at (inclusive)
     * @return an iterable
     */
    public Iterable<GameHeader> iterable(int gameId) {
        return () -> new DefaultIterator(gameId, null);
    }

    /**
     * Returns a stream over all game headers in the database
     * @return a stream
     */
    public Stream<GameHeader> stream() {
        return stream(1, null);
    }

    /**
     * Returns a stream over all game headers in the database starting at the specified id
     * @param gameId the id to start the stream at (inclusive)
     * @return a stream
     */
    public Stream<GameHeader> stream(int gameId) {
        return stream(gameId, null);
    }

    /**
     * Returns a stream over all game headers in the database starting at the specified id
     * @param gameId the id to start the stream at (inclusive)
     * @param filter a optional low level filter that _may_ filter out games at the ByteBuffer level.
     *               The filter has no effect if the data has already been deserialized, so a proper
     *               {@link se.yarin.cbhlib.games.search.SearchFilter} should be used as well.
     *               However, since it's much faster to filter things out at this level, it's a nice performance boost.
     * @return a stream
     */
    public Stream<GameHeader> stream(int gameId, SerializedGameHeaderFilter filter) {
        if (filter != null && !(storage instanceof PersistentGameHeaderStorage)) {
            log.warn("A serialized GameHeader filter was specified in iteration but the underlying storage doesn't support it");
        }
        Iterable<GameHeader> iterable = () -> new DefaultIterator(gameId, filter);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Gets the underlying raw data for a game header.
     * For debugging purposes only.
     * @param gameId the id of the game to get
     * @return a byte array containing the underlying data
     */
    public byte[] getRaw(int gameId) {
        if (!(storage instanceof PersistentGameHeaderStorage)) {
            throw new IllegalStateException("The underlying storage is not a persistent storage");
        }
        return ((PersistentGameHeaderStorage) storage).getRaw(gameId);
    }


    private class DefaultIterator implements Iterator<GameHeader> {
        private List<GameHeader> batch = new ArrayList<>();
        private static final int BATCH_SIZE = 1000;
        private int batchPos, nextBatchStart;
        private final int version;
        private final SerializedGameHeaderFilter filter;

        private void getNextBatch() {
            int endId = Math.min(getNextGameId(), nextBatchStart + BATCH_SIZE);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                if (storage instanceof PersistentGameHeaderStorage) {
                    batch = ((PersistentGameHeaderStorage) storage).getRange(nextBatchStart, endId, filter);
                } else {
                    batch = storage.getRange(nextBatchStart, endId);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        DefaultIterator(int startId, SerializedGameHeaderFilter filter) {
            version = storage.getVersion();
            this.filter = filter;
            nextBatchStart = startId;
            prefetchBatch();
        }

        private void prefetchBatch() {
            while (batch != null && batchPos == batch.size()) {
                getNextBatch();
            }
        }

        @Override
        public boolean hasNext() {
            if (version != storage.getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
            return batch != null;
        }

        @Override
        public GameHeader next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of game header iteration reached");
            }
            GameHeader gameHeader = batch.get(batchPos++);
            prefetchBatch();
            return gameHeader;
        }
    }

    /**
     * Updates an existing game header
     * @param gameHeaderId the id of the game header to update
     * @param gameHeader the new data of the game header
     * @return the saved gameHeader
     */
    public GameHeader update(int gameHeaderId, @NonNull GameHeader gameHeader) {
        // The id may not be set in gameHeader, in which case we need to do this now
        if (gameHeader.getId() != gameHeaderId) {
            gameHeader = gameHeader.toBuilder().id(gameHeaderId).build();
        }
        storage.put(gameHeader);
        return gameHeader;
    }

    /**
     * Adds a new game header to the base. The id field in gameHeader will be ignored.
     * @param gameHeader the game header to add
     * @return the id that the game header received
     */
    public GameHeader add(@NonNull GameHeader gameHeader) {
        int nextGameId = storage.getMetadata().getNextGameId();
        gameHeader = gameHeader.toBuilder().id(nextGameId).build();
        storage.put(gameHeader);

        GameHeaderStorageMetadata metadata = storage.getMetadata();
        metadata.setNextGameId(nextGameId + 1);
        metadata.setNextGameId2(nextGameId + 1); // ??
        storage.setMetadata(metadata);
        return gameHeader;
    }

    public GameHeader deserialize(int gameId, ByteBuffer buf) {
        GameHeader.GameHeaderBuilder builder = GameHeader.builder();
        builder.id(gameId);
        int type = ByteBufferUtil.getUnsignedByte(buf);
        if ((type & 1) == 0) {
            log.warn("Game Header type bit 0 was not set in game " + gameId);
        }
        builder.game((type & 1) > 0);
        builder.deleted((type & 128) > 0);
        boolean guidingText = (type & 2) > 0;
        builder.guidingText(guidingText);

        if ((type & 3) != 1 && (type & 3) != 3) {
            log.warn("Unknown game type for game id " + gameId + ": " + type);
        }
        if ((type & ~131) != 0) {
            log.warn("Game header type for game id " + gameId + " is " + type);
        }
        builder.movesOffset(ByteBufferUtil.getIntB(buf));
        if (guidingText) {
            int unknownShort = ByteBufferUtil.getUnsignedShortB(buf);
            if (unknownShort != 0) {
                log.warn("Unknown short in guiding text: " + unknownShort);
            }
            builder.whitePlayerId(-1);
            builder.blackPlayerId(-1);
            builder.tournamentId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.sourceId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.annotatorId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.round(ByteBufferUtil.getUnsignedByte(buf));
            builder.subRound(ByteBufferUtil.getUnsignedByte(buf));

            // Set some default values since null is not allowed
            builder.playedDate(new Date(0));
            builder.result(GameResult.NOT_FINISHED);
            builder.eco(Eco.unset());
            builder.chess960StartPosition(-1);
            builder.lineEvaluation(NAG.NONE);
            builder.medals(EnumSet.noneOf(Medal.class));

            int flagInt = ByteBufferUtil.getIntB(buf);
            if ((flagInt & ~GameHeaderFlags.allFlagsMask()) > 0) {
                log.warn(String.format("Unknown game header flags in game %d: %08X", gameId, flagInt));
            }
            // TODO: Check what flags can actually be set in texts, update in GameLoader accordingly
            builder.flags(GameHeaderFlags.decodeFlags(flagInt));

            int i = 0;
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b != 0) {
                    log.warn("Trailing bytes in guiding text are not 0: byte " + i + " is " + b);
                }
                i++;
            }
        } else {
            builder.annotationOffset(ByteBufferUtil.getIntB(buf));
            builder.whitePlayerId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.blackPlayerId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.tournamentId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.annotatorId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.sourceId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.playedDate(CBUtil.decodeDate(ByteBufferUtil.getUnsigned24BitB(buf)));
            builder.result(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf)));
            NAG nag = NAG.values()[ByteBufferUtil.getUnsignedByte(buf)];
            if (nag.getType() == NAGType.LINE_EVALUATION) {
                builder.lineEvaluation(nag);
            } else {
                builder.lineEvaluation(NAG.NONE);
            }
            builder.round(ByteBufferUtil.getUnsignedByte(buf));
            builder.subRound(ByteBufferUtil.getUnsignedByte(buf));
            builder.whiteElo(ByteBufferUtil.getUnsignedShortB(buf));
            builder.blackElo(ByteBufferUtil.getUnsignedShortB(buf));
            int ecoValue = ByteBufferUtil.getUnsignedShortB(buf);
            if (ecoValue >= 65536 - 960) {
                builder.chess960StartPosition(ecoValue - 65536 + 960);
                builder.eco(Eco.unset());
            } else {
                try {
                    builder.eco(CBUtil.decodeEco(ecoValue));
                } catch (IllegalArgumentException e) {
                    log.error("Error parsing ECO in game " + gameId + ": " + ecoValue);
                    builder.eco(Eco.unset());
                }
                builder.chess960StartPosition(-1);
            }

            builder.medals(Medal.decode(ByteBufferUtil.getUnsignedShortB(buf)));

            int flagInt = ByteBufferUtil.getIntB(buf);
            if ((flagInt & ~GameHeaderFlags.allFlagsMask()) > 0) {
                log.warn(String.format("Unknown game header flags in game %d: %08X", gameId, flagInt));
            }
            EnumSet<GameHeaderFlags> flags = GameHeaderFlags.decodeFlags(flagInt);
            builder.flags(flags);

            // These extra annotation bytes provide extra significance to flags already set
            // If the corresponding flag isn't set, the bit may be dirty and should be ignored

            int annotationMagnitudeFlags = ByteBufferUtil.getUnsignedShortB(buf);

            if (!flags.isEmpty()) {  // Usually empty, so this is a performance optimization
                // This byte may be contain stray bits set which should probably be ignored
                if (flags.contains(GameHeaderFlags.VARIATIONS)) {
                    builder.variationsMagnitude(1 + (annotationMagnitudeFlags & 3));
                }
                if (flags.contains(GameHeaderFlags.COMMENTARY)) {
                    builder.commentariesMagnitude(((annotationMagnitudeFlags & 4) > 0) ? 2 : 1);
                }
                if (flags.contains(GameHeaderFlags.SYMBOLS)) {
                    builder.symbolsMagnitude(((annotationMagnitudeFlags & 8) > 0) ? 2 : 1);
                }
                if (flags.contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                    builder.graphicalSquaresMagnitude(((annotationMagnitudeFlags & 16) > 0) ? 2 : 1);
                }
                if (flags.contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                    builder.graphicalArrowsMagnitude(((annotationMagnitudeFlags & 32) > 0) ? 2 : 1);
                }
                if (flags.contains(GameHeaderFlags.TIME_SPENT)) {
                    builder.timeSpentMagnitude((annotationMagnitudeFlags & 128) > 0 ? 2 : 1);
                }
                if (flags.contains(GameHeaderFlags.TRAINING)) {
                    builder.trainingMagnitude(1 + ((annotationMagnitudeFlags & 512) > 0 ? 1 : 0));
                }
            }

            if ((annotationMagnitudeFlags & ~(3|4|8|16|32|128|512)) != 0) {
                log.warn("Unknown annotationMagnitudeFlags value " + annotationMagnitudeFlags + " in game " + gameId);
            }

            builder.noMoves(ByteBufferUtil.getUnsignedByte(buf));
        }

        return builder.build();
    }

    public void close() throws IOException {
        storage.close();
    }

    public ByteBuffer serialize(GameHeader header) {
            ByteBuffer buf = ByteBuffer.allocate(getSerializedGameHeaderLength());
        int type = 0;
        if (header.isGame()) type += 1;
        if (header.isGuidingText()) type += 2;
        if (header.isDeleted()) type += 128;
        ByteBufferUtil.putByte(buf, type);
        ByteBufferUtil.putIntB(buf, header.getMovesOffset());

        if (header.isGuidingText()) {
            // TODO: Test this
            ByteBufferUtil.putShortB(buf, 0);
            ByteBufferUtil.put24BitB(buf, header.getTournamentId());
            ByteBufferUtil.put24BitB(buf, header.getSourceId());
            ByteBufferUtil.put24BitB(buf, header.getAnnotatorId());
            ByteBufferUtil.putByte(buf, header.getRound());
            ByteBufferUtil.putByte(buf, header.getSubRound());
            ByteBufferUtil.putIntB(buf, GameHeaderFlags.encodeFlags(header.getFlags()));
            buf.position(buf.limit());
        } else {
            ByteBufferUtil.putIntB(buf, header.getAnnotationOffset());
            ByteBufferUtil.put24BitB(buf, header.getWhitePlayerId());
            ByteBufferUtil.put24BitB(buf, header.getBlackPlayerId());
            ByteBufferUtil.put24BitB(buf, header.getTournamentId());
            ByteBufferUtil.put24BitB(buf, header.getAnnotatorId());
            ByteBufferUtil.put24BitB(buf, header.getSourceId());
            ByteBufferUtil.put24BitB(buf, CBUtil.encodeDate(header.getPlayedDate()));
            ByteBufferUtil.putByte(buf, CBUtil.encodeGameResult(header.getResult()));
            ByteBufferUtil.putByte(buf, header.getLineEvaluation().ordinal());
            ByteBufferUtil.putByte(buf, header.getRound());
            ByteBufferUtil.putByte(buf, header.getSubRound());
            ByteBufferUtil.putShortB(buf, header.getWhiteElo());
            ByteBufferUtil.putShortB(buf, header.getBlackElo());
            if (header.getFlags().contains(GameHeaderFlags.UNORTHODOX)) {
                ByteBufferUtil.putShortB(buf, header.getChess960StartPosition() + 65536 - 960);
            } else {
                ByteBufferUtil.putShortB(buf, CBUtil.encodeEco(header.getEco()));
            }
            ByteBufferUtil.putShortB(buf, Medal.encode(header.getMedals()));
            ByteBufferUtil.putIntB(buf, GameHeaderFlags.encodeFlags(header.getFlags()));
            int annotationMagnitudeFlags = 0;

            if (header.getFlags().contains(GameHeaderFlags.VARIATIONS)) {
                annotationMagnitudeFlags += (header.getVariationsMagnitude() - 1) & 3;
            }
            if (header.getFlags().contains(GameHeaderFlags.COMMENTARY)) {
                if (header.getCommentariesMagnitude() == 2) {
                    annotationMagnitudeFlags += 4;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.SYMBOLS)) {
                if (header.getSymbolsMagnitude() == 2) {
                    annotationMagnitudeFlags += 8;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                if (header.getGraphicalSquaresMagnitude() == 2) {
                    annotationMagnitudeFlags += 16;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                if (header.getGraphicalArrowsMagnitude() == 2) {
                    annotationMagnitudeFlags += 32;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.TIME_SPENT)) {
                if (header.getTimeSpentMagnitude() == 2) {
                    annotationMagnitudeFlags += 128;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.TRAINING)) {
                if (header.getTrainingMagnitude() == 2) {
                    annotationMagnitudeFlags += 512;
                }
            }

            ByteBufferUtil.putShortB(buf, annotationMagnitudeFlags);
            ByteBufferUtil.putByte(buf, header.getNoMoves());
        }

        assert buf.position() == buf.limit();
        buf.flip();
        return buf;
    }

    /**
     * Adjusts the moves offset of all game headers that have their move offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param movesOffset a game is only affected if its moves offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    public void adjustMovesOffset(int startGameId, long movesOffset, long insertedBytes) {
        storage.adjustMovesOffset(startGameId, movesOffset, insertedBytes);
    }

    /**
     * Adjusts the annotation offset of all game headers that have their annotation offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param annotationOffset a game is only affected if its annotation offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    public void adjustAnnotationOffset(int startGameId, long annotationOffset, long insertedBytes) {
        storage.adjustAnnotationOffset(startGameId, annotationOffset, insertedBytes);
    }

    public int getSerializedGameHeaderLength() {
        return storage.getMetadata().getSerializedHeaderSize();
    }
}
