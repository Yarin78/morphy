package se.yarin.cbhlib.moves;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferBitReader;
import se.yarin.util.ByteBufferBitWriter;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.EnumSet;

public final class MovesSerializer {
    private static final Logger log = LoggerFactory.getLogger(MovesSerializer.class);

    @Getter
    @Setter
    private boolean logDetailedErrors;

    // The CBG format supports many different chess variants, which are encoded differently.
    // This library only supports regular chess and Chess 960 at the moment.

    private static final int FLAG0_ENCRYPTION_KEY = 0;
    private static final int FLAG1_ENCRYPTION_KEY = 2;
    private static final int FLAG2_ENCRYPTION_KEY = 4;
    private static final int FLAG3_ENCRYPTION_KEY = 6;
    private static final int FLAG4_ENCRYPTION_KEY = 10;
    private static final int FLAG5_ENCRYPTION_KEY = 12;
    private static final int FLAG6_ENCRYPTION_KEY = 14;
    private static final int FLAG7_ENCRYPTION_KEY = 16;
    private static final int FLAG8_ENCRYPTION_KEY = 18;
    private static final int FLAG9_ENCRYPTION_KEY = 20;
    private static final int FLAG10_ENCRYPTION_KEY = 22;
    private static final int FLAG11_ENCRYPTION_KEY = 24;

    public MovesSerializer() {
        this(false);
    }

    public MovesSerializer(boolean logDetailedErrors) {
        this.logDetailedErrors = logDetailedErrors;
    }

    public ByteBuffer serializeMoves(@NonNull GameMovesModel model) {
        // TODO: All serialize method should probably not return a ByteBuffer but write to an existing one
        if (model.root().position().isRegularChess()) {
            return serializeMoves(model, 0);
        } else {
            // Chess960 requires a special encoding
            return serializeMoves(model, 10);
        }
    }

    ByteBuffer serializeMoves(@NonNull GameMovesModel model, int encodingMode) {
        validateEncodingMode(encodingMode);

        ByteBuffer buf = ByteBuffer.allocate(16384);

        int flags = encodingMode;

        if (model.isSetupPosition()) {
            flags |= 0x40;
        }

        boolean isChess960 = !model.root().position().isRegularChess();

        if (isChess960 && (encodingMode != 10 && encodingMode != 11)) {
            throw new IllegalArgumentException("Chess 960 requires encoding mode 10 or 11");
        }

        buf.put((byte) flags);
        // We don't know the size yet so skip ahead 3 bytes
        buf.position(4);

        if (model.isSetupPosition()) {
            serializeInitialPosition(model, buf, isChess960);
        }

        MoveEncoder moveEncoder = getMoveEncoder(encodingMode);

        try {
            moveEncoder.encode(buf, model);
        } catch (IllegalArgumentException e) {
            log.warn("Game contained illegal move that couldn't be encoded", e);
        }

        int size = buf.position();
        buf.position(1);
        ByteBufferUtil.put24BitB(buf, size);
        buf.position(size);
        buf.flip();
        return buf;
    }

    /**
     * Deserializes the moves of a ChessBase encoded chess game.
     * If there was some error decoding the game
     * @param buf a buffer containing the serialized game
     * @return a model of the game
     * @throws ChessBaseMoveDecodingException if there was an error deserializing the moves
     */
    public GameMovesModel deserializeMoves(ByteBuffer buf) throws ChessBaseMoveDecodingException {
        return deserializeMoves(buf, true, 0);
    }

