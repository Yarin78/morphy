package se.yarin.cbhlib.annotations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.*;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

@Builder
@EqualsAndHashCode(callSuper = false)
public class GameQuotationAnnotation extends Annotation implements StatisticalAnnotation {

    private static final Logger log = LoggerFactory.getLogger(GameQuotationAnnotation.class);

    // TODO: Move serialization to MovesSerializer
    private static final short[] encryptMap = KeyProvider.getMoveSerializationKey(8);
    private static final short[] decryptMap = KeyProvider.getMoveSerializationKey(9);

    @Getter
    private int type; // 1 = link to game in same database, 2 = contains game data

    @Getter
    @NonNull
    private String white;

    @Getter
    @NonNull
    private String black;

    @Getter
    private int whiteElo;

    @Getter
    private int blackElo;

    @Getter
    @NonNull
    private Eco eco;

    @Getter
    @NonNull
    private String event;

    @Getter
    @NonNull
    private String site;

    @Getter
    @NonNull
    private Date date;

    @Getter
    @NonNull
    private TournamentType tournamentType;

    @Getter
    @NonNull
    private TournamentTimeControl tournamentTimeControl;

    @Getter
    @NonNull
    private Nation tournamentCountry;

    @Getter
    private int tournamentCategory;

    @Getter
    private int tournamentRounds;

    @Getter
    private int round;

    @Getter
    private int subRound;

    @Getter
    private GameResult result;

    @Getter
    private int unknown;

    private byte[] setupPositionData;
    private byte[] gameData;

    public boolean hasSetupPosition() {
        return setupPositionData != null;
    }

    public boolean hasGame() {
        return type == 2;
    }

    // TODO: Should return a GameModel
    public GameMovesModel getMoves() throws ChessBaseInvalidDataException {
        GameMovesModel moves;
        if (hasSetupPosition()) {
            moves = MovesSerializer.parseInitialPosition(ByteBuffer.wrap(setupPositionData));
        } else {
            moves = new GameMovesModel();
        }

        GameMovesModel.Node current = moves.root();

        int modifier = 0;
        for (int i = 0; i < gameData.length; i+=2) {
            int b1 = decryptMap[(gameData[i] + modifier + 256) % 256];
            int b2 = decryptMap[(gameData[i+1] + modifier + 256) % 256];
            int op = b1 * 256 + b2;
            int fromSqi = op % 64, toSqi = (op / 64) % 64;
            int code = op / (64 * 64); // 0 = queen, 1 = rook, 2 = bishop, 3 = knight
            if (fromSqi == 0 && toSqi == 0 && code == 4) {
                // End marker
                break;
            }
            Piece promotionPiece = Piece.NO_PIECE;
            int row = Chess.sqiToRow(toSqi);
            if ((row == 0 || row == 7) && current.position().stoneAt(fromSqi).toPiece() == Piece.PAWN) {
                switch (code) {
                    case 0:
                        promotionPiece = Piece.QUEEN;
                        break;
                    case 1:
                        promotionPiece = Piece.ROOK;
                        break;
                    case 2:
                        promotionPiece = Piece.BISHOP;
                        break;
                    case 3:
                        promotionPiece = Piece.KNIGHT;
                        break;
                }
            }
            Stone promotionStone = promotionPiece.toStone(current.position().playerToMove());
            ShortMove move = new ShortMove(fromSqi, toSqi, promotionStone);
            try {
                current = current.addMove(move);
            } catch (IllegalMoveException e) {
                // Happens in game 359942 in megabase 2016, quotation Uhlmann,W (2535)-Kortschnoj,V (2665)
                log.warn("Illegal move in game quotation", e);
                break;
            }

            modifier = (modifier + 255) % 256;
        }

        return moves;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getWhite().length() > 0) {
            sb.append(getWhite());
            if (getWhiteElo() > 0) {
                sb.append(String.format(" (%d)", getWhiteElo()));
            }
        }
        if (getWhite().length() > 0 && getBlack().length() > 0) {
            sb.append("-");
        }
        if (getBlack().length() > 0) {
            sb.append(getBlack());
            if (getBlackElo() > 0) {
                sb.append(String.format(" (%d)", getBlackElo()));
            }
        }
        if (getEvent().length() > 0) {
            sb.append(" ").append(getEvent());
        }
        if (getSite().length() > 0) {
            sb.append(" ").append(getSite());
        }
        if (getDate().year() > 0) {
            sb.append(" ").append(getDate().toString());
        }

        if (getRound() > 0) {
            if (getSubRound() > 0) {
                sb.append(String.format(" (%d.%d)", getRound(), getSubRound()));
            } else {
                sb.append(String.format(" (%d)", getRound()));
            }
        }
        sb.append(" ").append(getResult().toString());

