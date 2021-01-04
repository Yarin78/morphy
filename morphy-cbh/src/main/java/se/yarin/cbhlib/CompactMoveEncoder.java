package se.yarin.cbhlib;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.chess.*;

import java.nio.ByteBuffer;
import java.util.Stack;

import static se.yarin.chess.Chess.*;

/**
 * This is the default encoder used to encode moves of a ChessBase game.
 * Most moves are encoded as a single byte.
 */
public class CompactMoveEncoder implements MoveEncoder {
    private static final Logger log = LoggerFactory.getLogger(CompactMoveEncoder.class);

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
            new OpcodeRange(Piece.PAWN,   115, 1),
            new OpcodeRange(Piece.PAWN,   119, 2),
            new OpcodeRange(Piece.PAWN,   123, 3),
            new OpcodeRange(Piece.PAWN,   127, 4),
            new OpcodeRange(Piece.PAWN,   131, 5),
            new OpcodeRange(Piece.PAWN,   135, 6),
            new OpcodeRange(Piece.PAWN,   139, 7),
            new OpcodeRange(Piece.QUEEN,  143, 1),
            new OpcodeRange(Piece.QUEEN,  171, 2),
            new OpcodeRange(Piece.ROOK,   199, 2),
            new OpcodeRange(Piece.BISHOP, 213, 2),
            new OpcodeRange(Piece.KNIGHT, 227, 2),
            new OpcodeRange(Piece.NO_PIECE, OPCODE_TWO_BYTES, 0)
    };

    private static int opcodeOffsets[][];

    private static final OpcodeMap[] opcodeMap = new OpcodeMap[256];

    private static final int[] kingDir = new int[] { 1, 9, 8, 7, -1, -9, -8, -7};
    private static final int[] knightDir = new int[] { 17, 10, -6, -15, -17, -10, 6, 15 };

    private static int[] kingDelta;
    private static int[] knightDelta;

    static {
        // Map the opcode ranges into lookup tables for each opcode
        for (int op = 1, ofs = 0, rangeNo = 0; op < 256; op++, ofs++) {
            if (rangeNo+1 < opcodeRanges.length && opcodeRanges[rangeNo+1].opcode == op) {
                rangeNo++;
                ofs=0;
            }
            opcodeMap[op] = new OpcodeMap(opcodeRanges[rangeNo].piece, ofs, opcodeRanges[rangeNo].pieceNo);
        }

        // Create inverse map of opcodeRanges
        opcodeOffsets = new int[7][8];
        for (OpcodeRange range : opcodeRanges) {
            opcodeOffsets[range.piece.ordinal()][range.pieceNo] = range.opcode;
        }

        // Create inverses of kingDir and knightDir
        kingDelta = new int[64];
        knightDelta = new int[64];
        for (int i = 0; i < 64; i++) {
            kingDelta[i] = knightDelta[i] = -1;
        }
        for (int i = 0; i < kingDir.length; i++) {
            kingDelta[kingDir[i] + 32] = i;
        }
        for (int i = 0; i < knightDir.length; i++) {
            knightDelta[knightDir[i] + 32] = i;
        }
    }

    private final boolean modifierFlag;
    private final boolean reverseScanOrder;
    private final short[] encryptionMap;
    private final short[] decryptionMap;

    private int modifier;

    public CompactMoveEncoder(int keyNo, boolean modifierFlag, boolean reverseScanOrder) {
        this.encryptionMap = KeyProvider.getMoveSerializationKey(keyNo);
        this.decryptionMap = KeyProvider.getMoveSerializationKey(keyNo + 1);
        this.modifierFlag = modifierFlag;
        this.reverseScanOrder = reverseScanOrder;
    }

    @Override
    public synchronized void encode(ByteBuffer buf, GameMovesModel movesModel) {
        StonePositions piecePosition = StonePositions.fromPosition(movesModel.root().position(), reverseScanOrder);
        encodeMoves(buf, movesModel.root(), piecePosition);
    }

    private void encodeMoves(ByteBuffer buf, GameMovesModel.Node node, StonePositions piecePosition) {
        if (INTEGRITY_CHECKS_ENABLED) {
            piecePosition.validate(node.position());
        }

        if (node.children().size() == 0) {
            put(buf, OPCODE_END_VARIANT);
            return;
        }

        for (int i = 0; i < node.children().size(); i++) {
            GameMovesModel.Node child = node.children().get(i);
            Move move = child.lastMove();
            try {
                int opcode = encodeMove(move, piecePosition, node.position());
                if (i + 1 < node.children().size()) {
                    put(buf, OPCODE_START_VARIANT);
                }
                put(buf, opcode);

                if (log.isDebugEnabled()) {
                    log.debug(String.format("Serialized move %s to opcode %02X", move.toLAN(), opcode));
                }

                if (opcode == OPCODE_TWO_BYTES) {
                    opcode = encodeSpecialMove(move);
                    put(buf, opcode / 256);
                    put(buf, opcode % 256);

                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Serialized move %s to opcode %04X", move.toLAN(), opcode));
                    }
                }
                modifier++;
                encodeMoves(buf, child, piecePosition.doMove(move));
            } catch (IllegalArgumentException e) {
                // Shouldn't happen if the game model contains legal moves
                // If it does, we don't encode the remainder of this variation
                // This is not tested since it shouldn't be possible to
                // construct a GameMovesModel with illegal moves...
                log.warn("Failed to encode illegal move", e);
                put(buf, OPCODE_END_VARIANT);
            }

        }
    }

    private int encodeMove(Move move, StonePositions stonePositions, Position position) {
        if (move.isNullMove()) {
            return OPCODE_NULLMOVE;
        }

        if (!position.isRegularChess() && move.isCastle()) {
            return OPCODE_TWO_BYTES;
        }

        int stoneNo = stonePositions.getStoneNo(move.movingStone(), move.fromSqi());
        Piece piece = move.movingStone().toPiece();
        Player playerToMove = position.playerToMove();

        int ofs = stoneNo >= 0 && stoneNo < 8 ? opcodeOffsets[piece.ordinal()][stoneNo] : 0;

        if (ofs == 0 || (piece == Piece.PAWN && !move.promotionStone().isNoStone())) {
            return OPCODE_TWO_BYTES;
        }

        if (piece == Piece.PAWN) {
            int dir = playerToMove == Player.WHITE ? 1 : -1;
            int delta = move.toSqi() - move.fromSqi();
            if (delta == dir) return ofs;
            if (delta == dir * 2) return ofs + 1;
            if (delta == dir * 9) return ofs + 2;
            if (delta == dir * -7) return ofs + 3;
            throw new IllegalArgumentException("Can't encode illegal move: " + move.toString());
        }

        if (piece == Piece.KING) {
            if (move.isShortCastle()) return ofs + 8;
            if (move.isLongCastle()) return ofs + 9;
            int dif = move.toSqi() - move.fromSqi() + 32;
            if (dif >= 0 && dif < 64 && kingDelta[dif] >= 0) return ofs + kingDelta[dif];
            throw new IllegalArgumentException("Can't encode illegal move: " + move.toString());
        }

        if (piece == Piece.BISHOP || piece == Piece.ROOK || piece == Piece.QUEEN) {
            int dx = (move.toCol() - move.fromCol() + 8) % 8;
            int dy = (move.toRow() - move.fromRow() + 8) % 8;
            int dir, stride;
            if (dx == 0 && dy != 0) {
                dir = 0;
                stride = (dy + 6) % 7;
            } else if (dx != 0 && dy == 0) {
                dir = 1;
                stride = (dx + 6) % 7;
            } else if (dx == dy) {
                dir = 2;
                stride = (dx + 6) % 7;
            } else if (dx + dy == 8) {
                dir = 3;
                stride = (dx + 6) % 7;
            } else {
                throw new IllegalArgumentException("Can't encode illegal move: " + move.toString());
            }
            if (piece == Piece.BISHOP) dir -= 2;
            return ofs + dir * 7 + stride;
        }

        if (piece == Piece.KNIGHT) {
            int dif = move.toSqi() - move.fromSqi() + 32;
            if (dif >= 0 && dif < 64 && knightDelta[dif] >= 0) return ofs + knightDelta[dif];
            throw new IllegalArgumentException("Can't encode illegal move: " + move.toString());
        }

        throw new IllegalArgumentException("Can't encode illegal move: " + move.toString());
    }

    private int encodeSpecialMove(Move move) {
        if (!move.position().isRegularChess() && move.isCastle()) {
            boolean isWhite = move.position().playerToMove() == Player.WHITE;
            int sqi = isWhite ? move.isShortCastle() ? G1 : C1 : move.isShortCastle() ? G8 : C8;
            return sqi * 64 + sqi;
        }

        int code = 0;
        switch (move.promotionStone().toPiece()) {
            case QUEEN :code = 0; break;
            case ROOK  :code = 1; break;
            case BISHOP:code = 2; break;
            case KNIGHT:code = 3; break;
        }
        return move.fromSqi() + move.toSqi() * 64 + code * 4096;
    }

    @Override
    public synchronized void decode(ByteBuffer buf, GameMovesModel movesModel)
            throws ChessBaseMoveDecodingException {

        GameMovesModel.Node currentNode = movesModel.root();
        StonePositions piecePosition = StonePositions.fromPosition(
                currentNode.position(), reverseScanOrder);

        Stack<GameMovesModel.Node> nodeStack = new Stack<>();
        Stack<StonePositions> piecePositionStack = new Stack<>();

        while (true) {
            int opcode = get(buf);
//                log.info(String.format("Decoded byte %02X", opcode));

            if (opcode == OPCODE_IGNORE) {
                // Not sure what this opcode does. Just ignoring it seems works fine.
                // Chessbase 9 removes this opcode when replacing the game.
                continue;
            }
            if (opcode > OPCODE_IGNORE && opcode < OPCODE_START_VARIANT) {
                log.warn(String.format("Unknown opcode in game data, ignoring: 0x%02X", opcode));
                continue;
            }
            if (opcode == OPCODE_START_VARIANT) {
                nodeStack.push(currentNode);
                piecePositionStack.push(piecePosition);
                continue;
            }
            if (opcode == OPCODE_END_VARIANT) {
                // Also used to mark the end of the game
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
                opcode = get(buf) * 256 + get(buf);
                move = decodeTwoByteMove(opcode, currentNode.position());
            } else {
                move = decodeSingleByteMove(opcode, piecePosition, currentNode.position());
            }

            if (log.isDebugEnabled()) {
                log.debug(String.format("Parsed opcode %02X to move %s", opcode, move.toLAN()));
            }

            // Update position of the moved piece
            piecePosition = piecePosition.doMove(move);
            currentNode = currentNode.addMove(move);

            if (INTEGRITY_CHECKS_ENABLED) {
                piecePosition.validate(currentNode.position());
            }

            modifier++;
        }
    }

    private Move decodeSingleByteMove(int opcode, StonePositions stonePositions, Position position)
            throws ChessBaseMoveDecodingException {
        Player playerToMove = position.playerToMove();

        if (opcode == OPCODE_NULLMOVE) {
            return Move.nullMove(position);
        }
        if (opcode < OPCODE_NULLMOVE || opcode > OPCODE_TWO_BYTES) {
            throw new ChessBaseMoveDecodingException("Invalid opcode: " + opcode);
        }

        Piece piece = opcodeMap[opcode].piece;
        int stoneNo = opcodeMap[opcode].pieceNo;
        int ofs = opcodeMap[opcode].ofs;

        int sqi = stonePositions.getSqi(piece.toStone(playerToMove), stoneNo);
        if (sqi < 0) {
            throw new ChessBaseMoveDecodingException(
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

        throw new ChessBaseMoveDecodingException("Invalid opcode: " + opcode);
    }

    private Move decodeTwoByteMove(int opcode, Position board)
            throws ChessBaseMoveDecodingException {
        Player playerToMove = board.playerToMove();
        int fromSqi = opcode % 64, toSqi = (opcode / 64) % 64;

        if (fromSqi == toSqi) {
            // Castles in Chess960 games are encoded like this
            if (fromSqi == G1 || fromSqi == G8) {
                return Move.shortCastles(board);
            }
            if (fromSqi == C1 || fromSqi == C8) {
                return Move.longCastles(board);
            }
        }

        Piece piece = board.stoneAt(fromSqi).toPiece();
        if (piece == Piece.NO_PIECE) {
            throw new ChessBaseMoveDecodingException("No piece at source square: " + Chess.sqiToStr(fromSqi));
        }

        if (piece != Piece.PAWN) {
            return new Move(board, fromSqi, toSqi);
        }

        // This should be a pawn promotion, weird otherwise
        int toRow = Chess.sqiToRow(toSqi);
        if (toRow > 0 && toRow < 7) {
            throw new ChessBaseMoveDecodingException("Double bytes used for non-promotion pawn move");
        }

        Piece promotedPiece;
        switch (opcode / 4096) {
            case 0: promotedPiece = Piece.QUEEN;  break;
            case 1: promotedPiece = Piece.ROOK;   break;
            case 2: promotedPiece = Piece.BISHOP; break;
            case 3: promotedPiece = Piece.KNIGHT; break;
            default:
                throw new ChessBaseMoveDecodingException("Illegal promoted piece: " + opcode / 4096);
        }

        return new Move(board, fromSqi, toSqi, promotedPiece.toStone(playerToMove));
    }


    public void put(ByteBuffer buf, int value) {
        if (modifierFlag) {
            value = encryptionMap[value] + modifier;
        } else {
            value = (encryptionMap[(value + modifier) % 256]);
        }
        buf.put((byte) value);
    }

    public int get(ByteBuffer buf) {
        int key = ByteBufferUtil.getUnsignedByte(buf);
        if (modifierFlag) {
            key = ((key - modifier) % 256 + 256) % 256;
        }
        int value = decryptionMap[key];
        if (!modifierFlag) {
            value = ((value - modifier) % 256 + 256) % 256;
        }
        return value;
    }
}
