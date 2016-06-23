package se.yarin.chess;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;
import static se.yarin.chess.Player.*;
import static se.yarin.chess.Stone.*;
import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Castles.*;

/**
 * Testing the move generator
 */
public class PositionTest {

    @Test
    public void testStartPosition() {
        assertTrue(Position.start().playerToMove() == WHITE);
        assertEquals(
                "rnbqkbnr\npppppppp\n........\n........\n........\n........\nPPPPPPPP\nRNBQKBNR\n",
                Position.start().toString());
        assertTrue(Position.start().isCastles(WHITE_SHORT_CASTLE));
        assertTrue(Position.start().isCastles(BLACK_LONG_CASTLE));
    }

    @Test
    public void testGenerateMovesAtStartingPosition() {
        assertEquals(20, Position.start().generateAllPseudoLegalMoves().size());
    }

    @Test
    public void testPositionFromString() {
        Position position = Position.fromString("k.K\n.R.", WHITE);
        assertEquals(WHITE_KING, position.stoneAt(C8));
        assertEquals(BLACK_KING, position.stoneAt(A8));
        assertEquals(WHITE_ROOK, position.stoneAt(B7));
        assertEquals(61, numEmptySquares(position));
        assertEquals(WHITE, position.playerToMove());
    }

    @Test
    public void testWhiteRookMoves() {
        Position position = Position.fromString(
                ".....\n" +
                ".R..K\n" +
                "..R..\n" +
                ".Pp..\n" +
                "...k.\n", WHITE);

        verifyToSquares(position.generateRookMoves(B7), B7, B8, B6, A7, C7, D7);
        verifyToSquares(position.generateRookMoves(C6), C6, C7, C8, C5, B6, A6, D6, E6, F6, G6, H6);
    }

    @Test
    public void testBlackRookMoves() {
        Position position = Position.fromString(
                "...K.\n" +
                ".....\n" +
                "rk...\n" +
                "B....\n" +
                ".....\n", BLACK);

        verifyToSquares(position.generateRookMoves(A6), A6, A5, A7, A8);
    }

    @Test
    public void testBishopMoves() {
        Position position = Position.fromString(
                "...k.\n" +
                ".P...\n" +
                "..B..\n" +
                "...p.\n" +
                ".K...\n", WHITE);

        verifyToSquares(position.generateBishopMoves(C6), C6, B5, A4, D5, D7, E8);
    }

    @Test
    public void testKnightMoves() {
        Position position = Position.fromString(
                "..k..\n" +
                ".N...\n" +
                "...K.\n" +
                ".....\n" +
                ".....\n", WHITE);

        verifyToSquares(position.generateKnightMoves(B7), B7, A5, C5, D8);
    }

    @Test
    public void testQueenMoves() {
        Position position = Position.fromString(
                "...k.\n" +
                ".Q...\n" +
                "...K.\n" +
                ".....\n" +
                ".R...\n", WHITE);

        verifyToSquares(position.generateQueenMoves(B7), B7,
                B6, B5, B8, A8, A7, A6, C8, C7, C6, D7, E7, F7, G7, H7, D5, E4, F3, G2, H1);
    }

    @Test
    public void testKingMoves() {
        Position position = Position.fromString(
                "...k.\n" +
                ".K...\n" +
                ".pP..\n" +
                ".....\n" +
                ".....\n", WHITE);

        verifyToSquares(position.generateKingMoves(B7), B7, A8, B8, C8, A7, C7, A6, B6);
    }

    @Test
    public void testWhitePawnMoves() {
        Position position = Position.fromString(
                "k.n..\n" +
                "...P.\n" +
                "..r..\n" +
                ".P...\n" +
                "q....\n" +
                "..N..\n" +
                "PPP..\n" +
                "...K.\n", WHITE);

        verifyToSquares(position.generatePawnMoves(A2), A2, A3);
        verifyToSquares(position.generatePawnMoves(B2), B2, B3, B4);
        verifyToSquares(position.generatePawnMoves(C2), C2);
        verifyToSquares(position.generatePawnMoves(B5), B5, B6, C6);

        List<Move> moves = position.generatePawnMoves(D7);
        assertEquals(8, moves.size());
        assertTrue(moves.contains(new ShortMove(D7, D8, Stone.WHITE_BISHOP)));
        assertTrue(moves.contains(new ShortMove(D7, D8, Stone.WHITE_ROOK)));
        assertTrue(moves.contains(new ShortMove(D7, C8, Stone.WHITE_QUEEN)));
        assertTrue(moves.contains(new ShortMove(D7, C8, Stone.WHITE_KNIGHT)));
    }

