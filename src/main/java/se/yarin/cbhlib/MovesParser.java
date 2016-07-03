package se.yarin.cbhlib;

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

    private static final int OPCODE_NULLMOVE = 0;
    private static final int OPCODE_KING = 1;
    private static final int OPCODE_KING_OO = 9;
    private static final int OPCODE_KING_OOO = 10;
    private static final int OPCODE_QUEEN_1 = 11;
    private static final int OPCODE_ROOK_1 = 39;
    private static final int OPCODE_ROOK_2 = 53;
    private static final int OPCODE_BISHOP_1 = 67;
    private static final int OPCODE_BISHOP_2 = 81;
    private static final int OPCODE_KNIGHT_1 = 95;
    private static final int OPCODE_KNIGHT_2 = 103;
    private static final int OPCODE_PAWNS = 111;
    private static final int OPCODE_QUEEN_2 = 143;
    private static final int OPCODE_QUEEN_3 = 171;
    private static final int OPCODE_ROOK_3 = 199;
    private static final int OPCODE_BISHOP_3 = 213;
    private static final int OPCODE_KNIGHT_3 = 227;
    private static final int OPCODE_TWO_BYTES = 235;
    private static final int OPCODE_IGNORE = 236;
    private static final int OPCODE_START_VARIANT = 254;
    private static final int OPCODE_END_VARIANT = 255;

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

        log.debug(String.format("Parsing move data at moveData pos %s with %d bytes left",
                model.root().position().toString(), buf.limit() - buf.position()));
        StonePositions piecePosition = StonePositions.fromPosition(model.root().position());

        int modifier = 0;
        Stack<GameMovesModel.Node> nodeStack = new Stack<>();
        Stack<StonePositions> piecePositionStack = new Stack<>();
        GameMovesModel.Node currentNode = model.root();

        while (true) {
            int data = ByteBufferUtil.getUnsignedByte(buf);
            if (encoded) {
                data = decryptMap[(data + modifier) % 256];
            }

            if (data == OPCODE_IGNORE) {
                // Not sure what this opcode do. Just ignoring it seems works fine.
                // Chessbase 9 removes this opcode when replacing the game.
                continue;
            }
            if (data > OPCODE_IGNORE && data < OPCODE_START_VARIANT) {
                throw new ChessBaseInvalidDataException(String.format("Unknown opcode in game data: 0x%02X", data));
            }
            if (data == OPCODE_START_VARIANT) {
                nodeStack.push(currentNode);
                piecePositionStack.push(piecePosition);
                continue;
            }
            if (data == OPCODE_END_VARIANT) {
                // Also used as end of game
                if (nodeStack.size() == 0)
                    break;

                currentNode = nodeStack.pop();
                piecePosition = piecePositionStack.pop();
                continue;
            }

            // Decode the move
            Move move;
            if (data == OPCODE_TWO_BYTES) {
                // In rare cases a move has to be encoded as two bytes
                // Typically pawn promotions or if a player has more than 3 pieces of some kind
                int msb = ByteBufferUtil.getUnsignedByte(buf);
                int lsb = ByteBufferUtil.getUnsignedByte(buf);
                if (encoded) {
                    msb = decryptMap[(msb + modifier) % 256];
                    lsb = decryptMap[(lsb + modifier) % 256];
                }
                int word = msb * 256 + lsb;
                move = decodeTwoByteMove(word, currentNode.position());
            } else {
                move = decodeSingleByteMove(data, piecePosition, currentNode.position());
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

    private static Move decodeSingleByteMove(int data, StonePositions stonePositions, Position position)
            throws ChessBaseInvalidDataException {
        Player playerToMove = position.playerToMove();

        if (data == OPCODE_NULLMOVE) {
            return Move.nullMove(position);
        }

        if (data < OPCODE_QUEEN_1) {
            // King move
            Stone kingStone = Piece.KING.toStone(playerToMove);
            int backRank = playerToMove == Player.WHITE ? 0 : 7;
            if (data == OPCODE_KING_OO)
                return new Move(position, 4, backRank, 6, backRank);
            if (data == OPCODE_KING_OOO)
                return new Move(position, 4, backRank, 2, backRank);
            int dir = data - OPCODE_KING;
            int ksqi = stonePositions.getSqi(kingStone, 0);
            return new Move(position, ksqi, ksqi + kingDir[dir]);
        }

        // TODO: Eliminate these range checks and multitude of if-statements somehow
        // But first add tests that uses all three pieces of each type
        if ((data < OPCODE_KNIGHT_1) || (data >= OPCODE_QUEEN_2 && data < OPCODE_KNIGHT_3)) {
            // Bishop, rook or queen move
            Piece pieceType;
            int stoneNo;
            if (data >= OPCODE_BISHOP_3) {
                stoneNo = 2;
                data -= OPCODE_BISHOP_3;
                pieceType = Piece.BISHOP;
            } else if (data >= OPCODE_ROOK_3) {
                stoneNo = 2;
                data -= OPCODE_ROOK_3;
                pieceType = Piece.ROOK;
            } else if (data >= OPCODE_QUEEN_3) {
                stoneNo = 2;
                data -= OPCODE_QUEEN_3;
                pieceType = Piece.QUEEN;
            } else if (data >= OPCODE_QUEEN_2) {
                stoneNo = 1;
                data -= OPCODE_QUEEN_2;
                pieceType = Piece.QUEEN;
            } else if (data >= OPCODE_BISHOP_2) {
                stoneNo = 1;
                data -= OPCODE_BISHOP_2;
                pieceType = Piece.BISHOP;
            } else if (data >= OPCODE_BISHOP_1) {
                stoneNo = 0;
                data -= OPCODE_BISHOP_1;
                pieceType = Piece.BISHOP;
            } else if (data >= OPCODE_ROOK_2) {
                stoneNo = 1;
                data -= OPCODE_ROOK_2;
                pieceType = Piece.ROOK;
            } else if (data >= OPCODE_ROOK_1) {
                stoneNo = 0;
                data -= OPCODE_ROOK_1;
                pieceType = Piece.ROOK;
            } else if (data >= OPCODE_QUEEN_1) {
                stoneNo = 0;
                data -= OPCODE_QUEEN_1;
                pieceType = Piece.QUEEN;
            } else {
                throw new ChessBaseInvalidDataException("Opcode error");
            }
            Stone stone = pieceType.toStone(playerToMove);
            int sqi = stonePositions.getSqi(stone, stoneNo);
            if (sqi < 0) {
                throw new ChessBaseInvalidDataException(
                        String.format("No piece coordinate for %s %s number %d", playerToMove, pieceType, stoneNo));
            }
            int px = Chess.sqiToCol(sqi), py = Chess.sqiToRow(sqi);
            int dir = data / 7, stride = data % 7 + 1;
            switch (dir + (pieceType == Piece.BISHOP ? 2 : 0)) {
                case 0: return new Move(position, sqi, Chess.coorToSqi(px, (py + stride) % 8));
                case 1: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, py));
                case 2: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, (py + stride) % 8));
                case 3: return new Move(position, sqi, Chess.coorToSqi((px + stride) % 8, (py + 8 - stride) % 8));
            }
        } else if ((data < OPCODE_PAWNS) || (data >= OPCODE_KNIGHT_3 && data < OPCODE_TWO_BYTES)) {
            int stoneNo;
            if (data >= OPCODE_KNIGHT_3) {
                stoneNo = 2;
                data -= OPCODE_KNIGHT_3;
            } else if (data >= OPCODE_KNIGHT_2) {
                stoneNo = 1;
                data -= OPCODE_KNIGHT_2;
            } else {
                stoneNo = 0;
                data -= OPCODE_KNIGHT_1;
            }
            Stone stone = Piece.KNIGHT.toStone(playerToMove);
            int sqi = stonePositions.getSqi(stone, stoneNo);
            if (sqi < 0) {
                throw new ChessBaseInvalidDataException(
                        String.format("No piece coordinate for %s number %d", stone.toString(), stoneNo));
            }
            return new Move(position, sqi, sqi + knightDir[data]);
        } else if (data >= OPCODE_PAWNS && data < OPCODE_QUEEN_2) {
            data -= OPCODE_PAWNS;
            int stoneNo = data / 4;
            Stone stone = Piece.PAWN.toStone(playerToMove);
            int pawnMove = data % 4;
            int sqi = stonePositions.getSqi(stone, stoneNo);
            if (sqi < 0) {
                throw new ChessBaseInvalidDataException(
                        String.format("No piece coordinate for %s number %d", stone.toString(), stoneNo));
            }
            int dir = playerToMove == Player.WHITE ? 1 : -1;
            switch (pawnMove) {
                case 0: return new Move(position, sqi, sqi + dir);
                case 1: return new Move(position, sqi, sqi + dir * 2);
                case 2: return new Move(position, sqi, sqi + dir * 9);
                case 3: return new Move(position, sqi, sqi - dir * 7);
            }
        }
        throw new ChessBaseInvalidDataException("Opcode error");
    }

    private static Move decodeTwoByteMove(int data, Position board)
            throws ChessBaseInvalidDataException {
        Player playerToMove = board.playerToMove();
        int fromSqi = data % 64, toSqi = (data / 64) % 64;
        Piece pieceType = board.stoneAt(fromSqi).toPiece();
        if (pieceType == Piece.NO_PIECE) {
            throw new ChessBaseInvalidDataException("No piece at source position");
        }

        if (pieceType != Piece.PAWN) {
            return new Move(board, fromSqi, toSqi);
        }

        // This should be a pawn promotion, weird otherwise
        int toRow = Chess.sqiToRow(toSqi);
        if (toRow > 0 && toRow < 7) {
            throw new ChessBaseInvalidDataException("Double bytes used for non-promotion pawn move");
        }

        Piece promotedPiece;
        switch (data / 4096) {
            case 0: promotedPiece = Piece.QUEEN;  break;
            case 1: promotedPiece = Piece.ROOK;   break;
            case 2: promotedPiece = Piece.BISHOP; break;
            case 3: promotedPiece = Piece.KNIGHT; break;
            default:
                throw new ChessBaseInvalidDataException("Illegal promoted piece");
        }
        Stone promotedStone = promotedPiece.toStone(playerToMove);
        return new Move(board, fromSqi, toSqi, promotedStone);
    }

    private static GameMovesModel parseInitialPosition(ByteBuffer buf)
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
