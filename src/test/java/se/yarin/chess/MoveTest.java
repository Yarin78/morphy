package se.yarin.chess;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.*;
import static se.yarin.chess.Player.*;
import static se.yarin.chess.Stone.*;
import static se.yarin.chess.Chess.*;

public class MoveTest {

    @Test
    public void testSimpleSAN() {
        assertEquals("Nf3", new Move(Position.start(), G1, F3).toSAN());
        assertEquals("e4", new Move(Position.start(), E2, E4).toSAN());
    }

    @Test
    public void testSimpleLAN() {
        assertEquals("Ng1-f3", new Move(Position.start(), G1, F3).toLAN());
        assertEquals("e2-e4", new Move(Position.start(), E2, E4).toLAN());
    }

    @Test
    public void testPawnMovesNotation() {
        Position position = Position.fromString(
                "....k...\n" +
                "........\n" +
                "...b....\n" +
                "..P..Pp.\n" +
                "........\n" +
                ".q.r....\n" +
                "..P.....\n" +
                "....K...\n", WHITE, EnumSet.noneOf(Castles.class), 6);

        assertEquals("cxb3", new Move(position, C2, B3).toSAN());
        assertEquals("c2xb3", new Move(position, C2, B3).toLAN());

        assertEquals("cxd3", new Move(position, C2, D3).toSAN());
        assertEquals("c2xd3", new Move(position, C2, D3).toLAN());

        assertEquals("c3", new Move(position, C2, C3).toSAN());
        assertEquals("c2-c3", new Move(position, C2, C3).toLAN());

        assertEquals("c4", new Move(position, C2, C4).toSAN());
        assertEquals("c2-c4", new Move(position, C2, C4).toLAN());

        assertEquals("cxd6", new Move(position, C5, D6).toSAN());
        assertEquals("c5xd6", new Move(position, C5, D6).toLAN());

        assertEquals("fxg6", new Move(position, F5, G6).toSAN());
        assertEquals("f5xg6", new Move(position, F5, G6).toLAN());
    }

    @Test
    public void testAmbiguousSAN() {
        Position position = Position.fromString(
                "....k...\n" +
                "........\n" +
                "..N...N.\n" +
                "........\n" +
                "..N...N.\n" +
                "........\n" +
                "..N.....\n" +
                "....K...\n", WHITE);

        assertEquals("Nc6e5", new Move(position, C6, E5).toSAN());
        assertEquals("Nc4e3", new Move(position, C4, E3).toSAN());
        assertEquals("N2e3", new Move(position, C2, E3).toSAN());
        assertEquals("Nce7", new Move(position, C6, E7).toSAN());
    }

    @Test
    public void testAmbiguousSAN2() {
        Position position = Position.fromString(
                "......k.\n" +
                "..Q.n...\n" +
                "..n.....\n" +
                ".Q...Qn.\n" +
                "...n....\n" +
                "....Qn..\n" +
                ".K..Q..Q\n" +
                "........\n", WHITE);

        assertEquals("Qcd7", new Move(position, C7, D7).toSAN());
        assertEquals("Qhe5", new Move(position, H2, E5).toSAN());
        assertEquals("Q2d3", new Move(position, E2, D3).toSAN());
    }

    @Test
    public void testNonAmbiguousSAN() {
        Position position = Position.fromString(
                "....k...\n" +
                "....r...\n" +
                "........\n" +
                "....N...\n" +
                ".b......\n" +
                "........\n" +
                "...N....\n" +
                "....K.N.\n", WHITE);
        assertEquals("Nf3", new Move(position, G1, F3).toSAN());
    }

    @Test
    public void testNotationWithCheck() {
        Position position = Position.fromString(
                "....k...\n" +
                "......R.\n" +
                ".K......\n", WHITE);

        assertEquals("Rg8+", new Move(position, G7, G8).toSAN());
        assertEquals("Rg7-e7+", new Move(position, G7, E7).toLAN());
    }

    @Test
    public void testNotationWithMate() {
        Position position = Position.fromString(
                "......k.\n" +
                ".....ppp\n" +
                "KRQ.....\n", WHITE);

        assertEquals("Rb8#", new Move(position, B6, B8).toSAN());
        assertEquals("Qc6-c8#", new Move(position, C6, C8).toLAN());
    }

