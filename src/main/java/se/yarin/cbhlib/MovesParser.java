package se.yarin.cbhlib;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Stack;

public final class MovesParser {
    private static final Logger log = LoggerFactory.getLogger(MovesParser.class);

    private MovesParser() {}

    // Enabling this will integrity check the two parallel position structures after parsing every move
    static boolean INTEGRITY_CHECKS_ENABLED = false;

    @AllArgsConstructor
    private static class OpcodeRange {
        private Piece piece;
        private int opcode, pieceNo;
    }

    @AllArgsConstructor
    private static class OpcodeMap {
        private Piece piece;
        private int ofs, pieceNo;
    }

    private static final int OPCODE_NULLMOVE = 0;
    private static final int OPCODE_TWO_BYTES = 235;
    private static final int OPCODE_IGNORE = 236;
    private static final int OPCODE_START_VARIANT = 254;
    private static final int OPCODE_END_VARIANT = 255;

    private static final OpcodeRange[] opcodeRanges = new OpcodeRange[] {
            new OpcodeRange(Piece.KING,     1, 0),
            new OpcodeRange(Piece.QUEEN,   11, 0),
            new OpcodeRange(Piece.ROOK,    39, 0),
            new OpcodeRange(Piece.ROOK,    53, 1),
            new OpcodeRange(Piece.BISHOP,  67, 0),
            new OpcodeRange(Piece.BISHOP,  81, 1),
            new OpcodeRange(Piece.KNIGHT,  95, 0),
            new OpcodeRange(Piece.KNIGHT, 103, 1),
            new OpcodeRange(Piece.PAWN,   111, 0),
            new OpcodeRange(Piece.QUEEN,  143, 1),
            new OpcodeRange(Piece.QUEEN,  171, 2),
            new OpcodeRange(Piece.ROOK,   199, 2),
            new OpcodeRange(Piece.BISHOP, 213, 2),
            new OpcodeRange(Piece.KNIGHT, 227, 2),
            new OpcodeRange(Piece.NO_PIECE, OPCODE_TWO_BYTES, 0)
    };

    private static final OpcodeMap[] opcodeMap = new OpcodeMap[256];

    private static final int[] kingDir = new int[] { 1, 9, 8, 7, -1, -9, -8, -7};
    private static final int[] knightDir = new int[] { 17, 10, -6, -15, -17, -10, 6, 15 };

    private static final byte[] encryptMap = new byte[256];
    private static final int[] decryptMap = new int[256];