    /**
     * Deserializes the moves of a ChessBase encoded chess game.
     * If there was some error decoding the game
     * @param buf a buffer containing the serialized game
     * @param checkLegalMoves if true, all decoded moves will be checked if they are legal or not
     * @param gameId the id of the game to load; only used in logging statements
     * @return a model of the game
     * @throws ChessBaseMoveDecodingException if there was an error deserializing the moves,
     * with the {@link ChessBaseMoveDecodingException#getModel()} containing the moves parsed so far.
     */
    public GameMovesModel deserializeMoves(ByteBuffer buf, boolean checkLegalMoves, int gameId) throws ChessBaseMoveDecodingException {
        GameMovesModel model;
        int flags, moveSize;
        try {
            flags = ByteBufferUtil.getUnsignedByte(buf);
            moveSize = ByteBufferUtil.getUnsigned24BitB(buf);
        } catch (BufferUnderflowException e) {
            throw new ChessBaseMoveDecodingException("Moves data header ended abruptly", e);
        }
        // Ensure we don't read too many bytes if the data would be broken
        ByteBuffer moveBuf = buf.slice();
        buf.position(buf.position() + moveSize - 4);
        moveBuf.limit(moveSize - 4);

        if ((flags & 0x80) > 0) {
            // The usage of this bit is unknown. Doesn't seem to affect anything.
            // It's set in two games in Mega Database 2016:
            // INFO  CheckMoveEncodingFlags - Flag 80: 1 games: 6161154
            // INFO  CheckMoveEncodingFlags - Flag 85: 1 games: 2017673

            log.warn("Bit 7 set in first byte in move data in game " + gameId);
        }
        boolean setupPosition = (flags & 0x40) > 0;

        int encodingMode = flags & 0x3F;
        validateEncodingMode(encodingMode);

        if (setupPosition) {
            model = parseInitialPosition(moveBuf, encodingMode == 10 || encodingMode == 11, gameId);
        } else {
            model = new GameMovesModel();
        }

        if (encodingMode != 0 && encodingMode != 10) {
            log.warn(String.format("Move data in game " + gameId + " has an unusual encoding: %02X", encodingMode));
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Parsing move data for game " + gameId + " at moveData pos %s with %d bytes left",
                    model.root().position().toString("|"), moveBuf.limit() - moveBuf.position()));
        }

        MoveEncoder moveEncoder = getMoveEncoder(encodingMode);

        try {
            moveEncoder.decode(moveBuf, model, checkLegalMoves);
        } catch (ChessBaseMoveDecodingException e) {
            // TODO: Add tests for this
            e.setModel(model);
            throw e;
        } catch (IllegalMoveException e) {
            String message = "Illegal move";
            if (logDetailedErrors) {
                message += ": " + e.toString().replace("\n", "\\");
            }
            throw new ChessBaseMoveDecodingException(message, e, model);
        } catch (BufferUnderflowException e) {
            String message = "Move data ended abruptly";
            if (logDetailedErrors) {
                message += ". Moves parsed so far: " + model.toString();
            }
            throw new ChessBaseMoveDecodingException(message, e, model);
        }

        return model;
    }

    private MoveEncoder getMoveEncoder(int encodingMode) {
        return switch (encodingMode) {
            case 0x00 -> new CompactMoveEncoder(FLAG0_ENCRYPTION_KEY, true, false);
            case 0x01 -> new SimpleMoveEncoder(FLAG1_ENCRYPTION_KEY, true, false);
            case 0x02 -> new CompactMoveEncoder(FLAG2_ENCRYPTION_KEY, true, true);
            case 0x03 -> new SimpleMoveEncoder(FLAG3_ENCRYPTION_KEY, true, true);
            case 0x04 -> new CompactMoveEncoder(FLAG4_ENCRYPTION_KEY, false, false);
            case 0x05 -> new SimpleMoveEncoder(FLAG5_ENCRYPTION_KEY, false, false);
            case 0x06 -> new CompactMoveEncoder(FLAG6_ENCRYPTION_KEY, false, true);
            case 0x07 -> new SimpleMoveEncoder(FLAG7_ENCRYPTION_KEY, false, true);
            case 0x08 -> new CompactMoveEncoder(FLAG8_ENCRYPTION_KEY, false, false); // Give away chess
            case 0x09 -> new CompactMoveEncoder(FLAG9_ENCRYPTION_KEY, false, true); // Give away chess
            case 0x0A -> new CompactMoveEncoder(FLAG10_ENCRYPTION_KEY, false, false); // Chess960
            case 0x0B -> new CompactMoveEncoder(FLAG11_ENCRYPTION_KEY, false, true); // Chess960
            default -> throw new UnsupportedOperationException(String.format("Unsupported move encoder: %02X", encodingMode));
        };
    }

    private void validateEncodingMode(int mode) {
        if (mode >= 0 && mode < 8) {
            return; // Regular chess
        }
        if (mode == 8 || mode == 9) {
            throw new UnsupportedOperationException("Chess variant 'giveaway' not supported");
        }
        if (mode == 10 || mode == 11) {
            return; // Chess 960
        }
        if (mode == 12 || mode == 13) {
            throw new UnsupportedOperationException("Chess variant 'out chatrang' not supported");
        }
        if (mode == 14 || mode == 15) {
            throw new UnsupportedOperationException("Chess variant 'twins' not supported");
        }
        if (mode == 16 || mode == 17) {
            throw new UnsupportedOperationException("Chess variant 'makruk' not supported");
        }
        if (mode == 18 || mode == 19) {
            throw new UnsupportedOperationException("Chess variant 'pawns' not supported");
        }
        if (mode == 63) {
            // TODO: Support illegal positions
            throw new UnsupportedOperationException("Illegal positions not supported");
        }
        throw new UnsupportedOperationException("Unknown encoding mode " + mode + " not supported");
    }

    public GameMovesModel parseInitialPosition(ByteBuffer buf, boolean hasExtraInfo, int gameId)
            throws ChessBaseMoveDecodingException {
        int endPosition = buf.position() + 28;
        int b = ByteBufferUtil.getUnsignedByte(buf);
        if (b != 1) {
            // Version?
            log.warn(String.format("Unexpected first byte in setup position in game " + gameId + ": %02X", b));
        }
        b = ByteBufferUtil.getUnsignedByte(buf);
        int epFile = (b & 15) - 1;
        Player sideToMove = (b & 16) == 0 ? Player.WHITE : Player.BLACK;
        if ((b & ~31) != 0) {
            log.warn("Unknown bits set in second byte in setup position in game " + gameId + ": " + b);
        }
        b = ByteBufferUtil.getUnsignedByte(buf);
        EnumSet<Castles> castles = EnumSet.noneOf(Castles.class);
        if ((b & 1) > 0) castles.add(Castles.WHITE_LONG_CASTLE);
        if ((b & 2) > 0) castles.add(Castles.WHITE_SHORT_CASTLE);
        if ((b & 4) > 0) castles.add(Castles.BLACK_LONG_CASTLE);
        if ((b & 8) > 0) castles.add(Castles.BLACK_SHORT_CASTLE);
        if ((b & ~15) != 0) {
            log.warn("Unknown bits set in third byte in setup position in game " + gameId + ": " + b);
        }
        int moveNumber = ByteBufferUtil.getUnsignedByte(buf);
        if (moveNumber == 0) {
            // 0 and 1 seems to mean same thing!?
            moveNumber = 1;
        }
        Stone[] stones = new Stone[64];
        ByteBufferBitReader byteBufferBitReader = new ByteBufferBitReader(buf);

        for (int i = 0; i < 64; i++) {
            if (byteBufferBitReader.getBit() == 0) {
                stones[i] = Stone.NO_STONE;
            } else {
                Player color = byteBufferBitReader.getBit() == 0 ? Player.WHITE : Player.BLACK;
                Piece piece;
                int pieceNo = byteBufferBitReader.getInt(3);
                piece = switch (pieceNo) {
                    case 1 -> Piece.KING;
                    case 2 -> Piece.QUEEN;
                    case 3 -> Piece.KNIGHT;
                    case 4 -> Piece.BISHOP;
                    case 5 -> Piece.ROOK;
                    case 6 -> Piece.PAWN;
                    default -> throw new ChessBaseMoveDecodingException("Invalid piece in setup position in game " + gameId + ": " + pieceNo);
                };
                stones[i] = piece.toStone(color);
            }
        }

        buf.position(endPosition);

        int sp = hasExtraInfo ? deserializeChess960StartingPosition(buf, stones, gameId) : Chess960.REGULAR_CHESS_SP;

        if (sp < 0 || sp >= 960) {
            throw new ChessBaseMoveDecodingException("Invalid Chess960 position " + sp + " in game " + gameId);
        }

        Position startPosition = new Position(stones, sideToMove, castles, epFile, sp);
        return new GameMovesModel(startPosition, moveNumber);
    }

    private int deserializeChess960StartingPosition(ByteBuffer buf, Stone[] stones, int gameId) {
        // The following 6 bytes contains the start square indices for
        // the white king, black king, white h-rook, white a-rook, black h-rook and black a-rook
        // They determine how the castles move is encoded
        int wkSqi = ByteBufferUtil.getUnsignedByte(buf);
        int bkSqi = ByteBufferUtil.getUnsignedByte(buf);
        int wkrSqi = ByteBufferUtil.getUnsignedByte(buf);
        int wqrSqi = ByteBufferUtil.getUnsignedByte(buf);
        int bkrSqi = ByteBufferUtil.getUnsignedByte(buf);
        int bqrSqi = ByteBufferUtil.getUnsignedByte(buf);

        // Then follows the start position number.
        // However, this value is wrong in some database (probably due to a ChessBase bug)
        int spNo = ByteBufferUtil.getUnsignedShortB(buf);

        if (spNo < 0 || spNo >= 960) {
            // TODO: Is this still wrong if only the lower 10 bits are considered?
            log.warn("Invalid Chess960 start position in game " + gameId + ": " + spNo);
        } else {
            Position sp = Chess960.getStartPosition(spNo);

            if (chess960Matches(sp, wkSqi, bkSqi, wkrSqi, wqrSqi, bkrSqi, bqrSqi)) {
                return spNo;
            }
            // This happens on some games in Mega Database 2016 and is probably due to a bug in an earlier Chessbase version
            log.warn("The specific positions of the kings and rooks doesn't match the Chess960 start position number in game " + gameId);
        }

        // The encoded start position is wrong, so we need to figure out what it should have been.
        // We really only need the information from the 6 first bytes, but our position model doesn't use them.
        // Instead, we try to determine the position number use the following heuristics:
        //  * Check if the actual initial position of the pieces is a valid Chess960 start position
        //    that matches the king and rook positions
        //  * If not, just find any Chess960 starting position that does so
        //  * If that fails, we just have an invalid game that probably can't be decoded correctly

        int originalSpNo = spNo;
        try {
            spNo = Chess960.getStartPositionNo(stones);
            Position sp = Chess960.getStartPosition(spNo);
            if (chess960Matches(sp, wkSqi, bkSqi, wkrSqi, wqrSqi, bkrSqi, bqrSqi)) {
                log.debug("Deduced the Chess960 starting position number in game " + gameId + " to be " + spNo);
                return spNo;
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            for (int i = 0; i < 960; i++) {
                Position sp = Chess960.getStartPosition(i);
                if (chess960Matches(sp, wkSqi, bkSqi, wkrSqi, wqrSqi, bkrSqi, bqrSqi)) {
                    log.debug("Using Chess960 starting position number " + i + " in game " + gameId);
                    return i;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        // Can't do so much here; return the original encoded (faulty) position
        return originalSpNo;
    }

    private boolean chess960Matches(Position sp, int wkSqi, int bkSqi, int wkrSqi, int wqrSqi, int bkrSqi, int bqrSqi) {
        return sp.stoneAt(wkSqi) == Stone.WHITE_KING && sp.stoneAt(bkSqi) == Stone.BLACK_KING
                && sp.stoneAt(wkrSqi) == Stone.WHITE_ROOK && sp.stoneAt(wqrSqi) == Stone.WHITE_ROOK
                && sp.stoneAt(bkrSqi) == Stone.BLACK_ROOK && sp.stoneAt(bqrSqi) == Stone.BLACK_ROOK;
    }

    public ByteBuffer serializeInitialPosition(GameMovesModel moves, ByteBuffer buf, boolean addExtraInfo) {
        Position position = moves.root().position();
        int mark = buf.position() + 28;

        buf.put((byte) 1);

        int b = position.getEnPassantCol() + 1;
        if (position.playerToMove() == Player.BLACK) b += 16;
        buf.put((byte) b);

        b = 0;
        if (position.isCastles(Castles.WHITE_LONG_CASTLE)) b += 1;
        if (position.isCastles(Castles.WHITE_SHORT_CASTLE)) b += 2;
        if (position.isCastles(Castles.BLACK_LONG_CASTLE)) b += 4;
        if (position.isCastles(Castles.BLACK_SHORT_CASTLE)) b += 8;
        buf.put((byte) b);

        b = Chess.plyToMoveNumber(moves.root().ply());
        if (b > 255) b = 255;
        buf.put((byte) b);

        try (ByteBufferBitWriter byteBufferBitWriter = new ByteBufferBitWriter(buf)) {
            for (int i = 0; i < 64; i++) {
                Stone stone = position.stoneAt(i);
                if (stone.isNoStone()) {
                    byteBufferBitWriter.putBit(0);
                } else {
                    byteBufferBitWriter.putBit(1);
                    byteBufferBitWriter.putBit(stone.isWhite() ? 0 : 1);
                    int p = switch (stone.toPiece()) {
                        case KING -> 1;
                        case QUEEN -> 2;
                        case KNIGHT -> 3;
                        case BISHOP -> 4;
                        case ROOK -> 5;
                        case PAWN -> 6;
                        default -> 0;
                    };
                    byteBufferBitWriter.putBits(p, 3);
                }
            }
        }
        if (buf.position() > mark) {
            // Can only happen if there are more than 32 pieces on the board which we don't support
            throw new IllegalArgumentException("The initial position contains too many pieces");
        }
        buf.position(mark);
        if (addExtraInfo) {
            serializeChess960StartingPosition(buf, position.chess960StartPosition());
        }
        return buf;
    }

    private void serializeChess960StartingPosition(ByteBuffer buf, int sp) {
        ByteBufferUtil.putByte(buf, Chess960.getKingSqi(sp, Player.WHITE));
        ByteBufferUtil.putByte(buf, Chess960.getKingSqi(sp, Player.BLACK));
        ByteBufferUtil.putByte(buf, Chess960.getHRookSqi(sp, Player.WHITE));
        ByteBufferUtil.putByte(buf, Chess960.getARookSqi(sp, Player.WHITE));
        ByteBufferUtil.putByte(buf, Chess960.getHRookSqi(sp, Player.BLACK));
        ByteBufferUtil.putByte(buf, Chess960.getARookSqi(sp, Player.BLACK));
        ByteBufferUtil.putShortB(buf, sp);
    }
}
