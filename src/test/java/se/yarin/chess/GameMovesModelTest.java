package se.yarin.chess;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import se.yarin.cbhlib.annotations.TextAfterMoveAnnotation;
import se.yarin.chess.annotations.*;

import java.util.EnumSet;

import static org.junit.Assert.*;
import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Player.*;

public class GameMovesModelTest {

    private GameMovesModel moves;
    private int numFiredChanges;
    private GameMovesModelChangeListener listener;

    @Before
    public void setupModel() {
        this.moves = new GameMovesModel();
        this.listener = (movesModel, node) -> numFiredChanges++;
        this.moves.addChangeListener(listener);
    }

    @After
    public void tearDownModel() {
        assertTrue(this.moves.removeChangeListener(listener));
    }

    @Test
    public void testEmptyModel() {
        assertEquals(Position.start(), moves.root().position());
        assertEquals(0, moves.root().ply());
        assertEquals(0, moves.root().children().size());
        assertTrue(moves.root().isRoot());
        assertFalse(moves.isSetupPosition());
        assertEquals(0, numFiredChanges);
    }

    @Test
    public void testSetupPositionWhiteToMove() {
        String startPos = "rnb.kbnr\nppp.pppp\n........\n...q....\n........\n........\nPPPP.PPP\nRNBQKBNR\n";
        GameMovesModel model = new GameMovesModel(Position.fromString(startPos, Player.WHITE, EnumSet.allOf(Castles.class), -1), 3);

        assertEquals(startPos, model.root().position().toString("\n"));
        assertEquals(4, model.root().ply());
    }

    @Test
    public void testSetupPositionBlackToMove() {
        String startPos = "rnb.kbnr\nppp.pppp\n........\n...q....\n........\nP.......\n.PPP.PPP\nRNBQKBNR\n";
        GameMovesModel model = new GameMovesModel(Position.fromString(startPos, Player.BLACK, EnumSet.allOf(Castles.class), -1), 3);

        assertEquals(startPos, model.root().position().toString("\n"));
        assertEquals(5, model.root().ply());
    }

    @Test
    public void testAddMove() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        assertFalse(node.isRoot());
        assertTrue(node.isValid());
        assertEquals(1, node.ply());
        assertEquals(node.parent(), moves.root());
        assertEquals(1, numFiredChanges);