    static {
        // This encryption map occurs in in CBase9.exe starting at position 0x7AE4A8
        // Hence it has probably been randomly generated, so no formula exist.
        // In CBase10.exe, it starts at 0x9D6530
        InputStream stream = MovesParser.class.getResourceAsStream("moveEncryptionKey.bin");
        try {
            stream.read(encryptMap);
            for (int i = 0; i < 256; i++) {
                decryptMap[(encryptMap[i] + 256) % 256] = i;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource moveEncryptionKey.bin");
        }

        // Map the opcode ranges into lookup tables for each opcode
        for (int op = 1, ofs = 0, rangeNo = 0; op < 256; op++, ofs++) {
            if (rangeNo+1 < opcodeRanges.length && opcodeRanges[rangeNo+1].opcode == op) {
                rangeNo++;
                ofs=0;
            }
            opcodeMap[op] = new OpcodeMap(opcodeRanges[rangeNo].piece, ofs, opcodeRanges[rangeNo].pieceNo);
        }
    }

    public static GameMovesModel parseMoveData(ByteBuffer buf)
            throws ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        GameMovesModel model;
        boolean encoded;
        try {
            // TODO: First byte seems to be flags only, and 7F if illegal position!?
            int flags = ByteBufferUtil.getUnsignedByte(buf);
            int moveSize = ByteBufferUtil.getUnsigned24BitB(buf);
            boolean illegalPosition = flags == 0x7F;
            boolean setupPosition = (flags & 0x40) > 0;
            encoded = (flags & 0x80) == 0; // Might mean something else

            if (setupPosition) {
                model = parseInitialPosition(buf);
            } else {
                model = new GameMovesModel();
            }

            if ((flags & ~0x40) != 0) {
                log.warn(String.format("Strange moves encoding. Flags: %02X, move size %d, move data %s", flags, moveSize, CBUtil.toHexString(buf)));
            /*

            Flag 7F: Illegal position

            Distribution in Mega Database 2016:
            INFO  CheckMoveEncodingFlags - Flag 00: 6470410 games: 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23...
            INFO  CheckMoveEncodingFlags - Flag 04: 2 games: 2017678, 2769933
            INFO  CheckMoveEncodingFlags - Flag 05: 2 games: 2870325, 3789643
            INFO  CheckMoveEncodingFlags - Flag 40: 1308 games: 100, 103, 108, 109, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 137...
            INFO  CheckMoveEncodingFlags - Flag 4A: 14 games: 3730250, 3730251, 3730252, 3730253, 3730254, 3730255, 3730256, 3730257, 6197425, 6197426, 6197427, 6197428, 6197429, 6197430
            INFO  CheckMoveEncodingFlags - Flag 80: 1 games: 6161154
            INFO  CheckMoveEncodingFlags - Flag 85: 1 games: 2017673

            Distribution in Mega Database 2014:
            INFO  CheckMoveEncodingFlags - Flag 00: 5790403 games: 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23...
            INFO  CheckMoveEncodingFlags - Flag 04: 2 games: 1994813, 2741165
            INFO  CheckMoveEncodingFlags - Flag 05: 2 games: 2840226, 3754063
            INFO  CheckMoveEncodingFlags - Flag 40: 1163 games: 100, 103, 108, 109, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 137...
            INFO  CheckMoveEncodingFlags - Flag 4A: 8 games: 3695376, 3695377, 3695378, 3695379, 3695380, 3695381, 3695382, 3695383
            INFO  CheckMoveEncodingFlags - Flag 85: 1 games: 1994808

            Distribution in Mega Database 2009:
            INFO  CheckMoveEncodingFlags - Flag 00: 4124957 games: 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23...
            INFO  CheckMoveEncodingFlags - Flag 40: 952 games: 105, 108, 112, 113, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 141...
            INFO  CheckMoveEncodingFlags - Flag 04: 2 games: 1947639, 2688349
            INFO  CheckMoveEncodingFlags - Flag 05: 2 games: 2786731, 3680063
            INFO  CheckMoveEncodingFlags - Flag 85: 1 games: 1947634
            INFO  CheckMoveEncodingFlags - Flag 4A: 8 games: 3626201, 3626202, 3626203, 3626204, 3626205, 3626206, 3626207, 3626208


            Game 94183 in Garry Kasparov - Queens Gambit has flag 05
            */
                throw new ChessBaseUnsupportedException(String.format("Unsupported move data format: %02X", flags));
            }

            if (illegalPosition) {
                // Seems like there are some bugs in ChessBase 13 for this as well
                // You can enter illegal moves, but they won't be saved properly!?
                log.warn("Illegal positions are not supported");
                log.debug("Move data is " + CBUtil.toHexString(buf));
                return model;
            }

            if (log.isDebugEnabled()) {
                log.debug(String.format("Parsing move data at moveData pos %s with %d bytes left",
                        model.root().position().toString("|"), buf.limit() - buf.position()));
            }

        } catch (BufferUnderflowException e) {
            throw new ChessBaseInvalidDataException("Moves data header ended abruptly");
        }

        StonePositions piecePosition = StonePositions.fromPosition(model.root().position());

        int modifier = 0;
        Stack<GameMovesModel.Node> nodeStack = new Stack<>();
        Stack<StonePositions> piecePositionStack = new Stack<>();
        GameMovesModel.Node currentNode = model.root();

        try {
            while (true) {
                int opcode = ByteBufferUtil.getUnsignedByte(buf);
                if (encoded) {
                    opcode = decryptMap[(opcode + modifier) % 256];
                }

                if (opcode == OPCODE_IGNORE) {
                    // Not sure what this opcode does. Just ignoring it seems works fine.
                    // Chessbase 9 removes this opcode when replacing the game.
                    continue;
                }
                if (opcode > OPCODE_IGNORE && opcode < OPCODE_START_VARIANT) {
//                throw new ChessBaseInvalidDataException(String.format("Unknown opcode in game data: 0x%02X", opcode));
                    log.warn(String.format("Unknown opcode in game data, ignoring: 0x%02X", opcode));
                    continue;
                }
                if (opcode == OPCODE_START_VARIANT) {
                    nodeStack.push(currentNode);
                    piecePositionStack.push(piecePosition);
                    continue;
                }
                if (opcode == OPCODE_END_VARIANT) {
                    // Also used as end of game
                    if (nodeStack.size() == 0)
                        break;

                    currentNode = nodeStack.pop();
                    piecePosition = piecePositionStack.pop();
                    continue;
                }

                // Decode the move
                Move move;
                if (opcode == OPCODE_TWO_BYTES) {
                    // In rare cases a move has to be encoded as two bytes
                    // Typically pawn promotions or if a player has more than 3 pieces of some kind
                    int msb = ByteBufferUtil.getUnsignedByte(buf);
                    int lsb = ByteBufferUtil.getUnsignedByte(buf);
                    if (encoded) {
                        msb = decryptMap[(msb + modifier) % 256];
                        lsb = decryptMap[(lsb + modifier) % 256];
                    }
                    opcode = msb * 256 + lsb;
                    move = decodeTwoByteMove(opcode, currentNode.position());
                } else {
                    move = decodeSingleByteMove(opcode, piecePosition, currentNode.position());
                }

                if (log.isDebugEnabled()) {
                    log.debug("Parsed move " + move.toLAN());
                }

                // Update position of the moved piece
                piecePosition = piecePosition.doMove(move);
                currentNode = currentNode.addMove(move);

                if (INTEGRITY_CHECKS_ENABLED) {
                    piecePosition.validate(currentNode.position());
                }

                modifier = (modifier + 255) % 256;
            }
        } catch (BufferUnderflowException e) {
            log.warn("Move data ended abruptly. Moves parsed so far: " + model.toString());
        }

        return model;
    }

