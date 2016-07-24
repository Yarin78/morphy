package se.yarin.cbhlib;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;

import java.io.IOException;
import java.io.InputStream;
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
            throws ChessBaseInvalidDataException {

        int gameSize = ByteBufferUtil.getIntB(buf);
        boolean setupPosition = (gameSize & 0x40000000) > 0;
        boolean encoded = (gameSize & 0x80000000) == 0; // Might mean something else
        gameSize &= 0x3FFFFFFF;

        GameMovesModel model;
        if (setupPosition) {
            model = parseInitialPosition(buf);
        } else {
            model = new GameMovesModel();
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Parsing move data at moveData pos %s with %d bytes left",
                    model.root().position().toString("|"), buf.limit() - buf.position()));
        }

        StonePositions piecePosition = StonePositions.fromPosition(model.root().position());

        int modifier = 0;
        Stack<GameMovesModel.Node> nodeStack = new Stack<>();
        Stack<StonePositions> piecePositionStack = new Stack<>();
        GameMovesModel.Node currentNode = model.root();

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
                throw new ChessBaseInvalidDataException(String.format("Unknown opcode in game data: 0x%02X", opcode));
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
