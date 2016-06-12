package yarin.chess;

import org.junit.Assert;
import org.junit.Test;

public class GameTest {

    @Test
    public void testSameMoveLeadToSameBoard() {
        Board board = new Board();
        Move move = new Move(board, 4, 1, 4, 3);
        Board board1 = board.doMove(move);
        Board board2 = board.doMove(move);
        Assert.assertEquals(board1, board2);
    }

    @Test
    public void testSameMoveCanLeadToDifferentPositionsInGame() {
        Game game = new Game();
        Move move = new Move(game.getPosition(), 4, 1, 4, 3);
        GamePosition pos1 = game.addMove(move);
        GamePosition pos2 = game.addMove(move);
        Assert.assertNotEquals(pos1, pos2);
        Assert.assertEquals(2, game.getMoves().size());
    }
}