    @Test
    public void testBlackPawnMoves() {
        Position position = Position.fromString(
                "...k.\n" +
                "pp...\n" +
                "R.p..\n" +
                "...B.\n" +
                ".....\n" +
                ".....\n" +
                ".p...\n" +
                "...K.\n", BLACK);

        verifyToSquares(position.generatePawnMoves(A7), A7);
        verifyToSquares(position.generatePawnMoves(B7), B7, B6, B5, A6);
        verifyToSquares(position.generatePawnMoves(C6), C6, C5, D5);

        List<Move> moves = position.generatePawnMoves(B2);
        assertEquals(4, moves.size());
        assertTrue(moves.contains(new ShortMove(B2, B1, Stone.BLACK_BISHOP)));
        assertTrue(moves.contains(new ShortMove(B2, B1, Stone.BLACK_ROOK)));
        assertTrue(moves.contains(new ShortMove(B2, B1, Stone.BLACK_QUEEN)));
        assertTrue(moves.contains(new ShortMove(B2, B1, Stone.BLACK_KNIGHT)));
    }

    @Test
    public void testEnPassantMoves() {
        Position position = Position.fromString(
                "...k.\n" +
                ".....\n" +
                ".....\n" +
                ".pPp.\n" +
                ".....\n" +
                ".pPp.\n" +
                ".....\n" +
                "...K.\n", WHITE, EnumSet.noneOf(Castles.class), 3);

        verifyToSquares(position.generatePawnMoves(C5), C5, C6, D6);
        verifyToSquares(position.generatePawnMoves(C3), C3, C4);
    }

