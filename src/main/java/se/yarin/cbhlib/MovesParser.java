package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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

        // All positions in the game in a flat list, excluding the initial position
        ArrayList<GameMovesModel.Node> nodesInOrder = new ArrayList<>();

        while (true) {
            int data = ByteBufferUtil.getUnsignedByte(buf);
            if (encoded) {
                data = decryptMap[(data + modifier) % 256];
            }

            if (data == OPCODE_IGNORE) {
                // Not sure what this opcode do. Just ignoring it seems works fine.
                // Chessbase 9 removes this opcode when replacing the game.
                continue;
            } else if (data > OPCODE_IGNORE && data < OPCODE_START_VARIANT) {
                throw new ChessBaseInvalidDataException(String.format("Unknown opcode in game data: 0x%02X", data));
            } else if (data == OPCODE_START_VARIANT) {
                nodeStack.push(currentNode);
                piecePositionStack.push(piecePosition);
                continue;
            } else if (data == OPCODE_END_VARIANT) {
                // Also used as end of game
                if (nodeStack.size() == 0)
                    break;

                currentNode = nodeStack.pop();
                piecePosition = piecePositionStack.pop();
                continue;
            }

            // Decode the move and update position
            Position board = currentNode.position();
            Player playerToMove = board.playerToMove();
            Move move;
            Piece pieceType;
            Stone stone;

            if (data < OPCODE_TWO_BYTES) {
                int stoneNo;
                if (data == OPCODE_NULLMOVE) {
                    move = Move.nullMove(board);
                    pieceType = Piece.NO_PIECE;
                    stone = Stone.NO_STONE;
                    stoneNo = 0;
                } else if (data < OPCODE_QUEEN_1) {
                    stoneNo = 0;
                    pieceType = Piece.KING;
                    stone = Piece.KING.toStone(playerToMove);
                    int backRank = playerToMove == Player.WHITE ? 0 : 7;
                    if (data == OPCODE_KING_OO)
                        move = new Move(board, 4, backRank, 6, backRank);
                    else if (data == OPCODE_KING_OOO)
                        move = new Move(board, 4, backRank, 2, backRank);
                    else {
                        int dir = data - OPCODE_KING;
                        int ksqi = piecePosition.getSqi(stone, 0);
                        move = new Move(board, ksqi, ksqi+kingDir[dir]);
                    }
                } else if ((data < OPCODE_KNIGHT_1) || (data >= OPCODE_QUEEN_2 && data < OPCODE_KNIGHT_3)) {
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
                    stone = pieceType.toStone(playerToMove);
                    int sqi = piecePosition.getSqi(stone, stoneNo);
                    if (sqi < 0) {
                        throw new ChessBaseInvalidDataException(
                                String.format("No piece coordinate for %s %s number %d", playerToMove, pieceType, stoneNo));
                    }
                    int px = Chess.sqiToCol(sqi), py = Chess.sqiToRow(sqi);
                    int dir = data / 7, stride = data % 7 + 1;
                    switch (dir + (pieceType == Piece.BISHOP ? 2 : 0)) {
                        case 0:
                            move = new Move(board, sqi, Chess.coorToSqi(px, (py + stride) % 8));
                            break;
                        case 1:
                            move = new Move(board, sqi, Chess.coorToSqi((px + stride) % 8, py));
                            break;
                        case 2:
                            move = new Move(board, sqi, Chess.coorToSqi((px + stride) % 8, (py + stride) % 8));
                            break;
                        case 3:
                            move = new Move(board, sqi, Chess.coorToSqi((px + stride) % 8, (py + 8 - stride) % 8));
                            break;
                        default:
                            throw new ChessBaseInvalidDataException("Opcode error"); // Shouldn't happen
                    }
                } else if ((data < OPCODE_PAWNS) || (data >= OPCODE_KNIGHT_3 && data < OPCODE_TWO_BYTES)) {
                    pieceType = Piece.KNIGHT;
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
                    stone = Piece.KNIGHT.toStone(playerToMove);
                    int sqi = piecePosition.getSqi(stone, stoneNo);
                    if (sqi < 0) {
                        throw new ChessBaseInvalidDataException(
                                String.format("No piece coordinate for %s number %d", stone.toString(), stoneNo));
                    }
                    move = new Move(board, sqi, sqi+knightDir[data]);
                } else if (data >= OPCODE_PAWNS && data < OPCODE_QUEEN_2) {
                    data -= OPCODE_PAWNS;
                    stoneNo = data / 4;
                    pieceType = Piece.PAWN;
                    stone = pieceType.toStone(playerToMove);
                    int pawnMove = data % 4;
                    int sqi = piecePosition.getSqi(stone, stoneNo);
                    if (sqi < 0) {
                        throw new ChessBaseInvalidDataException(
                                String.format("No piece coordinate for %s number %d", stone.toString(), stoneNo));
                    }
                    int dir = playerToMove == Player.WHITE ? 1 : -1;
                    switch (pawnMove) {
                        case 0:
                            move = new Move(board, sqi, sqi + dir);
                            break;
                        case 1:
                            move = new Move(board, sqi, sqi + dir * 2);
                            break;
                        case 2:
                            move = new Move(board, sqi, sqi + dir * 9);
                            break;
                        case 3:
                            move = new Move(board, sqi, sqi - dir * 7);
                            break;
                        default:
                            throw new ChessBaseInvalidDataException("Opcode error"); // Shouldn't happen
                    }
                } else {
                    throw new ChessBaseInvalidDataException("Opcode error"); // Shouldn't happen
                }

                // Update position of the moved piece
                if (pieceType != Piece.NO_PIECE) {
                    piecePosition = piecePosition.move(stone, stoneNo, move.toSqi());
                }
                if (move.isCastle()) {
                    int rookX1 = move.toCol() == 6 ? 7 : 0;
                    int rookX2 = (move.fromCol() + move.toCol()) / 2;
                    int rookY = playerToMove == Player.WHITE ? 0 : 7;
                    int rookFromSqi = Chess.coorToSqi(rookX1, rookY);
                    int rookToSqi = Chess.coorToSqi(rookX2, rookY);
                    // Update rook position as well
                    Stone rook = Piece.ROOK.toStone(playerToMove);
                    for (int i = 0; i < 3; i++) {
                        if (piecePosition.getSqi(rook, i) == rookFromSqi) {
                            piecePosition = piecePosition.move(rook, i, rookToSqi);
                        }
                    }
                }
            } else if (data == OPCODE_TWO_BYTES) {
                // In rare cases a move has to be encoded as two bytes
                // Typically pawn promotions or if a player has more than 3 pieces of some kind
                int msb = ByteBufferUtil.getUnsignedByte(buf);
                int lsb = ByteBufferUtil.getUnsignedByte(buf);
                if (encoded) {
                    msb = decryptMap[(msb + modifier) % 256];
                    lsb = decryptMap[(lsb + modifier) % 256];
                }
                int word = msb * 256 + lsb;
                int fromSqi = word % 64, toSqi = (word / 64) % 64;
                pieceType = board.stoneAt(fromSqi).toPiece();
                if (pieceType == Piece.NO_PIECE) {
                    throw new ChessBaseInvalidDataException("No piece at source position");
                }
                if (pieceType == Piece.PAWN) {
                    // This should be a pawn promotion, weird otherwise
                    int toRow = Chess.sqiToRow(toSqi);
                    if (toRow > 0 && toRow < 7) {
                        throw new ChessBaseInvalidDataException("Double bytes used for non-promotion pawn move");
                    }

                    Piece promotedPiece;
                    switch (word / 4096) {
                        case 0: promotedPiece = Piece.QUEEN;  break;
                        case 1: promotedPiece = Piece.ROOK;   break;
                        case 2: promotedPiece = Piece.BISHOP; break;
                        case 3: promotedPiece = Piece.KNIGHT; break;
                        default:
                            throw new ChessBaseInvalidDataException("Illegal promoted piece");
                    }
                    Stone promotedStone = promotedPiece.toStone(playerToMove);
                    stone = Piece.PAWN.toStone(playerToMove);
                    if (piecePosition.getStoneNo(stone, fromSqi) < 0) {
                        throw new ChessBaseInvalidDataException("Board is in inconsistent state");
                    }
                    piecePosition = piecePosition.remove(stone, fromSqi);
                    piecePosition = piecePosition.add(promotedStone, toSqi);
                    move = new Move(board, fromSqi, toSqi, promotedStone);
                } else {
                    move = new Move(board, fromSqi, toSqi);
                    if (piecePosition.getStoneNo(pieceType.toStone(playerToMove), fromSqi) >= 0) {
                        // Sanity check: make sure this piece doesn't occur in piecePosition
                        throw new ChessBaseInvalidDataException("A piece was moved with double bytes even though it was among the first three");
                    }
                }
            } else
                throw new ChessBaseInvalidDataException("Opcode error"); // Shouldn't happen


            if (move.isCapture()) {
                int captureSqi = move.toSqi();
                Stone capturedStone = board.stoneAt(captureSqi);
                if (capturedStone == Stone.NO_STONE) {
                    // En passant
                    captureSqi = Chess.coorToSqi(move.toCol(), move.fromRow());
                    capturedStone = Piece.PAWN.toStone(playerToMove.otherPlayer());
                }
                piecePosition = piecePosition.remove(capturedStone, captureSqi);
            }

            if (log.isDebugEnabled()) {
                log.debug("Parsed move " + move.toLAN());
            }

            modifier = (modifier + 255) % 256;

            currentNode = currentNode.addMove(move);


            nodesInOrder.add(currentNode);

            if (INTEGRITY_CHECKS_ENABLED) {
                verifyPosition(currentNode.position(), piecePosition);
            }
        }

        return model;
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

    private static void verifyPosition(Position position, StonePositions pieces)
            throws ChessBaseInvalidDataException {
        // Verify that all pieces in pieces are accounted for in position and vice versa
        // Only for debugging!
        int piecesFound = 0, piecesOnBoard = 0;
        for (Stone stone : Stone.values()) {
            if (stone.isNoStone()) continue;
            boolean endReached = false;
            for (int sqi : pieces.getSqis(stone)) {
                if (sqi < 0) {
                    endReached = true;
                } else {
                    if (endReached && stone.toPiece() != Piece.PAWN) {
                        throw new ChessBaseInvalidDataException("Pieces not adjusted correctly");
                    }
                    if (position.stoneAt(sqi) != stone) {
                        throw new ChessBaseInvalidDataException("Board is in inconsistent state");
                    }
                    piecesFound++;
                }
            }
            if (stone.toPiece() != Piece.PAWN) {
                // There may be more than 3 pieces of this color, count them off
                for (int sqi = 0; sqi < 64; sqi++) {
                    if (stone == position.stoneAt(sqi)) {
                        if (pieces.getStoneNo(stone, sqi) < 0) {
                            piecesFound++; // This is an extra piece
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 64; i++) {
            if (!position.stoneAt(i).isNoStone()) {
                piecesOnBoard++;
            }
        }
        if (piecesFound != piecesOnBoard) {
            throw new ChessBaseInvalidDataException("Board is in inconsistent state"); // Some pieces are missing
        }
    }

}