    private static Move decodeSingleByteMove(int opcode, StonePositions stonePositions, Position position)
            throws ChessBaseInvalidDataException {
        Player playerToMove = position.playerToMove();

        if (opcode == OPCODE_NULLMOVE) {
            return Move.nullMove(position);
        }
        if (opcode < OPCODE_NULLMOVE || opcode > OPCODE_TWO_BYTES) {
            throw new ChessBaseInvalidDataException("Invalid opcode: " + opcode);
        }

        Piece piece = opcodeMap[opcode].piece;
        int stoneNo = opcodeMap[opcode].pieceNo;
        int ofs = opcodeMap[opcode].ofs;

        if (piece == Piece.PAWN) {
            stoneNo = ofs / 4;
            ofs %= 4;
        }

        int sqi = stonePositions.getSqi(piece.toStone(playerToMove), stoneNo);
        if (sqi < 0) {
            throw new ChessBaseInvalidDataException(
                    String.format("No piece coordinate for %s %s number %d", playerToMove, piece, stoneNo));
        }

        if (piece == Piece.KING) {
            int backRank = playerToMove == Player.WHITE ? 0 : 7;
            if (ofs == 8) return new Move(position, 4, backRank, 6, backRank);
            if (ofs == 9) return new Move(position, 4, backRank, 2, backRank);
            return new Move(position, sqi, sqi + kingDir[ofs]);
        }

        if (piece == Piece.BISHOP || piece == Piece.ROOK || piece == Piece.QUEEN) {
            int px = Chess.sqiToCol(sqi), py = Chess.sqiToRow(sqi);
            int dir = ofs / 7, stride = ofs % 7 + 1;
            switch (dir + (piece == Piece.BISHOP ? 2 : 0)) {
                case 0: return new Move(position, sqi, Chess.coorToSqi(px, (py + stride) % 8));
                case 1: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, py));
                case 2: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, (py + stride) % 8));
                case 3: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, (py + 8 - stride) % 8));
            }
        }

        if (piece == Piece.KNIGHT) {
            return new Move(position, sqi, sqi + knightDir[ofs]);
        }

        if (piece == Piece.PAWN) {
            int dir = playerToMove == Player.WHITE ? 1 : -1;
            switch (ofs) {
                case 0: return new Move(position, sqi, sqi + dir);
                case 1: return new Move(position, sqi, sqi + dir * 2);
                case 2: return new Move(position, sqi, sqi + dir * 9);
                case 3: return new Move(position, sqi, sqi - dir * 7);
            }
        }

        throw new ChessBaseInvalidDataException("Invalid opcode: " + opcode);
    }

    private static Move decodeTwoByteMove(int opcode, Position board)
            throws ChessBaseInvalidDataException {
        Player playerToMove = board.playerToMove();
        int fromSqi = opcode % 64, toSqi = (opcode / 64) % 64;
        Piece piece = board.stoneAt(fromSqi).toPiece();
        if (piece == Piece.NO_PIECE) {
            throw new ChessBaseInvalidDataException("No piece at source position");
        }

        if (piece != Piece.PAWN) {
            return new Move(board, fromSqi, toSqi);
        }

        // This should be a pawn promotion, weird otherwise
        int toRow = Chess.sqiToRow(toSqi);
        if (toRow > 0 && toRow < 7) {
            throw new ChessBaseInvalidDataException("Double bytes used for non-promotion pawn move");
        }

        Piece promotedPiece;
        switch (opcode / 4096) {
            case 0: promotedPiece = Piece.QUEEN;  break;
            case 1: promotedPiece = Piece.ROOK;   break;
            case 2: promotedPiece = Piece.BISHOP; break;
            case 3: promotedPiece = Piece.KNIGHT; break;
            default:
                throw new ChessBaseInvalidDataException("Illegal promoted piece");
        }

        return new Move(board, fromSqi, toSqi, promotedPiece.toStone(playerToMove));
    }

    public static GameMovesModel parseInitialPosition(ByteBuffer buf)
            throws ChessBaseInvalidDataException {
        int endPosition = buf.position() + 28;
        int b = ByteBufferUtil.getUnsignedByte(buf);
        if (b != 1) {
            throw new ChessBaseInvalidDataException("First byte in setup position should be 1");
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
                switch (byteBufferBitReader.getInt(3)) {
                    case 1 : piece = Piece.KING; break;
                    case 2 : piece = Piece.QUEEN; break;
                    case 3 : piece = Piece.KNIGHT; break;
                    case 4 : piece = Piece.BISHOP; break;
                    case 5 : piece = Piece.ROOK; break;
                    case 6 : piece = Piece.PAWN; break;
                    default :
                        throw new ChessBaseInvalidDataException("Invalid piece in setup position");
                }
                stones[i] = piece.toStone(color);
            }
        }

        buf.position(endPosition);

        Position startPosition = new Position(stones, sideToMove, castles, epFile);
        return new GameMovesModel(startPosition, Chess.moveNumberToPly(moveNumber, sideToMove));
    }
}