        return sb.toString();
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.GAME_QUOTATION);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            GameQuotationAnnotation qa = (GameQuotationAnnotation) annotation;
            int start = buf.position();
            ByteBufferUtil.putShortB(buf, 0); // This is the size, will be filled in later
            ByteBufferUtil.putShortB(buf, qa.getType());
            if (qa.hasSetupPosition()) {
                ByteBufferUtil.putShortB(buf, 64);
                buf.put(qa.setupPositionData);
                // TODO: use MovesSerializer.serializeInitialPosition()
            } else {
                ByteBufferUtil.putShortB(buf, 0);
            }

            ByteBufferUtil.putByteString(buf, qa.getWhite());
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putByteString(buf, qa.getBlack());
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putShortB(buf, qa.getWhiteElo());
            ByteBufferUtil.putShortB(buf, qa.getBlackElo());
            ByteBufferUtil.putShortB(buf, CBUtil.encodeEco(qa.getEco()));
            ByteBufferUtil.putByteString(buf, qa.getEvent());
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putByteString(buf, qa.getSite());
            ByteBufferUtil.putByte(buf, 0);
            ByteBufferUtil.putIntB(buf, CBUtil.encodeDate(qa.getDate()));
            int typeValue = 0;
            switch (qa.getTournamentTimeControl()) {
                case BLITZ:
                    typeValue = 32;
                    break;
                case RAPID:
                    typeValue = 64;
                    break;
                case CORRESPONDENCE:
                    typeValue = 128;
                    break;
            }
            typeValue += qa.getTournamentType().ordinal();
            ByteBufferUtil.putShortB(buf, typeValue);
            ByteBufferUtil.putShortB(buf, qa.getTournamentCountry().ordinal());
            ByteBufferUtil.putShortB(buf, qa.getTournamentCategory());
            ByteBufferUtil.putShortB(buf, qa.getTournamentRounds());

            ByteBufferUtil.putByte(buf, qa.getSubRound());
            ByteBufferUtil.putByte(buf, qa.getRound());
            ByteBufferUtil.putByte(buf, CBUtil.encodeGameResult(qa.getResult()));
            ByteBufferUtil.putShortB(buf, qa.getUnknown());

            buf.put(qa.gameData);
            int end = buf.position();
            buf.position(start);
            ByteBufferUtil.putShortB(buf, end - start);
            buf.position(end);
        }

        @Override
        public GameQuotationAnnotation deserialize(ByteBuffer buf, int length) {
            int startPos = buf.position();
            int size = ByteBufferUtil.getUnsignedShortB(buf);
            GameQuotationAnnotationBuilder builder = GameQuotationAnnotation.builder();
            int type = ByteBufferUtil.getUnsignedShortB(buf);
            if (type != 1 && type != 2) {
                log.warn("Unknown game quotation type: " + type);
            }
            builder.type(type);

            int flags = ByteBufferUtil.getUnsignedShortB(buf);
            if ((flags & 64) > 0) {
                byte[] setupPositionData = new byte[28];
                buf.get(setupPositionData);
                flags -= 64;
                builder.setupPositionData(setupPositionData);
            }
            if (flags != 0) {
                log.warn("Unknown flag value parsing game quotation: " + flags);
            }

            builder.white(ByteBufferUtil.getByteString(buf));
            buf.get();
            builder.black(ByteBufferUtil.getByteString(buf));
            buf.get();
            builder.whiteElo(ByteBufferUtil.getUnsignedShortB(buf));
            builder.blackElo(ByteBufferUtil.getUnsignedShortB(buf));
            Eco eco = CBUtil.decodeEco(ByteBufferUtil.getUnsignedShortB(buf));
            builder.eco(eco);
            builder.event(ByteBufferUtil.getByteString(buf));
            buf.get();
            builder.site(ByteBufferUtil.getByteString(buf));
            buf.get();
            Date date = CBUtil.decodeDate(ByteBufferUtil.getIntB(buf));
            builder.date(date);

            int typeValue = ByteBufferUtil.getUnsignedShortB(buf);
            builder.tournamentTimeControl(TournamentTimeControl.NORMAL);
            if ((typeValue & 32) > 0) builder.tournamentTimeControl(TournamentTimeControl.BLITZ);
            if ((typeValue & 64) > 0) builder.tournamentTimeControl(TournamentTimeControl.RAPID);
            if ((typeValue & 128) > 0) builder.tournamentTimeControl(TournamentTimeControl.CORRESPONDENCE);
            builder.tournamentType(TournamentType.values()[typeValue & 31]);
            builder.tournamentCountry(Nation.values()[ByteBufferUtil.getUnsignedShortB(buf)]);
            builder.tournamentCategory(ByteBufferUtil.getUnsignedShortB(buf));
            builder.tournamentRounds(ByteBufferUtil.getUnsignedShortB(buf));

            builder.subRound(ByteBufferUtil.getUnsignedByte(buf));
            builder.round(ByteBufferUtil.getUnsignedByte(buf));
            builder.result(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf)));

            int unknown = ByteBufferUtil.getUnsignedShortB(buf);
            // This one is always set to some value. No idea what it does though.
            // log.warn(String.format("Unknown value in game quotation is %d (%04X), type is %d", unknown, unknown, type));
            builder.unknown(unknown);

            byte[] gameData = new byte[size - (buf.position() - startPos)];
            buf.get(gameData);

            builder.gameData(gameData);
            return builder.build();
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