    @Test
    public void testCastleMoves() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "......K.\n", BLACK, EnumSet.of(BLACK_SHORT_CASTLE, BLACK_LONG_CASTLE), NO_COL);

        verifyToSquares(position.generateKingMoves(E8), E8, C8, G8, D8, D7, E7, F7, F8);

        position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "......K.\n", BLACK, EnumSet.of(BLACK_LONG_CASTLE), NO_COL);

        verifyToSquares(position.generateKingMoves(E8), E8, C8, D8, D7, E7, F7, F8);

        position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "......K.\n", BLACK, EnumSet.noneOf(Castles.class), NO_COL);

        verifyToSquares(position.generateKingMoves(E8), E8, D8, D7, E7, F7, F8);

        position = Position.fromString(
                "rn..kn.r\n" +
                "........\n" +
                "......K.\n", BLACK, EnumSet.of(BLACK_SHORT_CASTLE, BLACK_LONG_CASTLE), NO_COL);

        verifyToSquares(position.generateKingMoves(E8), E8, D8, D7, E7, F7);

        position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "....R.K.\n", BLACK, EnumSet.of(BLACK_SHORT_CASTLE, BLACK_LONG_CASTLE), NO_COL);

        verifyToSquares(position.generateKingMoves(E8), E8, D8, D7, E7, F7, F8);
    }

    @Test
    public void testSimpleDoMove() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "R...K..R\n", WHITE, EnumSet.allOf(Castles.class), -1);

        Position newPos = position.doMove(new ShortMove(A1, B1));
        assertEquals(BLACK, newPos.playerToMove());
        assertEquals(WHITE_ROOK, newPos.stoneAt(B1));
        assertEquals(NO_STONE, newPos.stoneAt(A1));
        assertEquals(WHITE_KING, newPos.stoneAt(E1));
        assertTrue(newPos.isCastles(WHITE_SHORT_CASTLE));
        assertFalse(newPos.isCastles(WHITE_LONG_CASTLE));
        assertTrue(newPos.isCastles(BLACK_SHORT_CASTLE));
        assertTrue(newPos.isCastles(BLACK_LONG_CASTLE));
        assertEquals(NO_COL, newPos.getEnPassantCol());
    }

    @Test
    public void testNullMoveDoMove() {
        Position position = Position.fromString(
                "....k...\n" +
                "K.......\n", WHITE);
        assertEquals(WHITE, position.playerToMove());

        position = position.doMove(ShortMove.nullMove());
        assertEquals(BLACK, position.playerToMove());
    }

    @Test
    public void testCastlesDoMove() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "........\n" +
                ".pP.....\n" +
                "........\n" +
                ".....P..\n" +
                "....P...\n" +
                "R...K..R\n", BLACK, EnumSet.allOf(Castles.class), 1);

        Position newPos = position.doMove(new ShortMove(E8, C8));
        assertTrue(newPos.stoneAt(D8) == BLACK_ROOK);
        assertTrue(newPos.stoneAt(C8) == BLACK_KING);
        assertTrue(newPos.isCastles(WHITE_SHORT_CASTLE));
        assertTrue(newPos.isCastles(WHITE_LONG_CASTLE));
        assertFalse(newPos.isCastles(BLACK_LONG_CASTLE));
        assertFalse(newPos.isCastles(BLACK_LONG_CASTLE));
    }

    @Test
    public void testPawnDoMove() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "........\n" +
                ".pP.....\n" +
                "........\n" +
                ".....P..\n" +
                "....P...\n" +
                "R...K..R\n", WHITE, EnumSet.allOf(Castles.class), 1);

        Position newPos = position.doMove(new ShortMove(E2, E4));
        assertEquals(4, newPos.getEnPassantCol());

        newPos = position.doMove(new ShortMove(F3, F4));
        assertEquals(NO_COL, newPos.getEnPassantCol());

        newPos = position.doMove(new ShortMove(C5, B6));
        assertEquals(NO_COL, newPos.getEnPassantCol());
        assertEquals(NO_STONE, newPos.stoneAt(B5));
    }

    @Test
    public void testPromotionDoMove() {
        Position position = Position.fromString(
                "....k...\n" +
                ".......P\n" +
                ".K......\n", WHITE);

        Position newPos = position.doMove(new ShortMove(H7, H8));
        assertEquals(WHITE_QUEEN, newPos.stoneAt(H8));

        newPos = position.doMove(new ShortMove(H7, H8, WHITE_ROOK));
        assertEquals(WHITE_ROOK, newPos.stoneAt(H8));
    }

    @Test
    public void testIsCheck() {
        Position position = Position.fromString("r.K.k..r\n", WHITE);
        assertTrue(position.isCheck());

        position = Position.fromString("r.K.k..r\n", BLACK);
        assertFalse(position.isCheck());

        position = Position.fromString(
                "..K.k..r\n" +
                "........\n" +
                ".n......\n", WHITE);
        assertTrue(position.isCheck());

        position = Position.fromString(
                "..K.k..r\n" +
                "........\n" +
                ".N.q....\n", WHITE);
        assertFalse(position.isCheck());

        position = Position.fromString(
                "..K.k..r\n" +
                ".....P..\n" +
                "........\n", BLACK);
        assertTrue(position.isCheck());

        position = Position.fromString(
                "..K.k..r\n" +
                "....P...\n" +
                "........\n", BLACK);
        assertFalse(position.isCheck());

        position = Position.fromString(
                "R..rk...\n" +
                "........\n" +
                "K.......\n", BLACK);
        assertFalse(position.isCheck());
    }

    @Test
    public void testGenerateLegalMoves() {
        Position position = Position.fromString(
                "......rk\n" +
                ".......p\n" +
                "........\n" +
                "........\n" +
                "....n...\n" +
                "........\n" +
                ".......P\n" +
                "rQ..K..R\n", WHITE, EnumSet.of(WHITE_SHORT_CASTLE), NO_COL);

        List<Move> moves = position.generateAllLegalMoves();
        assertEquals(10, moves.size());
        assertTrue(moves.contains(new ShortMove(B1, A1)));
        assertTrue(moves.contains(new ShortMove(B1, C1)));
        assertTrue(moves.contains(new ShortMove(B1, D1)));
        assertTrue(moves.contains(new ShortMove(E1, D1)));
        assertTrue(moves.contains(new ShortMove(E1, F1)));
        assertTrue(moves.contains(new ShortMove(E1, E2)));
        assertTrue(moves.contains(new ShortMove(H1, G1)));
        assertTrue(moves.contains(new ShortMove(H1, F1)));
        assertTrue(moves.contains(new ShortMove(H2, H3)));
        assertTrue(moves.contains(new ShortMove(H2, H4)));
    }

    @Test
    public void testIsMoveLegal() {
        Position position = Position.fromString(
                "Nk......\n" +
                ".......P\n" +
                "........\n" +
                "...PpPp.\n" +
                "........\n" +
                "........\n" +
                "....B..P\n" +
                "R...K..R\n", WHITE, EnumSet.of(WHITE_SHORT_CASTLE), 4);

        assertTrue(position.isMoveLegal(E1, G1));
        assertFalse(position.isMoveLegal(E1, C1));
        assertFalse(position.isMoveLegal(H1, H3));
        assertTrue(position.isMoveLegal(H1, F1));
        assertTrue(position.isMoveLegal(D5, E6));
        assertTrue(position.isMoveLegal(F5, E6));
        assertFalse(position.isMoveLegal(F5, G6));
        assertFalse(position.isMoveLegal(B1, C1));
        assertTrue(position.isMoveLegal(A1, C1));
        assertTrue(position.isMoveLegal(E2, H5));
        assertFalse(position.isMoveLegal(E2, B7));
        assertTrue(position.isMoveLegal(A8, B6));
        assertTrue(position.isMoveLegal(A8, C7));
        assertTrue(position.isMoveLegal(new ShortMove(H7, H8, WHITE_QUEEN)));
        assertTrue(position.isMoveLegal(new ShortMove(H7, H8, WHITE_KNIGHT)));
        assertFalse(position.isMoveLegal(new ShortMove(H7, H8, WHITE_PAWN)));
        assertFalse(position.isMoveLegal(new ShortMove(H7, H8, WHITE_KING)));
        assertFalse(position.isMoveLegal(new ShortMove(H7, H8, BLACK_QUEEN)));
        assertFalse(position.isMoveLegal(new ShortMove(H7, H8, NO_STONE)));

        position = Position.fromString(
                "r...k..r\n" +
                "...p.N..\n" +
                "..B.....\n" +
                "........\n" +
                "..pP....\n" +
                "........\n" +
                "..q.....\n" +
                "....K...\n", BLACK);

        assertFalse(position.isMoveLegal(D7, D6));
        assertTrue(position.isMoveLegal(D7, C6));
        assertFalse(position.isMoveLegal(E8, C8));
        assertTrue(position.isMoveLegal(E8, G8));
        assertTrue(position.isMoveLegal(C2, D2));
        assertFalse(position.isMoveLegal(C2, C2));
        assertFalse(position.isMoveLegal(C4, C5));
        assertTrue(position.isMoveLegal(C4, C3));
        assertFalse(position.isMoveLegal(D4, D5));

        assertTrue(position.isMoveLegal(ShortMove.nullMove())); // special case

        position = Position.fromString("r.K.k\n", WHITE);
        assertTrue(position.isMoveLegal(C8, C7));
        assertFalse(position.isMoveLegal(ShortMove.nullMove())); // can't do null move if in check
    }

    @Test
    public void testEqualsAndHashing() {
        Position p1 = Position.start().doMove(G1, F3).doMove(G8, F6).doMove(F3, G1).doMove(F6, G8);
        Position p2 = Position.start().doMove(G1, H3).doMove(G8, H6).doMove(H3, G1).doMove(H6, G8);
        Position p3 = Position.start().doMove(G1, H3).doMove(G8, H6).doMove(H1, G1).doMove(H6, G8)
            .doMove(G1, H1).doMove(G8, F6).doMove(H3, G1).doMove(F6, G8);

        assertEquals(p1.getZobristHash(), p2.getZobristHash());
        assertNotEquals(p1.getZobristHash(), p3.getZobristHash());

        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1.hashCode(), p3.hashCode());

        assertTrue(p1.equals(p2));
        assertFalse(p1.equals(p3));
    }

    @Test
    public void testIsMate() {
        Position position = Position.fromString(
                "R.....k.\n" +
                ".K...ppp\n" +
                "........\n", BLACK);
        assertTrue(position.isMate());
        assertFalse(position.isStaleMate());

        position = Position.fromString(
                "R.....k.\n" +
                ".K...ppp\n" +
                "..r.....\n", BLACK);
        assertFalse(position.isMate());
        assertFalse(position.isStaleMate());
    }

    @Test
    public void testIsStaleMate() {
        Position position = Position.fromString(
                "k..\n" +
                ".R.\n" +
                "..K\n", BLACK);
        assertTrue(position.isStaleMate());

        position = Position.fromString(
                "k..\n" +
                ".R.\n" +
                "p.K\n", BLACK);
        assertFalse(position.isStaleMate());
    }

    private int numEmptySquares(Position position) {
        int cnt = 0;
        for (int i = 0; i < 64; i++) {
            if (position.stoneAt(i) == NO_STONE) cnt++;
        }
        return cnt;
    }

    private void verifyToSquares(List<Move> moves, int fromSqi, int... toSqis) {
        assertEquals(moves.size(), toSqis.length);
        for (int toSqi : toSqis) {
            assertTrue(moves.contains(new ShortMove(fromSqi, toSqi)));
        }
    }

}
