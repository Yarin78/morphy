package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Stack;

public final class MovesParser {
    private static final Logger log = LoggerFactory.getLogger(MovesParser.class);

    private MovesParser() {}

    private static final boolean INTEGRITY_CHECK = true;

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

    private static int[] kingDir = new int[] { 1, 9, 8, 7, -1, -9, -8, -7};
    private static int[] knightDir = new int[] { 17, 10, -6, -15, -17, -10, 6, 15 };

    // TODO: Move as resource
    // This encryption map occurs in in CBase9.exe starting at position 0x7AE4A8
    // Hence it has probably been randomly generated, so no formula exist.
    // In CBase10.exe, it starts at 0x9D6530
    private static int[] encryptMap = new int[] {
            0xAA, 0x49, 0x39, 0xD8, 0x5D, 0xC2, 0xB1, 0xB2,
            0x47, 0x76, 0xB5, 0xA5, 0xB8, 0xCB, 0x53, 0x7F,
            0x6B, 0x8D, 0x79, 0xBE, 0xEB, 0x21, 0x99, 0xD2,
            0x57, 0x4D, 0xB4, 0xBF, 0x62, 0xBD, 0x24, 0x96,
            0xA7, 0x48, 0x28, 0x6E, 0x2F, 0x5A, 0x18, 0x4E,
            0xF8, 0x43, 0xD7, 0x63, 0x9C, 0xE6, 0x2E, 0xC6,
            0x26, 0x88, 0x30, 0x61, 0x6F, 0x14, 0xA9, 0x68,
            0xEE, 0xFB, 0x77, 0xE2, 0xA6, 0x05, 0x8B, 0xA1,
            0x98, 0x32, 0x52, 0x02, 0x97, 0xE1, 0x41, 0xC3,
            0x7C, 0xE4, 0x06, 0xB7, 0x55, 0xD9, 0x2C, 0xAE,
            0x37, 0xF6, 0x3F, 0x08, 0x93, 0x73, 0x5E, 0x78,
            0x35, 0xF2, 0x6D, 0x71, 0xA2, 0xF3, 0x16, 0x58,
            0x3D, 0xFA, 0xE9, 0xBA, 0xD4, 0xDD, 0x4A, 0xC4,
            0x0E, 0xFE, 0x5F, 0x75, 0x07, 0x89, 0x34, 0x2D,
            0xC1, 0x8E, 0xF5, 0x64, 0x17, 0x70, 0xA4, 0x7B,
            0xDA, 0xE0, 0x85, 0xC5, 0x0B, 0x90, 0xF9, 0x84,
            0xFF, 0x15, 0x36, 0x09, 0x9E, 0x7D, 0xDE, 0xBB,
            0xDF, 0xBC, 0x3A, 0x12, 0x33, 0x13, 0x19, 0xE5,
            0x94, 0x50, 0x11, 0xEA, 0x31, 0x01, 0x5C, 0x95,
            0xCA, 0xD3, 0x1D, 0x7E, 0xEF, 0x44, 0x80, 0xA0,
            0x1F, 0x83, 0x00, 0x4B, 0x67, 0x20, 0x5B, 0x2A,
            0x92, 0xB6, 0x60, 0x1A, 0x42, 0x0F, 0x0D, 0xB0,
            0xD1, 0x23, 0xF0, 0x7A, 0x54, 0x4F, 0xF4, 0xA8,
            0x72, 0xE7, 0x40, 0x38, 0x59, 0x87, 0xE8, 0x6C,
            0x86, 0x04, 0xF1, 0x8C, 0xCE, 0x6A, 0xDB, 0x81,
            0x82, 0x9A, 0x1B, 0x9D, 0x0A, 0x2B, 0x8F, 0xCD,
            0xED, 0x10, 0x74, 0x69, 0xD6, 0x51, 0xB9, 0x45,
            0x3B, 0x56, 0x91, 0xFD, 0xAB, 0x66, 0x3E, 0x46,
            0xB3, 0xFC, 0xC8, 0x9B, 0xC0, 0xE3, 0xA3, 0xAC,
            0xC9, 0xEC, 0x27, 0x29, 0x9F, 0x25, 0xC7, 0xCC,
            0x65, 0x4C, 0xD5, 0x1E, 0xCF, 0x03, 0x8A, 0xAF,
            0xF7, 0xAD, 0x3C, 0xD0, 0x22, 0x1C, 0xDC, 0x0C
    };

    // Inverse of the above map
    private static int[] decryptMap;

