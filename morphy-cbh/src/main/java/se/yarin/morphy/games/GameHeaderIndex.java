package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.storage.ItemStorageSerializer;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;
import static se.yarin.morphy.storage.MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS;

public class GameHeaderIndex implements ItemStorageSerializer<GameHeaderIndex.Prolog, GameHeader> {

    private static final Logger log = LoggerFactory.getLogger(GameHeaderIndex.class);

    private final ItemStorage<GameHeaderIndex.Prolog, GameHeader> storage;

    public GameHeaderIndex() {
        this.storage = new InMemoryItemStorage<>(Prolog.empty(), null, true);
    }

    private GameHeaderIndex(
            @NotNull File file,
            @NotNull Set<OpenOption> options) throws IOException {
        if (!CBUtil.extension(file).equals(".cbh")) {
            throw new IllegalArgumentException("The file extension of a GameHeader index must be .cbh");
        }

        boolean strict = options.contains(WRITE) || !options.contains(IGNORE_NON_CRITICAL_ERRORS);

        this.storage = new FileItemStorage<>(file, this, Prolog.empty(), options);
        if (strict) {
            if (storage.getHeader().serializedItemSize() != Prolog.DEFAULT_SERIALIZED_ITEM_SIZE) {
                throw new MorphyNotSupportedException("Game header item size mismatches; writes not possible.");
            }
        }
    }