        assertEquals(1, moves.root().children().size());
        assertTrue(new ShortMove(E2, E4).moveEquals(moves.root().children().get(0).lastMove()));
    }

    @Test(expected = IllegalMoveException.class)
    public void testAddIllegalMove() {
        moves.root().addMove(E2, E5);
    }

    @Test(expected = IllegalMoveException.class)
    public void testAddIllegalMove2() {
        moves.root().addMove(D2, D4).addMove(E7, E6).addMove(B1, C3).addMove(F8, B4).addMove(C3, B1);
    }

    @Test
    public void testSetupPositionAndReset() {
        Position position = Position.fromString(
                "....k...\n" +
                "......R.\n" +
                ".K......\n", WHITE);
        moves.setupPosition(position, 82);
        GameMovesModel.Node node = moves.root().addMove(G7, G8);

        assertTrue(moves.isSetupPosition());
        assertEquals(1, moves.countPly(false));
        assertEquals(83, node.ply());

        moves.reset();
        assertFalse(moves.isSetupPosition());
    }

    @Test
    public void testAddMultipleMoves() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(D7, D5).addMove(E4, D5).addMove(D8, D5);
        assertEquals(4, node.ply());
        assertEquals(4, numFiredChanges);
        assertEquals(WHITE, node.position().playerToMove());
        assertEquals("rnb.kbnr\nppp.pppp\n........\n...q....\n........\n........\nPPPP.PPP\nRNBQKBNR\n",
                node.position().toString());
    }

    @Test
    public void testAddVariations() {
        moves.root().addMove(E2, E4).parent().addMove(D2, D4);
        moves.root().addMove(E2, E4);
        assertEquals(3, moves.root().children().size());
        assertTrue(new ShortMove(E2, E4).moveEquals(moves.root().children().get(0).lastMove()));
        assertTrue(new ShortMove(D2, D4).moveEquals(moves.root().children().get(1).lastMove()));
        assertTrue(new ShortMove(E2, E4).moveEquals(moves.root().children().get(2).lastMove()));
        assertNotEquals(moves.root().children().get(0), moves.root().children().get(2));
    }

    @Test
    public void testPlyCount() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        assertEquals(5, moves.countPly(false));
        assertEquals(5, moves.countPly(true));

        node.addMove(F8, C5);
        assertEquals(6, moves.countPly(false));
        assertEquals(6, moves.countPly(true));

        node.addMove(G8, F6);
        assertEquals(6, moves.countPly(false));
        assertEquals(7, moves.countPly(true));

        node.addMove(D7, D6);
        assertEquals(6, moves.countPly(false));
        assertEquals(8, moves.countPly(true));

        moves.root().addMove(E2, E4);
        assertEquals(6, moves.countPly(false));
        assertEquals(9, moves.countPly(true));
    }

    @Test
    public void testNodeSAN() {
        GameMovesModel.Node node = moves.root().addMove(D2, D4).addMove(D7, D5).addMove(C2, C4).addMove(E7, E6).addMove(B1, C3);
        node.addMove(F8, E7);
        node.addMove(C7, C6);
        GameMovesModel.Node node2 = node.addMove(G8, F6);
        node2.addMove(C1, G5).addMove(F8, E7);
        node2.addMove(C1, F4).addMove(F8, D6);

        assertEquals("1.d4 d5 2.c4 e6 3.Nc3 Be7 (3...c6; 3...Nf6 4.Bg5 (4.Bf4 Bd6) 4...Be7)", moves.toString());
    }

    @Test
    public void testDeleteNode() {
        moves.root().addMove(E2, E4).addMove(E7, E5);
        moves.root().addMove(D2, D4);
        moves.root().mainNode().deleteNode();
        assertEquals(1, moves.root().numMoves());
        assertTrue(new ShortMove(D2, D4).moveEquals(moves.root().mainMove()));
    }

    @Test
    public void testPromoteMove() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        node.addMove(F8, C5);
        node.addMove(A7, A6);
        GameMovesModel.Node promotedNode = node.addMove(G8, F6);
        node.addMove(D7, D6);

        assertTrue(new ShortMove(F8, C5).moveEquals(node.children().get(0).lastMove()));
        assertTrue(new ShortMove(A7, A6).moveEquals(node.children().get(1).lastMove()));
        assertTrue(new ShortMove(G8, F6).moveEquals(node.children().get(2).lastMove()));
        assertTrue(new ShortMove(D7, D6).moveEquals(node.children().get(3).lastMove()));

        promotedNode.promoteMove();
        assertTrue(new ShortMove(G8, F6).moveEquals(node.children().get(0).lastMove()));
        assertTrue(new ShortMove(A7, A6).moveEquals(node.children().get(1).lastMove()));
        assertTrue(new ShortMove(F8, C5).moveEquals(node.children().get(2).lastMove()));
        assertTrue(new ShortMove(D7, D6).moveEquals(node.children().get(3).lastMove()));
    }

    @Test
    public void testOverwriteMove() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        node.addMove(F8, C5);
        node.addMove(A7, A6);
        assertEquals(2, node.children().size());
        node.overwriteMove(G8, F6);
        assertEquals(1, node.children().size());
        assertEquals(new Move(node.position(), G8, F6), node.children().get(0).lastMove());
    }

    @Test
    public void testInsertMove() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        GameMovesModel.Node node2 = node.addMove(F8, C5);
        GameMovesModel.Node a = node2.addMove(E1, G1).addMove(G8, F6)
                .addMove(D2, D3)
                .addAnnotation(new SymbolAnnotation(MovePrefix.DIRECTED_AGAINST))
                .addMove(C5, B6)
                .addAnnotation(new SymbolAnnotation(MoveComment.BLUNDER))
                .addMove(C2, C3);
        GameMovesModel.Node b = node2.addMove(B1, C3).addMove(C5, B6)
                .addMove(D2, D3)
                .addAnnotation(new SymbolAnnotation(LineEvaluation.UNCLEAR));

        numFiredChanges = 0;
        assertEquals("3...Bc5 4.O-O (4.Nc3 Bb6 5.d3 unclear) 4...Nf6 Directed against... 5.d3 Bb6?? 6.c3", node.toSAN());
        assertEquals(3, moves.countAnnotations());
        node.insertMove(F8, E7);
        assertEquals("3...Be7 4.O-O (4.Nc3) 4...Nf6 Directed against... 5.d3", node.toSAN());
        assertFalse(a.isValid());
        assertFalse(b.isValid());
        assertEquals(1, numFiredChanges);
        assertEquals(1, moves.countAnnotations());
    }

    @Test
    public void testDeleteRemainingMoves() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        node.addMove(F8, C5).addMove(B1, C3);
        node.addMove(A7, A6);
        Assert.assertEquals(2, node.children().size());
        node.deleteRemainingMoves();
        Assert.assertEquals(0, node.children().size());
    }

    @Test
    public void testPromoteVariation() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        node.addMove(A7, A6);
        GameMovesModel.Node node2 = node.addMove(F8, C5).addMove(B1, C3);
        node2.addMove(D7, D6).addMove(E1, G1);
        GameMovesModel.Node node3 = node2.addMove(G8, F6).addMove(E1, G1);

        Assert.assertEquals("1.e4 e5 2.Nf3 Nc6 3.Bc4 a6 (3...Bc5 4.Nc3 d6 (4...Nf6 5.O-O) 5.O-O)",  moves.toString());
        Assert.assertEquals(2, node3.getVariationDepth());
        Assert.assertFalse(node3.isMainLine());
        node3.promoteVariation();
        Assert.assertEquals("1.e4 e5 2.Nf3 Nc6 3.Bc4 a6 (3...Bc5 4.Nc3 Nf6 (4...d6 5.O-O) 5.O-O)",  moves.toString());
        Assert.assertEquals(1, node3.getVariationDepth());
        Assert.assertFalse(node3.isMainLine());
        node3.promoteVariation();
        Assert.assertEquals("1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 (3...a6) 4.Nc3 Nf6 (4...d6 5.O-O) 5.O-O",  moves.toString());
        Assert.assertEquals(0, node3.getVariationDepth());
        Assert.assertTrue(node3.isMainLine());
        node3.promoteVariation();
        Assert.assertEquals(0, node3.getVariationDepth());
        Assert.assertTrue(node3.isMainLine());
    }

    @Test
    public void testDeleteVariation() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        GameMovesModel.Node mainVar = node.addMove(F8, C5).addMove(D2, D3).addMove(G8, F6);
        GameMovesModel.Node deleteVar = node.addMove(A7, A6).addMove(A2, A4).addMove(F8, C5);
        node.addMove(G8, F6).addMove(F3, G5).addMove(D7, D5);

        assertEquals(3, node.numMoves());

        GameMovesModel.Node retNode = mainVar.deleteVariation();
        assertNull(retNode);

        retNode = deleteVar.deleteVariation();
        assertSame(node, retNode);
        assertEquals(2, node.numMoves());
        assertTrue(new ShortMove(F8, C5).moveEquals(node.moves().get(0)));
        assertTrue(new ShortMove(G8, F6).moveEquals(node.moves().get(1)));
    }

    @Test
    public void testDeletePreviousMoves() {
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5);
        GameMovesModel.Node newStart = node.addMove(G1, F3).addMove(B8, C6).addMove(F1, C4);
        node.addMove(B1, C3).addMove(G8, F6);
        newStart.addMove(F8, C5).addMove(D2, D3);

        assertEquals(9, moves.countPly(true));
        assertFalse(moves.isSetupPosition());
        assertNotNull(newStart.lastMove());

        assertTrue(node.isValid());
        assertTrue(newStart.isValid());

        newStart.deletePreviousMoves();
        assertTrue(moves.isSetupPosition());
        assertEquals(5, moves.root().ply());
        assertSame(newStart, moves.root());
        assertEquals(2, moves.countPly(true));
        assertNull(newStart.lastMove());

        assertFalse(node.isValid());
        assertTrue(newStart.isValid());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNodeChildrenIsReadOnly() {
        moves.root().addMove(E2, E4);
        assertEquals(1, moves.root().children().size());
        moves.root().children().add(moves.root().mainNode());
        assertEquals(1, moves.root().children().size());
    }

    @Test
    public void testAnnotations() {
        moves.root()
            .addMove(E2, E4)
            .addAnnotation(new CommentaryAfterMoveAnnotation("Good start!"))
            .addMove(A7, A6)
            .addAnnotation(new SymbolAnnotation(MoveComment.BAD_MOVE, LineEvaluation.WHITE_SLIGHT_ADVANTAGE));

        assertEquals(2, moves.countAnnotations());
        assertEquals("1.e4 { Good start! } a6? +/=", moves.toString());

        moves.root().mainNode().deleteAnnotations();
        assertEquals(1, moves.countAnnotations());
        assertEquals("1.e4 a6? +/=", moves.toString());
    }

    @Test
    public void testDeleteAllAnnotations() {
        moves.root()
                .addAnnotation(new CommentaryAfterMoveAnnotation("start of game"))
                .addMove(E2, E4)
                .addAnnotation(new SymbolAnnotation(MoveComment.GOOD_MOVE))
                .addMove(E7, E5)
                .addMove(G1, F3)
                .parent()
                .addMove(B1, C3)
                .addAnnotation(new SymbolAnnotation(MoveComment.INTERESTING_MOVE));

        numFiredChanges = 0;
        moves.deleteAllAnnotations();
        assertEquals(0, moves.countAnnotations());
        assertEquals(1, numFiredChanges);
    }

    @Test
    public void testReplaceAll() {
        moves.root()
                .addAnnotation(new CommentaryAfterMoveAnnotation("start of game"))
                .addMove(E2, E4)
                .addAnnotation(new SymbolAnnotation(MoveComment.GOOD_MOVE))
                .addMove(E7, E5)
                .addMove(G1, F3)
                .parent()
                    .addMove(B1, C3)
                    .addAnnotation(new SymbolAnnotation(MoveComment.INTERESTING_MOVE));

        numFiredChanges = 0;

        GameMovesModel newModel = new GameMovesModel();
        newModel.root()
                .addMove(E2, E4)
                .addMove(D7, D6)
                .addAnnotation(new CommentaryAfterMoveAnnotation("Pirc"))
                .addMove(D2, D4);

        moves.replaceAll(newModel);

        assertEquals("1.e4 d6 { Pirc } 2.d4", moves.toString());
        assertEquals(1, numFiredChanges);
    }

    @Test
    public void testCloneModel() {
        moves.root()
                .addMove(E2, E4)
                .addAnnotation(new SymbolAnnotation(MoveComment.GOOD_MOVE))
                .addMove(E7, E5)
                .addMove(G1, F3)
                .parent()
                .addMove(B1, C3)
                .addAnnotation(new SymbolAnnotation(MoveComment.INTERESTING_MOVE));

        // Test that there are no shared nodes in the original and clone by
        // performing various operations on the cloned version
        GameMovesModel clone = new GameMovesModel(moves);
        assertEquals(moves.toString(), clone.toString());
        clone.root().addMove(D2, D4);
        assertNotEquals(moves.toString(), clone.toString());

        clone = new GameMovesModel(moves);
        clone.root().mainNode().addMove(E7, E6);
        assertNotEquals(moves.toString(), clone.toString());

        clone = new GameMovesModel(moves);
        clone.root().mainNode().deleteAnnotations();
        assertNotEquals(moves.toString(), clone.toString());

        clone = new GameMovesModel(moves);
        clone.root().mainNode().mainNode().children().get(1).addAnnotation(new TextAfterMoveAnnotation("variant"));
        assertNotEquals(moves.toString(), clone.toString());
    }
}
