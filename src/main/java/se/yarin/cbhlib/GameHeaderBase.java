package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.UncheckedEntityException;
import se.yarin.chess.*;
import se.yarin.chess.Date;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class GameHeaderBase implements GameHeaderSerializer, Iterable<GameHeader> {

    // TODO: GameHeaderBase and GameHeaderStorageBase + implementations needs refactoring
    // It's quite messy right now, could be made cleaner
    // TODO: Add search
    private static final Logger log = LoggerFactory.getLogger(GameHeaderBase.class);

    private static final int DEFAULT_UNKNOWN_FLAGS = 0x2C;
    private static final int DEFAULT_SERIALIZED_GAME_HEADER_SIZE = 46;

    private GameHeaderStorageBase storage;

    /**
     * Creates a new game header base that is initially empty.
     */
    public GameHeaderBase() {
        this.storage = new InMemoryGameHeaderStorage();
    }

    private GameHeaderBase(@NonNull GameHeaderStorageBase storage) throws IOException {
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
    int getNextGameId() {
        return storage.getMetadata().getNextGameId();
    }

    /**
     * Gets a game header from the base
     * @param gameHeaderId the id of the game header to get
     * @return a game header id, or null if there was no game header with the specified id
     */
    public GameHeader getGameHeader(int gameHeaderId) throws IOException {
        return storage.get(gameHeaderId);
    }

    /**
     * Gets an iterator over all game headers in the database
     * @return an iterator
     */
    public Iterator<GameHeader> iterator() {
        return new DefaultIterator(1);
    }

    private class DefaultIterator implements Iterator<GameHeader> {
        private List<GameHeader> batch = new ArrayList<>();
        private int batchPos, nextBatchStart = 0, batchSize = 1000;
        private final int version;

        private void getNextBatch() {
            int endId = Math.min(getNextGameId(), nextBatchStart + batchSize);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                try {
                    batch = storage.getRange(nextBatchStart, endId);
                } catch (IOException e) {
                    throw new UncheckedEntityException("An IO error when iterating game headers", e);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        DefaultIterator(int startId) {
            version = storage.getVersion();
            nextBatchStart = startId;
            prefetchBatch();
        }

        private void prefetchBatch() {
            if (batch == null || batchPos == batch.size()) {
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
    public GameHeader update(int gameHeaderId, @NonNull GameHeader gameHeader) throws IOException {
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
    public GameHeader add(@NonNull GameHeader gameHeader) throws IOException {
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
        builder.game((type & 1) > 0); // Is this one always set?
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
            // TODO: Test this
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
            builder.lineEvaluation(LineEvaluation.NO_EVALUATION);
            builder.medals(EnumSet.noneOf(Medal.class));

            int b1 = ByteBufferUtil.getUnsignedByte(buf);
            int b2 = ByteBufferUtil.getUnsignedByte(buf);
            EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
            if ((b1 & 4) > 0) flags.add(GameHeaderFlags.STREAM);
            if ((b1 & ~4) > 0) log.warn("GameHeaderFlags byte 1 in guiding text is " + b1);
            if ((b2 & 1) > 0) flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
            if ((b2 & 2) > 0) flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
            if ((b2 & 4) > 0) flags.add(GameHeaderFlags.EMBEDDED_VIDEO);
            if ((b2 & ~7) > 0) log.warn("GameHeaderFlags byte 2 in guiding text is " + b2);
            builder.flags(flags);

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
            Symbol symbol = CBUtil.decodeSymbol((byte) ByteBufferUtil.getUnsignedByte(buf));
            if (symbol instanceof LineEvaluation) {
                builder.lineEvaluation((LineEvaluation) symbol);
            } else {
                builder.lineEvaluation(LineEvaluation.NO_EVALUATION);
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
            builder.medals(CBUtil.decodeMedals(ByteBufferUtil.getUnsignedShortB(buf)));
            int flagInt = ByteBufferUtil.getIntB(buf);

            EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
            for (GameHeaderFlags flag : GameHeaderFlags.values()) {
                if ((flagInt & flag.getValue()) > 0) {
                    flags.add(flag);
                }
            }

            if ((flagInt & ~0x1BFF03BF) > 0) {
                log.warn(String.format("Unknown game header flags in game %d: %08X", gameId, flagInt));
            }

            builder.flags(flags);
            // These extra annotation bytes provide extra significance to flags already set
            // If the corresponding flag isn't set, the bit may be dirty and should be ignored
            int extraAnnotations2 = ByteBufferUtil.getUnsignedByte(buf);
            if (flags.contains(GameHeaderFlags.TRAINING)) {
                builder.trainingMagnitude(1 + (extraAnnotations2 & 2) > 0 ? 1 : 0);
            }
            extraAnnotations2 &= ~2;
            if (extraAnnotations2 != 0) {
                log.warn("Unknown extraAnnotations2 value " + extraAnnotations2 + " in game " + gameId);
            }
            int extraAnnotations = ByteBufferUtil.getUnsignedByte(buf);
            // This byte may be contain stray bits set which should probably be ignored
            if (flags.contains(GameHeaderFlags.VARIATIONS)) {
                builder.variationsMagnitude(1 + (extraAnnotations & 3));
                extraAnnotations &= ~3;
            }
            if (flags.contains(GameHeaderFlags.COMMENTARY)) {
                builder.commentariesMagnitude(((extraAnnotations & 4) > 0) ? 2 : 1);
                extraAnnotations &= ~4;
            }
            if (flags.contains(GameHeaderFlags.SYMBOLS)) {
                builder.symbolsMagnitude(((extraAnnotations & 8) > 0) ? 2 : 1);
                extraAnnotations &= ~8;
            }
            if (flags.contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                builder.graphicalSquaresMagnitude(((extraAnnotations & 16) > 0) ? 2 : 1);
                extraAnnotations &= ~16;
            }
            if (flags.contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                builder.graphicalArrowsMagnitude(((extraAnnotations & 32) > 0) ? 2 : 1);
                extraAnnotations &= ~32;
            }
            if (flags.contains(GameHeaderFlags.TIME_SPENT)) {
                builder.timeSpentMagnitude((extraAnnotations & 128) > 0 ? 2 : 1);
                extraAnnotations &= ~128;
            }
            extraAnnotations &= 64;
            if (extraAnnotations != 0) {
                log.warn("Unknown extraAnnotations value " + extraAnnotations + " in game " + gameId);
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
            int b1 = 0, b2 = 0;
            if (header.getFlags().contains(GameHeaderFlags.STREAM)) b1 += 4;
            if (header.getFlags().contains(GameHeaderFlags.EMBEDDED_AUDIO)) b2 += 1;
            if (header.getFlags().contains(GameHeaderFlags.EMBEDDED_PICTURE)) b2 += 2;
            if (header.getFlags().contains(GameHeaderFlags.EMBEDDED_VIDEO)) b2 += 4;
            ByteBufferUtil.putByte(buf, b1);
            ByteBufferUtil.putByte(buf, b2);
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
            ByteBufferUtil.putByte(buf, CBUtil.encodeSymbol(header.getLineEvaluation()));
            ByteBufferUtil.putByte(buf, header.getRound());
            ByteBufferUtil.putByte(buf, header.getSubRound());
            ByteBufferUtil.putShortB(buf, header.getWhiteElo());
            ByteBufferUtil.putShortB(buf, header.getBlackElo());
            if (header.getFlags().contains(GameHeaderFlags.UNORTHODOX)) {
                ByteBufferUtil.putShortB(buf, header.getChess960StartPosition() + 65536 - 960);
            } else {
                ByteBufferUtil.putShortB(buf, CBUtil.encodeEco(header.getEco()));
            }
            ByteBufferUtil.putShortB(buf, CBUtil.encodeMedals(header.getMedals()));
            int flagInt = 0;
            for (GameHeaderFlags flag : header.getFlags()) {
                flagInt += flag.getValue();
            }
            ByteBufferUtil.putIntB(buf, flagInt);
            int extraAnnotations2 = 0, extraAnnotations = 0;

            if (header.getFlags().contains(GameHeaderFlags.TRAINING)) {
                if (header.getTrainingMagnitude() == 2) {
                    extraAnnotations2 += 2;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.VARIATIONS)) {
                extraAnnotations += (header.getVariationsMagnitude() - 1) & 3;
            }
            if (header.getFlags().contains(GameHeaderFlags.COMMENTARY)) {
                if (header.getCommentariesMagnitude() == 2) {
                    extraAnnotations += 4;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.SYMBOLS)) {
                if (header.getSymbolsMagnitude() == 2) {
                    extraAnnotations += 8;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                if (header.getGraphicalSquaresMagnitude() == 2) {
                    extraAnnotations += 16;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                if (header.getGraphicalArrowsMagnitude() == 2) {
                    extraAnnotations += 32;
                }
            }
            if (header.getFlags().contains(GameHeaderFlags.TIME_SPENT)) {
                if (header.getTimeSpentMagnitude() == 2) {
                    extraAnnotations += 128;
                }
            }
            ByteBufferUtil.putByte(buf, extraAnnotations2);
            ByteBufferUtil.putByte(buf, extraAnnotations);
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
    void adjustMovesOffset(int startGameId, int movesOffset, int insertedBytes) throws IOException {
        storage.adjustMovesOffset(startGameId, movesOffset, insertedBytes);
    }

    public int getSerializedGameHeaderLength() {
        return storage.getMetadata().getSerializedHeaderSize();
    }
}