    public static GameHeaderIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return new GameHeaderIndex(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static GameHeaderIndex open(@NotNull File file) throws IOException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    public static GameHeaderIndex open(@NotNull File file, @NotNull DatabaseMode mode) throws IOException {
        if (mode == DatabaseMode.IN_MEMORY) {
            GameHeaderIndex source = open(file, DatabaseMode.READ_ONLY);
            GameHeaderIndex target = new GameHeaderIndex();
            source.copyGameHeaders(target);
            return target;
        }
        return new GameHeaderIndex(file, mode.openOptions());
    }

    private void copyGameHeaders(GameHeaderIndex target) {
        if (target.count() != 0) {
            // Since this is a low-level copy, things will not work if there already are games in the target index
            throw new IllegalStateException("Target index must be empty");
        }
        for (int i = 1; i <= count(); i++) {
            target.storage.putItem(i, storage.getItem(i));
        }
        target.storage.putHeader(storage.getHeader());
    }

    /**
     * Gets the number of game headers in the index
     * @return the number of game headers
     */
    public int count() {
        return storage.getHeader().nextGameId() - 1;
    }

    /**
     * Gets a game header from the index
     * If the id is invalid (larger than the capacity of the index), an {@link IllegalArgumentException} is thrown.
     * @param gameHeaderId the id of the game header to get
     * @return a game header with the specified id
     */
    public @NotNull GameHeader getGameHeader(int gameHeaderId) {
        try {
            return storage.getItem(gameHeaderId);
        } catch (MorphyIOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Updates an existing game header
     * @param gameHeaderId the id of the game header to update
     * @param gameHeader the new data of the game header
     */
    public void update(int gameHeaderId, @NotNull GameHeader gameHeader) {
        storage.putItem(gameHeaderId, gameHeader);
    }

    /**
     * Adds a new game header to the index. The id field in gameHeader will be ignored.
     * @param gameHeader the game header to add
     * @return the id that the game header received
     */
    public int add(@NotNull GameHeader gameHeader) {
        int newGameId = 1 + count();

        // This is necessary for the InMemory storage
        gameHeader = ImmutableGameHeader.copyOf(gameHeader).withId(newGameId);

        storage.putItem(newGameId, gameHeader);

        ImmutableProlog newProlog = ImmutableProlog.copyOf(storage.getHeader())
                .withNextGameId(newGameId + 1)
                .withNextGameId2(newGameId + 1);
        storage.putHeader(newProlog);

        return newGameId;
    }

    /**
     * Updates a game header in the index. The id field in gameHeader will be ignored
     * @param gameId the id to replace
     * @param gameHeader the new game header
     */
    public void put(int gameId, @NotNull GameHeader gameHeader) {
        if (gameId > count()) {
            throw new IllegalArgumentException("Can't put a game beyond the end of the storage");
        }
        // This is necessary for the InMemory storage
        gameHeader = ImmutableGameHeader.copyOf(gameHeader).withId(gameId);
        storage.putItem(gameId, gameHeader);
    }

    /**
     * Gets a list of all game headers in the index.
     * @return a list of all GameHeaders
     */
    public List<GameHeader> getAll() {
        ArrayList<GameHeader> result = new ArrayList<>(count());
        for (int i = 1; i <= count(); i++) {
            result.add(storage.getItem(i));
        }
        return result;
    }

    /**
     * Represents the Prolog in the CBH file.
     * See games.md for more information
     */
    @Value.Immutable
    public static abstract class Prolog {
        public static final int DEFAULT_SERIALIZED_ITEM_SIZE = 46;

        @Value.Default
        public int unknownByte1() {
            return 0;
        }

        @Value.Default
        public int unknownFlags() {
            // 0x24 in old bases, 0x2C in new ones
            return 0x2C;
        };

        @Value.Default
        public int serializedItemSize() {
            // Both file header and each game header
            return DEFAULT_SERIALIZED_ITEM_SIZE;
        }

        @Value.Default
        public int unknownByte2() { return 1; }

        @Value.Default
        public int nextGameId() {
            return 1;
        }

        @Value.Default
        public int unknownShort1() { return 0; }

        @Value.Default
        public int nextEmbeddedSoundId() { return 0; }

        @Value.Default
        public int nextEmbeddedPictureId() { return 0; }

        @Value.Default
        public int nextEmbeddedVideoId() { return 0; }

        @Value.Default
        public int unknownInt1() { return 0; }

        @Value.Default
        public int unknownInt2() { return 0; }

        @Value.Default
        public int unknownInt3() { return 0; }

        @Value.Default
        public int unknownInt4() { return 0; }

        @Value.Default
        public int nextGameId2() {
            // Virtually always the same as nextGameId
            return 1;
        }

        @Value.Default
        public int unknownShort2() {
            return 0;
        };

        public static Prolog empty() {
            return ImmutableProlog.builder().build();
        }
    }

    @Override
    public int serializedHeaderSize() {
        return Prolog.DEFAULT_SERIALIZED_ITEM_SIZE;
    }

    @Override
    public long itemOffset(@NotNull Prolog prolog, int id) {
        // Games are 1-indexed, but the prolog is the same size as an item
        return (long) id * prolog.serializedItemSize();
    }

    @Override
    public int itemSize(@NotNull Prolog prolog) {
        return prolog.serializedItemSize();
    }

    @Override
    public int headerSize(@NotNull Prolog prolog) {
        // Headers and items have the same size; actually, the header
        // is probably meant to be an item as well
        return prolog.serializedItemSize();
    }

    @Override
    public @NotNull Prolog deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException {
        ImmutableProlog prolog = ImmutableProlog.builder()
                .unknownByte1(ByteBufferUtil.getUnsignedByte(buf))
                .unknownFlags(ByteBufferUtil.getUnsignedShortB(buf))
                .serializedItemSize(ByteBufferUtil.getUnsignedShortB(buf))
                .unknownByte2(ByteBufferUtil.getUnsignedByte(buf))
                .nextGameId(ByteBufferUtil.getIntB(buf))
                .unknownShort1(ByteBufferUtil.getUnsignedShortB(buf))
                .nextEmbeddedSoundId(ByteBufferUtil.getIntB(buf))
                .nextEmbeddedPictureId(ByteBufferUtil.getIntB(buf))
                .nextEmbeddedVideoId(ByteBufferUtil.getIntB(buf))
                .unknownInt1(ByteBufferUtil.getIntB(buf))
                .unknownInt2(ByteBufferUtil.getIntB(buf))
                .unknownInt3(ByteBufferUtil.getIntB(buf))
                .unknownInt4(ByteBufferUtil.getIntB(buf))
                .nextGameId2(ByteBufferUtil.getIntB(buf))
                .unknownShort2(ByteBufferUtil.getUnsignedShortB(buf))
                .build();

        if (prolog.unknownFlags() != 0x24 && prolog.unknownFlags() != 0x2C) {
            log.warn("Prolog unknown flags = " + prolog.unknownFlags());
        }
        if (prolog.serializedItemSize() != Prolog.DEFAULT_SERIALIZED_ITEM_SIZE) {
            log.warn("Invalid serialized size in chb prolog = " + prolog.serializedItemSize());
        }
        if (prolog.unknownByte1() != 0 || prolog.unknownByte2() != 1 || prolog.unknownShort1() != 0 || prolog.unknownShort2() != 0 ||
                prolog.unknownInt1() != 0 || prolog.unknownInt2() != 0 || prolog.unknownInt3() != 0 || prolog.unknownInt4() != 0) {
            log.warn(String.format("Unknown prolog bytes did not have the default values: %d %d %d %d %d %d %d %d",
                    prolog.unknownByte1(), prolog.unknownByte2(), prolog.unknownShort1(), prolog.unknownShort2(),
                    prolog.unknownInt1(), prolog.unknownInt2(), prolog.unknownInt3(), prolog.unknownInt4()));
        }
        if (prolog.nextGameId2() != prolog.nextGameId() && prolog.nextGameId2() != 0) {
            log.warn(String.format("Second nextGameId didn't match the first one (%d != %d)", prolog.nextGameId(), prolog.nextGameId2()));
        }

        return prolog;
    }

    @Override
    public void serializeHeader(@NotNull Prolog prolog, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putByte(buf, prolog.unknownByte1());
        ByteBufferUtil.putShortB(buf, prolog.unknownFlags());
        ByteBufferUtil.putShortB(buf, prolog.serializedItemSize());
        ByteBufferUtil.putByte(buf, prolog.unknownByte2());
        ByteBufferUtil.putIntB(buf, prolog.nextGameId());
        ByteBufferUtil.putShortB(buf, prolog.unknownShort1());
        ByteBufferUtil.putIntB(buf, prolog.nextEmbeddedSoundId());
        ByteBufferUtil.putIntB(buf, prolog.nextEmbeddedPictureId());
        ByteBufferUtil.putIntB(buf, prolog.nextEmbeddedVideoId());
        ByteBufferUtil.putIntB(buf, prolog.unknownInt1());
        ByteBufferUtil.putIntB(buf, prolog.unknownInt2());
        ByteBufferUtil.putIntB(buf, prolog.unknownInt3());
        ByteBufferUtil.putIntB(buf, prolog.unknownInt4());
        ByteBufferUtil.putIntB(buf, prolog.nextGameId2());
        ByteBufferUtil.putShortB(buf, prolog.unknownShort2());
    }

    @Override
    public @NotNull GameHeader deserializeItem(int gameHeaderId, @NotNull ByteBuffer buf) {
        ImmutableGameHeader.Builder builder = ImmutableGameHeader.builder();
        builder.id(gameHeaderId);
        int type = ByteBufferUtil.getUnsignedByte(buf);
        if ((type & 1) == 0) {
            log.warn("Game Header type bit 0 was not set in game " + gameHeaderId);
        }
        builder.deleted((type & 128) > 0);
        boolean guidingText = (type & 2) > 0;
        builder.guidingText(guidingText);

        if ((type & 3) != 1 && (type & 3) != 3) {
            log.warn("Unknown game type for game id " + gameHeaderId + ": " + type);
        }
        if ((type & ~131) != 0) {
            log.warn("Game header type for game id " + gameHeaderId + " is " + type);
        }
        int movesOffset = ByteBufferUtil.getIntB(buf), annotationOffset = 0;
        builder.movesOffset(movesOffset);
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
            builder.noMoves(0);

            int flagInt = ByteBufferUtil.getIntB(buf);
            if ((flagInt & ~GameHeaderFlags.allFlagsMask()) > 0) {
                log.warn(String.format("Unknown game header flags in game %d: %08X", gameHeaderId, flagInt));
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
            annotationOffset = ByteBufferUtil.getIntB(buf);
            builder.annotationOffset(annotationOffset);
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
                    log.error("Error parsing ECO in game " + gameHeaderId + ": " + ecoValue);
                    builder.eco(Eco.unset());
                }
                builder.chess960StartPosition(-1);
            }

            builder.medals(Medal.decode(ByteBufferUtil.getUnsignedShortB(buf)));

            int flagInt = ByteBufferUtil.getIntB(buf);
            if ((flagInt & ~GameHeaderFlags.allFlagsMask()) > 0) {
                log.warn(String.format("Unknown game header flags in game %d: %08X", gameHeaderId, flagInt));
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
                log.warn("Unknown annotationMagnitudeFlags value " + annotationMagnitudeFlags + " in game " + gameHeaderId);
            }

            builder.noMoves(ByteBufferUtil.getUnsignedByte(buf));
        }

        return builder.build();
    }

    @Override
    public void serializeItem(@NotNull GameHeader gameHeader, @NotNull ByteBuffer buf) {
        int type = 1;
        if (gameHeader.guidingText()) type += 2;
        if (gameHeader.deleted()) type += 128;
        ByteBufferUtil.putByte(buf, type);
        ByteBufferUtil.putIntB(buf, gameHeader.movesOffset());

        if (gameHeader.guidingText()) {
            // TODO: Test this
            ByteBufferUtil.putShortB(buf, 0);
            ByteBufferUtil.put24BitB(buf, gameHeader.tournamentId());
            ByteBufferUtil.put24BitB(buf, gameHeader.sourceId());
            ByteBufferUtil.put24BitB(buf, gameHeader.annotatorId());
            ByteBufferUtil.putByte(buf, gameHeader.round());
            ByteBufferUtil.putByte(buf, gameHeader.subRound());
            ByteBufferUtil.putIntB(buf, GameHeaderFlags.encodeFlags(gameHeader.flags()));
            buf.position(buf.limit());
        } else {
            ByteBufferUtil.putIntB(buf, gameHeader.annotationOffset());
            ByteBufferUtil.put24BitB(buf, gameHeader.whitePlayerId());
            ByteBufferUtil.put24BitB(buf, gameHeader.blackPlayerId());
            ByteBufferUtil.put24BitB(buf, gameHeader.tournamentId());
            ByteBufferUtil.put24BitB(buf, gameHeader.annotatorId());
            ByteBufferUtil.put24BitB(buf, gameHeader.sourceId());
            ByteBufferUtil.put24BitB(buf, CBUtil.encodeDate(gameHeader.playedDate()));
            ByteBufferUtil.putByte(buf, CBUtil.encodeGameResult(gameHeader.result()));
            ByteBufferUtil.putByte(buf, gameHeader.lineEvaluation().ordinal());
            ByteBufferUtil.putByte(buf, gameHeader.round());
            ByteBufferUtil.putByte(buf, gameHeader.subRound());
            ByteBufferUtil.putShortB(buf, gameHeader.whiteElo());
            ByteBufferUtil.putShortB(buf, gameHeader.blackElo());
            if (gameHeader.flags().contains(GameHeaderFlags.UNORTHODOX)) {
                ByteBufferUtil.putShortB(buf, gameHeader.chess960StartPosition() + 65536 - 960);
            } else {
                ByteBufferUtil.putShortB(buf, CBUtil.encodeEco(gameHeader.eco()));
            }
            ByteBufferUtil.putShortB(buf, Medal.encode(gameHeader.medals()));
            ByteBufferUtil.putIntB(buf, GameHeaderFlags.encodeFlags(gameHeader.flags()));
            int annotationMagnitudeFlags = 0;

            if (gameHeader.flags().contains(GameHeaderFlags.VARIATIONS)) {
                annotationMagnitudeFlags += (gameHeader.variationsMagnitude() - 1) & 3;
            }
            if (gameHeader.flags().contains(GameHeaderFlags.COMMENTARY)) {
                if (gameHeader.commentariesMagnitude() == 2) {
                    annotationMagnitudeFlags += 4;
                }
            }
            if (gameHeader.flags().contains(GameHeaderFlags.SYMBOLS)) {
                if (gameHeader.symbolsMagnitude() == 2) {
                    annotationMagnitudeFlags += 8;
                }
            }
            if (gameHeader.flags().contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
                if (gameHeader.graphicalSquaresMagnitude() == 2) {
                    annotationMagnitudeFlags += 16;
                }
            }
            if (gameHeader.flags().contains(GameHeaderFlags.GRAPHICAL_ARROWS)) {
                if (gameHeader.graphicalArrowsMagnitude() == 2) {
                    annotationMagnitudeFlags += 32;
                }
            }
            if (gameHeader.flags().contains(GameHeaderFlags.TIME_SPENT)) {
                if (gameHeader.timeSpentMagnitude() == 2) {
                    annotationMagnitudeFlags += 128;
                }
            }
            if (gameHeader.flags().contains(GameHeaderFlags.TRAINING)) {
                if (gameHeader.trainingMagnitude() == 2) {
                    annotationMagnitudeFlags += 512;
                }
            }

            ByteBufferUtil.putShortB(buf, annotationMagnitudeFlags);
            ByteBufferUtil.putByte(buf, gameHeader.noMoves());
        }
    }

    @Override
    public @NotNull GameHeader emptyItem(int id) {
        return ImmutableGameHeader.builder()
                .id(id)
                .whitePlayerId(0)
                .blackPlayerId(0)
                .tournamentId(0)
                .annotatorId(0)
                .sourceId(0)
                .build();
    }

    public void close() {
        storage.close();
    }
}
