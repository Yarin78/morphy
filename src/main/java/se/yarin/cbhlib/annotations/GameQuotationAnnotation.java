package se.yarin.cbhlib.annotations;

import lombok.Builder;
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
public class GameQuotationAnnotation extends Annotation {

    private static final Logger log = LoggerFactory.getLogger(GameQuotationAnnotation.class);

    private static final byte[] encryptMap = new byte[256];
    private static final int[] decryptMap = new int[256];

    static {
        // This encryption map exists in the ChessBase executable file
        InputStream stream = MovesParser.class.getResourceAsStream("gameQuotationEncryptionKey.bin");
        try {
            stream.read(encryptMap);
            for (int i = 0; i < 256; i++) {
                decryptMap[(encryptMap[i] + 256) % 256] = i;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource gameQuotationEncryptionKey.bin");
        }
    }


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
            moves = MovesParser.parseInitialPosition(ByteBuffer.wrap(setupPositionData));
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

    public static GameQuotationAnnotation deserialize(ByteBuffer buf) {
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
}
