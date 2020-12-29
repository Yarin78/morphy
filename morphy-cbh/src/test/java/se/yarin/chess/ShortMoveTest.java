package se.yarin.chess;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Chess.G7;
import static se.yarin.chess.Chess.G8;
import static se.yarin.chess.Player.BLACK;
import static se.yarin.chess.Player.WHITE;
import static se.yarin.chess.Stone.WHITE_QUEEN;

public class ShortMoveTest {
    @Test
    public void testSimple() {
        assertEquals("a7a8=B", new ShortMove(A7, A8, Stone.WHITE_BISHOP).toString());
        assertEquals("e6d6", new ShortMove(E6, D6).toString());
    }

    @Test
    public void testCastles() {
        assertEquals("O-O-O", ShortMove.longCastles().toString());
        assertEquals("O-O", ShortMove.shortCastles().toString());
    }

    @Test
    public void testMoveEquals() {
        Position position = Position.fromString(
                "....k...\n" +
                "P.....R.\n" +
                ".K......\n", WHITE);
        ShortMove mv1 = new ShortMove(G7, G8);
        Move mv2 = new Move(position, G7, G8);
        assertTrue(mv1.moveEquals(mv2));
    }

    @Test
    public void testCastleMoveEquals() {
        Position position = Position.fromString(
                "r...k..r\n" +
                "........\n" +
                ".K......\n", BLACK);
        assertTrue(ShortMove.longCastles().moveEquals(Move.longCastles(position)));
        assertTrue(ShortMove.shortCastles().moveEquals(Move.shortCastles(position)));
        // Castling needs to be set explicitly in ShortMove to avoid Chess960 ambiguities.
        assertFalse(new ShortMove(E8, C8).moveEquals(Move.longCastles(position)));
        assertFalse(new ShortMove(E8, G8).moveEquals(Move.shortCastles(position)));
    }

    @Test
    public void testChess960CastleMoveEquals() {
        Position position = Position.fromString(
                "r....k.r\n" +
                "........\n" +
                ".K......\n", BLACK, EnumSet.allOf(Castles.class), -1, 160);

        assertTrue(ShortMove.shortCastles().moveEquals(Move.shortCastles(position)));
        assertTrue(new ShortMove(F8, G8).moveEquals(new Move(position, F8, G8)));
        assertFalse(new ShortMove(F8, G8).moveEquals(Move.shortCastles(position)));
    }

    @Test
    public void testConstructMoveFromShortMove() {
        ShortMove shortMove = new ShortMove(G1, F3);
        Move move = shortMove.toMove(Position.start());
        assertEquals("Nf3", move.toSAN());

        shortMove = ShortMove.shortCastles();
        move = shortMove.toMove(Position.start());
        assertTrue(shortMove.moveEquals(move));

        shortMove = ShortMove.longCastles();
        move = shortMove.toMove(Position.start());
        assertTrue(shortMove.moveEquals(move));
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