    static {
        decryptMap = new int[256];
        for (int i = 0; i < 256; i++)
            decryptMap[encryptMap[i]] = i;
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
        PiecePositions piecePosition = PiecePositions.fromPosition(model.root().position());

        int modifier = 0;
        Stack<GameMovesModel.Node> nodeStack = new Stack<>();
        Stack<PiecePositions> piecePositionStack = new Stack<>();
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

            if (INTEGRITY_CHECK) {
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

    private static void verifyPosition(Position position, PiecePositions pieces)
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

    /**
     * An internal representation of where the pieces are on the board.
     * This can't be determined from {@link Position} as the order of the pieces is important for the move parser.
     * This class is immutable.
     */
    private static class PiecePositions {
        // For every stone, there's a list of square indexes
        private int[][] pieceSqi;

        private PiecePositions(int[][] pieceSqi) {
            this.pieceSqi = pieceSqi;
        }

        public static PiecePositions fromPosition(Position position) {
            int[][] pps = new int[13][];
            for (Stone stone : Stone.values()) {
                int cnt;
                switch (stone.toPiece()) {
                    case PAWN: cnt = 8; break;
                    case KING: cnt = 1; break;
                    default  : cnt = 3; break;
                }
                pps[stone.index()] = new int[cnt];
                for (int i = 0; i < cnt; i++) {
                    pps[stone.index()][i] = -1;
                }
            }

            for (int i = 0; i < 64; i++) {
                Stone stone = position.stoneAt(i);
                if (!stone.isNoStone()) {
                    int[] pp = pps[stone.index()];
                    for (int j = 0; j < pp.length; j++) {
                        if (pp[j] == -1) {
                            pp[j] = i;
                            break;
                        }
                    }
                }
            }
            return new PiecePositions(pps);
        }

        private int[][] cloneData() {
            int[][] a = new int[pieceSqi.length][];
            for (int i = 0; i < a.length; i++) {
                a[i] = pieceSqi[i].clone();
            }
            return a;
        }

        /**
         * Gets the square of a specific stone give the stoneNo
         * @param stone the stone to get
         * @param stoneNo the stone number
         * @return the square for this stone, or -1 if no stone with this stoneNo on the board
         */
        public int getSqi(Stone stone, int stoneNo) {
            if (stoneNo >= 0 && stoneNo < pieceSqi[stone.index()].length) {
                return pieceSqi[stone.index()][stoneNo];
            }
            return -1;
        }

        /**
         * Gets all the squares the given stone is on, in stoneNo order
         * @param stone the stone to get squares for
         * @return an array containing the square indexes of the given stone
         */
        public int[] getSqis(Stone stone) {
            return pieceSqi[stone.index()].clone(); // clone to ensure immutability
        }

        /**
         * Finds which stoneNo is on a given square
         * @param stone the stone to look for
         * @param sqi the square to check
         * @return the stone number, or -1 if the given stone isn't on the given square
         */
        public int getStoneNo(Stone stone, int sqi) {
            int[] pp = pieceSqi[stone.index()];
            for (int j = 0; j < pp.length; j++) {
                if (pp[j] == sqi) return j;
            }
            return -1;
        }
        /**
         * Removes a stone from the position and returns the updated position
         * @param stone the stone to remove
         * @param sqi the square it should be removed from
         * @return the update position
         */
        public PiecePositions remove(Stone stone, int sqi) {
            int pno = getStoneNo(stone, sqi);
            if (pno < 0) {
                // This can happen if e.g. the fourth queen is captured
                return this;
            }
            int[][] pieces = cloneData();
            int[] removeStone = pieces[stone.index()];

            // If it's a pawn, just remove it
            if (stone.toPiece() == Piece.PAWN) {
                removeStone[pno] = -1;
            } else {
                // Otherwise we must adjust the pieces
                int i = 0, j = 0;
                while (i < removeStone.length) {
                    if (removeStone[i] != sqi) {
                        removeStone[j++] = removeStone[i++];
                    } else {
                        i++;
                    }
                }
                while (j < removeStone.length) {
                    removeStone[j++] = -1;
                }
            }
            return new PiecePositions(pieces);
        }

        /**
         * Changes the square of a stone and returns the updated position
         * @param stone the type of stone to update
         * @param stoneNo the stone number
         * @param sqi the new square for the stone
         * @return the updated position
         */
        public PiecePositions move(Stone stone, int stoneNo, int sqi) {
            int[][] pieces = cloneData();
            pieces[stone.index()][stoneNo] = sqi;
            return new PiecePositions(pieces);
        }

        /**
         * Adds a new stone and returns the updated position
         * @param stone the stone to add
         * @param sqi the square to add it to
         * @return the updated position
         */
        public PiecePositions add(Stone stone, int sqi) {
            int[][] pieces = cloneData();
            int[] pp = pieces[stone.index()];
            for (int j = 0; j < pp.length; j++) {
                if (pp[j] == -1) {
                    pp[j] = sqi;
                    return new PiecePositions(pieces);
                }
            }
            return this; // This is ok since it's an immutable data structure
        }

    }

}
