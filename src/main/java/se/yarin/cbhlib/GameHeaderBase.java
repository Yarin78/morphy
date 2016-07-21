package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.LineEvaluation;
import se.yarin.chess.Symbol;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;

public class GameHeaderBase implements GameHeaderSerializer {

    private static final Logger log = LoggerFactory.getLogger(GameHeaderBase.class);

    private static final int DEFAULT_HEADER_SIZE = 46;
    private static final int SERIALIZED_GAME_HEADER_SIZE = 46;

    private GameHeaderStorageBase storage;

    private GameHeaderBase() {
        this.storage = new InMemoryGameHeaderStorage(new GameHeaderStorageMetadata());
    }

    private GameHeaderBase(@NonNull GameHeaderStorageBase storage) throws IOException {
        this.storage = storage;
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
        PersistentGameHeaderStorage.createEmptyStorage(file, DEFAULT_HEADER_SIZE);
        return GameHeaderBase.open(file);
    }

    /**
     * Gets the number of game headers
     * @return the number of game headers
     */
    public int size() {
        return storage.getMetadata().getNextGameId() - 1;
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
     * Updates an existing game header
     * @param gameHeaderId the id of the game header to update
     * @param gameHeader the new data of the game header
     */
    public void update(int gameHeaderId, @NonNull GameHeader gameHeader) throws IOException {
        if (gameHeader.getId() != gameHeaderId) {
            gameHeader = gameHeader.toBuilder().id(gameHeaderId).build();
        }
        storage.put(gameHeader);
    }

    /**
     * Adds a new game header to the base
     * @param gameHeader the game header to add
     * @return the id that the game header received
     */
    public int add(@NonNull GameHeader gameHeader) throws IOException {
        int nextGameId = storage.getMetadata().getNextGameId();
        gameHeader = gameHeader.toBuilder().id(nextGameId).build();
        storage.put(gameHeader);
        // TODO: This doesn't save the metadata
        storage.getMetadata().setNextGameId(nextGameId + 1);
        return nextGameId;
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
            int unknownShort = ByteBufferUtil.getUnsignedShortB(buf);
            if (unknownShort != 0) {
                log.warn("Unknown short in guiding text: " + unknownShort);
            }
            builder.tournamentId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.sourceId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.annotatorId(ByteBufferUtil.getUnsigned24BitB(buf));
            builder.round(ByteBufferUtil.getUnsignedByte(buf));

            int unknownByte = ByteBufferUtil.getUnsignedByte(buf); // subround?
            if (unknownByte != 0) {
                log.warn("Unknown byte in guiding text: " + unknownByte);
            }

            int b1 = ByteBufferUtil.getUnsignedByte(buf);
            int b2 = ByteBufferUtil.getUnsignedByte(buf);
            EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
            if ((b1 & 4) > 0) flags.add(GameHeaderFlags.STREAM);
            if ((b1 & ~4) > 0) log.warn("GameHeaderFlags byte 1 in guiding text is " + b1);
            if ((b2 & 1) > 0) flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
            if ((b2 & 2) > 0) flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
            if ((b2 & 4) > 0) flags.add(GameHeaderFlags.EMBEDDED_VIDEO);
            if ((b2 & ~7) > 0) log.warn("GameHeaderFlags byte 2 in guiding text is " + b2);

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
            // TODO: Support subeco
            // TODO: In Fischer random games, this value probably indicates the starting position instead
            // But Chessbase 13 doesn't seem to support this!?
            int ecoValue = ByteBufferUtil.getUnsignedShortB(buf);
            try {
                builder.eco(CBUtil.decodeEco(ecoValue));
            } catch (IllegalArgumentException e) {
                log.error("Error parsing ECO in game " + gameId + ": " + ecoValue);
            }
            builder.medals(CBUtil.decodeMedals(ByteBufferUtil.getUnsignedShortB(buf)));
            int b1 = ByteBufferUtil.getUnsignedByte(buf);
            int b2 = ByteBufferUtil.getUnsignedByte(buf);
            int b3 = ByteBufferUtil.getUnsignedByte(buf);
            int b4 = ByteBufferUtil.getUnsignedByte(buf);
            EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
            if ((b1 & 1) > 0) flags.add(GameHeaderFlags.CRITICAL_POSITION);
            if ((b1 & 2) > 0) flags.add(GameHeaderFlags.CORRESPONDENCE_HEADER);
            if ((b1 & 8) > 0) flags.add(GameHeaderFlags.FISCHER_RANDOM);
            if ((b1 & ~11) > 0) log.warn("GameHeaderFlags byte 1 is " + b1 + " in game " + gameId);
            if ((b2 & 1) > 0) flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
            if ((b2 & 2) > 0) flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
            if ((b2 & 4) > 0) flags.add(GameHeaderFlags.EMBEDDED_VIDEO);
            if ((b2 & 8) > 0) flags.add(GameHeaderFlags.GAME_QUOTATION);
            if ((b2 & 16) > 0) flags.add(GameHeaderFlags.PATH_STRUCTURE);
            if ((b2 & 32) > 0) flags.add(GameHeaderFlags.PIECE_PATH);
            // bit 6 and 7 of b2 are set in many games in megabase 2016 but no idea what they are used for
//            if ((b2 & ~63) > 0) log.warn("GameHeaderFlags byte 2 is " + b2 + " in game " + gameId);
            // bit 0 in b3 is set in one game (#6161281) in megabase 2016
            if ((b3 & 2) > 0) flags.add(GameHeaderFlags.TRAINING);
            if ((b3 & ~2) > 0) log.warn("GameHeaderFlags byte 3 is " + b3 + " in game " + gameId);
            if ((b4 & 1) > 0) flags.add(GameHeaderFlags.SETUP_POSITION);
            if ((b4 & 2) > 0) flags.add(GameHeaderFlags.VARIATIONS);
            if ((b4 & 4) > 0) flags.add(GameHeaderFlags.COMMENTARY);
            if ((b4 & 8) > 0) flags.add(GameHeaderFlags.SYMBOLS);
            if ((b4 & 16) > 0) flags.add(GameHeaderFlags.GRAPHICAL_SQUARES);
            if ((b4 & 32) > 0) flags.add(GameHeaderFlags.GRAPHICAL_ARROWS);
            if ((b4 & 128) > 0) flags.add(GameHeaderFlags.TIME_NOTIFICATIONS);
            if ((b4 & ~191) > 0) log.warn("GameHeaderFlags byte 4 is " + b4 + " in game " + gameId);
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
//                extraAnnotations &= ~3;
            }
            if (flags.contains(GameHeaderFlags.COMMENTARY)) {
                builder.commentariesMagnitude(((extraAnnotations & 4) > 0) ? 2 : 1);
//                extraAnnotations &= ~4;
            }
            if (flags.contains(GameHeaderFlags.SYMBOLS)) {
                builder.symbolsMagnitude(((extraAnnotations & 8) > 0) ? 2 : 1);
//                extraAnnotations &= ~8;
            }
            if (flags.contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                builder.graphicalSquaresMagnitude(((extraAnnotations & 16) > 0) ? 2 : 1);
//                extraAnnotations &= ~16;
            }
            if (flags.contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                builder.graphicalArrowsMagnitude(((extraAnnotations & 32) > 0) ? 2 : 1);
//                extraAnnotations &= ~32;
            }
            if (flags.contains(GameHeaderFlags.TIME_NOTIFICATIONS)) {
                builder.timeAnnotationsMagnitude((extraAnnotations & 128) > 0 ? 2 : 1);
//                extraAnnotations &= ~128;
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
        ByteBuffer buffer = ByteBuffer.allocate(SERIALIZED_GAME_HEADER_SIZE);

        return buffer;
    }

    public int getSerializedGameHeaderLength() {
        return SERIALIZED_GAME_HEADER_SIZE;
    }
}
