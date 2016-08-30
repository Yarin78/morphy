package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.EnumSet;

public final class MovesSerializer {
    private static final Logger log = LoggerFactory.getLogger(MovesSerializer.class);

    private static final int FLAG0_ENCRYPTION_KEY = 0;
    private static final int FLAG1_ENCRYPTION_KEY = 2;
    private static final int FLAG2_ENCRYPTION_KEY = 4;
    private static final int FLAG3_ENCRYPTION_KEY = 6;
    private static final int FLAG4_ENCRYPTION_KEY = 10;
    private static final int FLAG5_ENCRYPTION_KEY = 12;
    private static final int FLAG6_ENCRYPTION_KEY = 14;
    private static final int FLAG7_ENCRYPTION_KEY = 16;

    private MovesSerializer() {}

    public static ByteBuffer serializeMoves(@NonNull GameMovesModel model) {
        // TODO: All serialize method should probably not return a ByteBuffer but write to an existing one
        return serializeMoves(model, 0);
    }

    static ByteBuffer serializeMoves(@NonNull GameMovesModel model, int encodingMode) {
        if (encodingMode < 0 || encodingMode >= 8) {
            throw new IllegalArgumentException("encodingMode must be between 0 and 7");
        }

        ByteBuffer buf = ByteBuffer.allocate(16384);

        int flags = encodingMode;

        if (model.isSetupPosition()) {
            flags |= 0x40;
        }

        buf.put((byte) flags);
        // We don't know the size yet so skip ahead 3 bytes
        buf.position(4);

        if (model.isSetupPosition()) {
            serializeInitialPosition(model, buf);
        }

        if (encodingMode != 0) {
            // This is non-standard
            log.info("Encoding game with encoding mode " + encodingMode);
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
    public static GameMovesModel deserializeMoves(ByteBuffer buf) throws ChessBaseMoveDecodingException {
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

            log.warn("Bit 7 set in first byte in move data");
        }
        boolean setupPosition = (flags & 0x40) > 0;
        boolean illegalPosition = flags == 0x7F; // Not sure this have to be treated in a special way?

        if (setupPosition) {
            model = parseInitialPosition(moveBuf);
        } else {
            model = new GameMovesModel();
        }

        int encodingMode = flags & 0x3F;

        if (encodingMode != 0) {
            log.warn(String.format("Deserializing game with unusual encoding: %02X", encodingMode));
        }

        if (illegalPosition) {
            // Seems like there are some bugs in ChessBase 13 for this as well
            // You can enter illegal moves, but they won't be saved properly!?
            log.warn("Illegal positions are not supported");
            log.debug("Move data is " + CBUtil.toHexString(moveBuf));
            return model;
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Parsing move data at moveData pos %s with %d bytes left",
                    model.root().position().toString("|"), moveBuf.limit() - moveBuf.position()));
        }


        MoveEncoder moveEncoder = getMoveEncoder(encodingMode);

        try {
            moveEncoder.decode(moveBuf, model);
        } catch (ChessBaseMoveDecodingException e) {
            // TODO: Add tests for this
            e.setModel(model);
            log.warn("Parsed illegal move; aborting. Moves parsed so far: " + model.toString());
        } catch (BufferUnderflowException e) {
            log.warn("Move data ended abruptly. Moves parsed so far: " + model.toString());
            throw new ChessBaseMoveDecodingException("Move data ended abruptly", e, model);
        }

        return model;
    }

    private static MoveEncoder getMoveEncoder(int encodingMode) {
        switch (encodingMode) {
            case 0x00 :
                return new CompactMoveEncoder(FLAG0_ENCRYPTION_KEY, true, false);
            case 0x01:
                return new SimpleMoveEncoder(FLAG1_ENCRYPTION_KEY, true, false);
            case 0x02 :
                return new CompactMoveEncoder(FLAG2_ENCRYPTION_KEY, true, true);
            case 0x03 :
                return new SimpleMoveEncoder(FLAG3_ENCRYPTION_KEY, true, true);
            case 0x04 :
                return new CompactMoveEncoder(FLAG4_ENCRYPTION_KEY, false, false);
            case 0x05 :
                return new SimpleMoveEncoder(FLAG5_ENCRYPTION_KEY, false, false);
            case 0x06 :
                return new CompactMoveEncoder(FLAG6_ENCRYPTION_KEY, false, true);
            case 0x07 :
                return new SimpleMoveEncoder(FLAG7_ENCRYPTION_KEY, false, true);
            default :
                throw new UnsupportedOperationException(String.format("Unsupported move encoder: %02X", encodingMode));
        }
    }

    public static GameMovesModel parseInitialPosition(ByteBuffer buf)
            throws ChessBaseMoveDecodingException {
        int endPosition = buf.position() + 28;
        int b = ByteBufferUtil.getUnsignedByte(buf);
        if (b != 1) {
            log.warn(String.format("Unexpected first byte in setup position: %02X", b));
        }
        b = ByteBufferUtil.getUnsignedByte(buf);
        int epFile = (b & 15) - 1;
        Player sideToMove = (b & 16) == 0 ? Player.WHITE : Player.BLACK;
        b = ByteBufferUtil.getUnsignedByte(buf);
        EnumSet<Castles> castles = EnumSet.noneOf(Castles.class);
        if ((b & 1) > 0) castles.add(Castles.WHITE_LONG_CASTLE);
        if ((b & 2) > 0) castles.add(Castles.WHITE_SHORT_CASTLE);
        if ((b & 4) > 0) castles.add(Castles.BLACK_LONG_CASTLE);
        if ((b & 8) > 0) castles.add(Castles.BLACK_SHORT_CASTLE);
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
                switch (pieceNo) {
                    case 1 : piece = Piece.KING; break;
                    case 2 : piece = Piece.QUEEN; break;
                    case 3 : piece = Piece.KNIGHT; break;
                    case 4 : piece = Piece.BISHOP; break;
                    case 5 : piece = Piece.ROOK; break;
                    case 6 : piece = Piece.PAWN; break;
                    default :
                        throw new ChessBaseMoveDecodingException("Invalid piece in setup position: " + pieceNo);
                }
                stones[i] = piece.toStone(color);
            }
        }

        buf.position(endPosition);

        Position startPosition = new Position(stones, sideToMove, castles, epFile);
        return new GameMovesModel(startPosition, moveNumber);
    }

    public static ByteBuffer serializeInitialPosition(GameMovesModel moves, ByteBuffer buf) {
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

        ByteBufferBitWriter byteBufferBitWriter = new ByteBufferBitWriter(buf);

        for (int i = 0; i < 64; i++) {
            Stone stone = position.stoneAt(i);
            if (stone.isNoStone()) {
                byteBufferBitWriter.putBit(0);
            } else {
                byteBufferBitWriter.putBit(1);
                byteBufferBitWriter.putBit(stone.isWhite() ? 0 : 1);
                int p = 0;
                switch (stone.toPiece()) {
                    case KING:   p = 1; break;
                    case QUEEN:  p = 2; break;
                    case KNIGHT: p = 3; break;
                    case BISHOP: p = 4; break;
                    case ROOK:   p = 5; break;
                    case PAWN:   p = 6; break;
                }
                byteBufferBitWriter.putBits(p, 3);
            }
        }
        if (buf.position() > mark) {
            // Can only happen if there are more than 32 pieces on the board which we don't support
            throw new IllegalArgumentException("The initial position contains too many pieces");
        }
        buf.position(mark);
        return buf;
    }
}
