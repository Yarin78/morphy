package se.yarin.morphy.boosters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.*;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Objects;

/**
 * Represents events that have happened during the game
 */
public class GameEvents {
    private final @NotNull BitSet bits;

    public GameEvents() {
        this.bits = new BitSet(52);
    }

    public GameEvents(@NotNull ByteBuffer buf) {
        if (buf.limit() - buf.position() != 52) {
            throw new IllegalArgumentException("Expected a ByteBuffer with 52 bytes left");
        }
        this.bits = BitSet.valueOf(buf);
    }

    public GameEvents(@NotNull GameMovesModel moves) {
        this(ByteBuffer.wrap(movesToBits(moves)));
    }

    private static byte[] movesToBits(@NotNull GameMovesModel moves) {
        byte[] bytes = new byte[52];

        GameMovesModel.Node node = moves.root();

        int[] stoneCnt = new int[13];

        for (int sqi = 0; sqi < 64; sqi++) {
            Stone stone = node.position().stoneAt(sqi);
            updatePieceAt(bytes, stone, sqi);
            stoneCnt[stone.index()] += 1;
        }

        int piecesLeft = piecesLeftMask(stoneCnt);
        boolean noWhitePieces = (piecesLeft & 0x0F0F) == 0x0F0F;
        boolean noBlackPieces = (piecesLeft & 0xF0F0) == 0xF0F0;

        while (node.mainMove() != null) {
            node = node.mainNode();
            Move move = node.lastMove();

            if (!move.isNullMove()) {
                Stone destinationStone = move.movingStone();
                if (move.promotionStone() != Stone.NO_STONE) {
                    destinationStone = move.promotionStone();
                    stoneCnt[move.movingStone().index()] -= 1;
                    stoneCnt[destinationStone.index()] += 1;
                }
                updatePieceAt(bytes, destinationStone, move.toSqi());

                if (move.isCapture()) {
                    stoneCnt[move.capturedStone().index()] -= 1;
                }
                if (move.isCastle()) {
                    if (move.position().playerToMove() == Player.WHITE) {
                        updatePieceAt(bytes, Stone.WHITE_ROOK, move.isShortCastle() ? Chess.F1 : Chess.D1);
                    } else {
                        updatePieceAt(bytes, Stone.BLACK_ROOK, move.isShortCastle() ? Chess.F8 : Chess.D8);
                    }
                }
                int mask = piecesLeftMask(stoneCnt);
                noWhitePieces |= (mask & 0x0F0F) == 0x0F0F;
                noBlackPieces |= (mask & 0xF0F0) == 0xF0F0;

                piecesLeft |= mask;
            }
        }

        bytes[3] = (byte)
                (Math.min(7, stoneCnt[Stone.WHITE_PAWN.index()]) +
                (noWhitePieces ? 8 : 0) +
                Math.min(7, stoneCnt[Stone.BLACK_PAWN.index()]) * 16 +
                (noBlackPieces ? 128 : 0));
        bytes[4] = (byte) piecesLeft;
        bytes[5] = (byte) (piecesLeft >> 8);

        return bytes;
    }

    private static int piecesLeftMask(int[] stoneCnt) {
        return ((stoneCnt[Stone.WHITE_QUEEN.index()] == 0 ? 1 : 0) +
                (stoneCnt[Stone.WHITE_ROOK.index()] < 2 ? 2 : 0) +
                (stoneCnt[Stone.WHITE_BISHOP.index()] < 2 ? 4 : 0) +
                (stoneCnt[Stone.WHITE_KNIGHT.index()] < 2 ? 8 : 0) +
                (stoneCnt[Stone.BLACK_QUEEN.index()] == 0 ? 16 : 0) +
                (stoneCnt[Stone.BLACK_ROOK.index()] < 2 ? 32 : 0) +
                (stoneCnt[Stone.BLACK_BISHOP.index()] < 2 ? 64 : 0) +
                (stoneCnt[Stone.BLACK_KNIGHT.index()] < 2 ? 128 : 0) +
                (stoneCnt[Stone.WHITE_QUEEN.index()] == 0 ? 256 : 0) +
                (stoneCnt[Stone.WHITE_ROOK.index()] == 0 ? 512 : 0) +
                (stoneCnt[Stone.WHITE_BISHOP.index()] == 0 ? 1024 : 0) +
                (stoneCnt[Stone.WHITE_KNIGHT.index()] == 0 ? 2048 : 0) +
                (stoneCnt[Stone.BLACK_QUEEN.index()] == 0 ? 4096 : 0) +
                (stoneCnt[Stone.BLACK_ROOK.index()] == 0 ? 8192 : 0) +
                (stoneCnt[Stone.BLACK_BISHOP.index()] == 0 ? 16384 : 0) +
                (stoneCnt[Stone.BLACK_KNIGHT.index()] == 0 ? 32768 : 0));
    }

    private static void updatePieceAt(byte[] bytes, @NotNull Stone stone, int sqi) {
        int row = Chess.sqiToRow(sqi);
        int col = Chess.sqiToCol(sqi);
        if (stone == Stone.WHITE_PAWN) {
            if (row >= 2 && row <= 6) {
                bytes[6 + row - 2] |= 1 << col;
            }
        }
        if (stone == Stone.BLACK_PAWN) {
            if (row >= 1 && row <= 5) {
                bytes[11 + row - 1] |= 1 << col;
            }
        }
        if (stone == Stone.WHITE_KING) {
            bytes[16] |= 1 << row;
            bytes[18] |= 1 << col;
        }
        if (stone == Stone.BLACK_KING) {
            bytes[17] |= 1 << row;
            bytes[19] |= 1 << col;
        }
        if (stone == Stone.WHITE_QUEEN || stone == Stone.WHITE_ROOK) {
            bytes[20 + row] |= 1 << col;
        }
        if (stone == Stone.BLACK_QUEEN || stone == Stone.BLACK_ROOK) {
            bytes[28 + row] |= 1 << col;
        }
        if (stone == Stone.WHITE_KNIGHT || stone == Stone.WHITE_BISHOP) {
            bytes[36 + row] |= 1 << col;
        }
        if (stone == Stone.BLACK_KNIGHT || stone == Stone.BLACK_BISHOP) {
            bytes[44 + row] |= 1 << col;
        }
    }

    public byte[] getBytes() {
        return bits.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameEvents that = (GameEvents) o;
        return bits.equals(that.bits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bits);
    }

    @Override
    public String toString() {
        return "GameEvents{" +
                "bits=" + bits +
                '}';
    }
}
