package se.yarin.cbhlib.annotations;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.*;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GameQuotationAnnotation extends Annotation implements StatisticalAnnotation {

    private static final Logger log = LoggerFactory.getLogger(GameQuotationAnnotation.class);

    // TODO: The model should be read-only since annotations must be immutable
    @Getter
    private final GameHeaderModel header;

    // Deserialization is done lazily
    private byte[] setupPositionData;
    private byte[] gameData;

    @Getter
    private int unknown;

    /**
     * Creates a new GameQuotation annotation that only contains the header information about the game.
     * @param header the header model of the game to quote
     */
    public GameQuotationAnnotation(@NonNull GameHeaderModel header) {
        this.header = header;
        this.setupPositionData = null;
        this.gameData = null;
        this.unknown = 0;
    }

    /**
     * Creates a new GameQuotation annotation that contains both header and game moves
     * Annotations and variations will be stripped
     * @param game the game model of the game to quote
     */
    public GameQuotationAnnotation(@NonNull GameModel game) {
        this.header = game.header();

        // Only embed moves data if it's a regular chess game.
        // ChessBase doesn't seem to support embedding unorthodox games
        if (game.moves().root().position().isRegularChess()) {
            if (game.moves().isSetupPosition()) {
                this.setupPositionData = new byte[28];
                MovesSerializer.serializeInitialPosition(game.moves(),
                        ByteBuffer.wrap(this.setupPositionData), false);
            }

            MoveEncoder moveEncoder = new GameQuotationMoveEncoder();
            // This allocation is a bit ugly since it uses knowledge of the underlying encoder
            ByteBuffer buf = ByteBuffer.allocate(game.moves().countPly(false) * 2 + 2);
            moveEncoder.encode(buf, game.moves());
            gameData = buf.array();
        }
    }

    private GameQuotationAnnotation(
            @NonNull GameHeaderModel header,
            byte[] setupPositionData,
            byte[] gameData,
            int unknown) {
        this.setupPositionData = setupPositionData;
        this.gameData = gameData;
        this.header = header;
        this.unknown = unknown;
    }

    public boolean hasGame() {
        return gameData != null;
    }

    public GameModel getGameModel()  {
        if (hasGame()) {
            return new GameModel(getHeader(), getMoves());
        }
        return new GameModel(getHeader(), new GameMovesModel());
    }

    private GameMovesModel getMoves() {
        GameMovesModel moves;
        try {
            if (setupPositionData != null) {
                moves = MovesSerializer.parseInitialPosition(ByteBuffer.wrap(setupPositionData), false, 0);
            } else {
                moves = new GameMovesModel();
            }
        }
        catch (ChessBaseMoveDecodingException e) {
            log.warn("Error parsing initial position in game quotation", e);
            return new GameMovesModel();
        }

        try {
            MoveEncoder encoder = new GameQuotationMoveEncoder();
            encoder.decode(ByteBuffer.wrap(gameData), moves);
        } catch (ChessBaseMoveDecodingException e) {
            log.warn("Error parsing move in game quotation", e);
            moves = e.getModel();
        }

        return moves;
    }

    @Override
    public String toString() {
        return "GameQuotationAnnotation: " + header.toString();
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.GAME_QUOTATION);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameQuotationAnnotation that = (GameQuotationAnnotation) o;

        if (unknown != that.unknown) return false;
        if (!header.equals(that.header)) return false;
        if (!Arrays.equals(setupPositionData, that.setupPositionData)) return false;
        return Arrays.equals(gameData, that.gameData);
    }

    @Override
    public int hashCode() {
        return header.hashCode() * 31 + unknown;
    }

    public static class Serializer implements AnnotationSerializer {

        private <T> T valueOrDefault(T value, T defaultValue) {
            return value == null ? defaultValue : value;
        }

        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            GameQuotationAnnotation qa = (GameQuotationAnnotation) annotation;
            int start = buf.position();
            ByteBufferUtil.putShortB(buf, 0); // This is the size, will be filled in later
            ByteBufferUtil.putShortB(buf, qa.hasGame() ? 2 : 1);
            if (qa.setupPositionData != null) {
                ByteBufferUtil.putShortB(buf, 64);
                buf.put(qa.setupPositionData);
            } else {
                ByteBufferUtil.putShortB(buf, 0);
            }

            ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getWhite(), ""));
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getBlack(), ""));
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getWhiteElo(), 0));
            ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getBlackElo(), 0));
            ByteBufferUtil.putShortB(buf, CBUtil.encodeEco(valueOrDefault(qa.header.getEco(), Eco.unset())));
            ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getEvent(), ""));
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getEventSite(), ""));
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putIntB(buf, CBUtil.encodeDate(valueOrDefault(qa.header.getDate(), Date.unset())));

            TournamentTimeControl ttc = TournamentTimeControl.fromName(qa.header.getEventTimeControl());
            TournamentType tt = TournamentType.fromName(qa.header.getEventType());
            ByteBufferUtil.putShortB(buf, CBUtil.encodeTournamentType(tt, ttc));
            ByteBufferUtil.putShortB(buf, CBUtil.encodeNation(Nation.fromIOC(valueOrDefault(
                    qa.header.getEventCountry(), ""))));

            ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getEventCategory(), 0));
            ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getEventRounds(), 0));
            ByteBufferUtil.putByte(buf, valueOrDefault(qa.header.getSubRound(), 0));
            ByteBufferUtil.putByte(buf, valueOrDefault(qa.header.getRound(), 0));
            ByteBufferUtil.putByte(buf, CBUtil.encodeGameResult(valueOrDefault(qa.header.getResult(), GameResult.NOT_FINISHED)));
            ByteBufferUtil.putShortB(buf, qa.getUnknown());

            if (qa.gameData != null) {
                buf.put(qa.gameData);
            }
            int end = buf.position();
            buf.position(start);
            ByteBufferUtil.putShortB(buf, end - start);
            buf.position(end);
        }

        @Override
        public GameQuotationAnnotation deserialize(ByteBuffer buf, int length) {
            int startPos = buf.position();
            int size = ByteBufferUtil.getUnsignedShortB(buf);

            int type = ByteBufferUtil.getUnsignedShortB(buf);
            if (type != 1 && type != 2) {
                log.warn("Unknown game quotation type: " + type);
            }

            byte[] setupPositionData = null;

            int flags = ByteBufferUtil.getUnsignedShortB(buf);
            if ((flags & 64) > 0) {
                setupPositionData = new byte[28];
                buf.get(setupPositionData);
                flags -= 64;
            }
            if (flags != 0) {
                log.warn("Unknown flag value parsing game quotation: " + flags);
            }

            GameHeaderModel header = new GameHeaderModel();
            header.setWhite(ByteBufferUtil.getByteString(buf));
            buf.get();
            header.setBlack(ByteBufferUtil.getByteString(buf));
            buf.get();
            header.setWhiteElo(ByteBufferUtil.getUnsignedShortB(buf));
            header.setBlackElo(ByteBufferUtil.getUnsignedShortB(buf));
            Eco eco = CBUtil.decodeEco(ByteBufferUtil.getUnsignedShortB(buf));
            header.setEco(eco);
            header.setEvent(ByteBufferUtil.getByteString(buf));
            buf.get();
            header.setEventSite(ByteBufferUtil.getByteString(buf));
            buf.get();
            Date date = CBUtil.decodeDate(ByteBufferUtil.getIntB(buf));
            header.setDate(date);

            int typeValue = ByteBufferUtil.getUnsignedShortB(buf);
            TournamentTimeControl timeControl = CBUtil.decodeTournamentTimeControl(typeValue);
            TournamentType tournamentType = CBUtil.decodeTournamentType(typeValue);
            Nation nation = CBUtil.decodeNation(ByteBufferUtil.getUnsignedShortB(buf));

            header.setEventTimeControl(timeControl.getName());
            header.setEventType(tournamentType.getName());
            header.setEventCountry(nation.getIocCode());
            header.setEventCategory(ByteBufferUtil.getUnsignedShortB(buf));
            header.setEventRounds(ByteBufferUtil.getUnsignedShortB(buf));

            header.setSubRound(ByteBufferUtil.getUnsignedByte(buf));
            header.setRound(ByteBufferUtil.getUnsignedByte(buf));
            header.setResult(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf)));

            int unknown = ByteBufferUtil.getUnsignedShortB(buf);
            // This one is always set to some value. No idea what it does though.
            // log.warn(String.format("Unknown value in game quotation is %d (%04X), type is %d", unknown, unknown, type));

            byte[] gameData = new byte[size - (buf.position() - startPos)];
            buf.get(gameData);

            if (gameData.length == 0) {
                gameData = null;
            }

            return new GameQuotationAnnotation(header, setupPositionData, gameData, unknown);
        }

        @Override
        public Class getAnnotationClass() {
            return GameQuotationAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x13;
        }
    }
}
