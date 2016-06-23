package se.yarin.chess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class NavigableGameModelTest {

    private int numCursorChanges = 0, numMovesChanges = 0, numHeaderChanges = 0;
    private NavigableGameModel model;
    private NavigableGameModelChangeListener listener;

    @Before
    public void setup() {
        model = new NavigableGameModel();
        listener = new NavigableGameModelChangeListener() {
            @Override
            public void cursorChanged(GameMovesModel.Node oldCursor, GameMovesModel.Node newCursor) {
                numCursorChanges++;
            }

            @Override
            public void headerModelChanged(GameHeaderModel headerModel) {
                numHeaderChanges++;
            }

            @Override
            public void moveModelChanged(GameMovesModel movesModel, GameMovesModel.Node node) {
                numMovesChanges++;
            }
        };
        model.addChangeListener(listener);

    }

    @After
    public void tearDown() {
        assertTrue(model.removeChangeListener(listener));
    }

    @Test
    public void testEmptyModel() {
        assertSame(model.cursor(), model.moves().root());
    }

    @Test
    public void testReplaceAll() {
        NavigableGameModel model1 = new NavigableGameModel();
        GameMovesModel.Node node1 = model1.moves().root().addMove(D2, D4).addMove(D7, D5);
        model1.setCursor(node1);
        assertTrue(model1.cursor().isValid());

        NavigableGameModel model2 = new NavigableGameModel();
        GameMovesModel.Node node2 = model2.moves().root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3);
        model2.setCursor(node2);
        assertTrue(model2.cursor().isValid());

        assertEquals("1.d4 d5", model1.moves().toString());
        assertEquals(new ShortMove(D7, D5), model1.cursor().lastMove());

        model1.replaceAll(model2);

        assertEquals("1.e4 e5 2.Nf3", model1.moves().toString());

        // Test that the cursor has been replaced correctly
        assertNotSame(model1.cursor(), model2.cursor());
        assertTrue(model1.cursor().isValid());
        assertEquals(new ShortMove(G1, F3), model1.cursor().lastMove());
    }

    @Test
    public void testListeners() {
        model.addMove(new ShortMove(E2, E4));
        model.addMove(new ShortMove(E7, E5));

        assertEquals(2, numCursorChanges);
        assertEquals(2, numMovesChanges);
        assertEquals(0, numHeaderChanges);

        model.goBack();
        assertEquals(3, numCursorChanges);
        assertEquals(2, numMovesChanges);

        model.header().setField("blackTeam", "some team");
        assertEquals(3, numCursorChanges);
        assertEquals(2, numMovesChanges);
        assertEquals(1, numHeaderChanges);
    }
}