    @Test
    public void testNotationWithMoveNumber() {
        Position position = Position.fromString(
                "....k\n" +
                ".....\n" +
                ".KN..\n", WHITE);
        assertEquals("37.Nc6-b8", new Move(position, C6, B8).toLAN(72));
        assertEquals("37.Nb8", new Move(position, C6, B8).toSAN(72));

        position = position.doMove(Move.nullMove());
        assertEquals("3...Ke8-d8", new Move(position, E8, D8).toLAN(5));
        assertEquals("3...Kd8", new Move(position, E8, D8).toSAN(5));
    }

    @Test
    public void testCastleNotation() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "........\n" +
                "R...K..R\n", WHITE);
        assertEquals("O-O", new Move(position, E1, G1).toSAN());
        assertEquals("O-O", new Move(position, E1, G1).toLAN());
        assertEquals("O-O-O", new Move(position, E1, C1).toSAN());
        assertEquals("O-O-O", new Move(position, E1, C1).toLAN());

        position = position.doMove(Move.nullMove());
        assertEquals("O-O", new Move(position, E8, G8).toSAN());
        assertEquals("O-O", new Move(position, E8, G8).toLAN());
        assertEquals("O-O-O", new Move(position, E8, C8).toSAN());
        assertEquals("O-O-O", new Move(position, E8, C8).toLAN());
    }

    @Test
    public void testPromotionNotation() {
        Position position = Position.fromString(
                ".....K.k\n" +
                "P.......\n", WHITE);

        assertEquals("a8=Q", new Move(position, A7, A8, WHITE_QUEEN).toSAN());
        assertEquals("a7-a8=N", new Move(position, A7, A8, WHITE_KNIGHT).toLAN());
    }

    @Test
    public void testNullMoveNotation() {
        Position position = Position.fromString(
                ".....K.k\n" +
                "P.......\n", WHITE);

        assertEquals("--", Move.nullMove(position).toSAN());
        assertEquals("--", Move.nullMove(position).toLAN());
    }

    @Test
    public void testSANCache() {
        Position position = Position.fromString(
                ".....K.k\n" +
                "P.......\n", WHITE);

        Move move = new Move(position, 0, 6, 0, 7, Stone.WHITE_ROOK);
        assertEquals("a8=R", move.toSAN());
        assertEquals("a8=R", move.toSAN());
    }

    @Test
    public void testToString() {
        Position position = Position.fromString(
                "....k..r\n" +
                "........\n" +
                "K.......\n", BLACK);
        Move move = new Move(position, E8, G8);
        assertTrue(move.isCastle());
        assertEquals("O-O", move.toString());
    }

    @Test
    public void testShortMove() {
        assertEquals("a7a8=B", new ShortMove(A7, A8, Stone.WHITE_BISHOP).toString());
        assertEquals("e6d6", new ShortMove(E6, D6).toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        Position position = Position.fromString(
                "....k...\n" +
                "P.....R.\n" +
                ".K......\n", WHITE);
        ShortMove mv1 = new ShortMove(G7, G8);
        Move mv2 = new Move(position, G7, G8);
        Move mv3 = new Move(position, G7, H7);
        assertEquals(mv1.hashCode(), mv2.hashCode());
        assertTrue(mv1.equals(mv2));
        assertFalse(mv2.equals(mv3));

        ShortMove mv4 = new ShortMove(A7, A8, Stone.WHITE_BISHOP);
        Move mv5 = new Move(position, A7, A8, Stone.WHITE_KNIGHT);
        assertNotEquals(mv4.hashCode(), mv5.hashCode());
    }

    @Test
    public void testConstructMoveFromShortMove() {
        ShortMove shortMove = new ShortMove(G1, F3);
        Move move = shortMove.toMove(Position.start());
        assertEquals("Nf3", move.toSAN());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIllegalMove() {
        new ShortMove(-10, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIllegalMove2() {
        new ShortMove(-1, -1, WHITE_QUEEN);
    }
}
