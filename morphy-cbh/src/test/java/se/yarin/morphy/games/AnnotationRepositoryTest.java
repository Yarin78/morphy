package se.yarin.morphy.games;

import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.games.annotations.ImmutableTextBeforeMoveAnnotation;
import se.yarin.morphy.games.annotations.SymbolAnnotation;
import se.yarin.morphy.storage.BlobStorageHeader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class AnnotationRepositoryTest {
    private GameMovesModel testGame() {
        GameMovesModel model = new GameMovesModel();
        model.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3).addMove(B8, C6);
        return model;
    }

    private GameMovesModel testGameWithAnnotations() {
        GameMovesModel model = testGame();
        model.root().mainNode().addAnnotation(ImmutableTextAfterMoveAnnotation.of("testing"));
        model.root().mainNode().mainNode().addAnnotation(SymbolAnnotation.of(NAG.GOOD_MOVE));
        return model;
    }

    private GameMovesModel testGameWithMoreAnnotations() {
        GameMovesModel model = testGameWithAnnotations();
        model.root().addAnnotation(ImmutableTextBeforeMoveAnnotation.of("so it begins"));
        return model;
    }

    @Test
    public void putAndGetAnnotation() {
        AnnotationRepository repo = new AnnotationRepository();
        long offset = repo.putAnnotations(1, 0, testGameWithAnnotations());
        assertEquals(BlobStorageHeader.DEFAULT_SERIALIZED_HEADER_SIZE, offset);

        GameMovesModel game = testGame();
        repo.getAnnotations(game, offset);
        assertEquals(testGameWithAnnotations().root().toSAN(), game.root().toSAN());
    }

    @Test(expected = MorphyInvalidDataException.class)
    public void putGameBeyondEnd() {
        AnnotationRepository repo = new AnnotationRepository();
        repo.putAnnotations(1, 100, testGameWithAnnotations());
    }

    @Test
    public void replaceGameWithMoreAnnotations() {
        AnnotationRepository repo = new AnnotationRepository();
        long ofs1 = repo.putAnnotations(1, 0, testGameWithAnnotations());
        long ofs2 = repo.putAnnotations(2, 0, testGameWithAnnotations());
        assertTrue(ofs2 > ofs1);

        int delta = repo.preparePutBlob(ofs1, ofs1, testGameWithMoreAnnotations());
        assertTrue(delta > 0);
        repo.putAnnotations(1, ofs1, testGameWithMoreAnnotations());

        GameMovesModel firstGame = testGame();
        repo.getAnnotations(firstGame, ofs1);
        assertEquals(testGameWithMoreAnnotations().root().toSAN(), firstGame.root().toSAN());

        GameMovesModel movedGame = testGame();
        repo.getAnnotations(movedGame, ofs2 + delta);
        assertEquals(testGameWithAnnotations().root().toSAN(), movedGame.root().toSAN());
    }

    @Test
    public void addAnnotationToGameWithoutAnnotations() {
        // A difference between MoveRepository and AnnotationRepository is that games might
        // not even exist in the latter, so there's a slight difference when annotations are
        // added for the first time to a game

        AnnotationRepository repo = new AnnotationRepository();
        long ofs1 = repo.putAnnotations(1, 0, testGame());
        long ofs2 = repo.putAnnotations(2, 0, testGameWithAnnotations());
        assertEquals(0, ofs1);
        assertTrue(ofs2 > 0);

        int delta = repo.preparePutBlob(0, ofs2, testGameWithMoreAnnotations());
        assertTrue(delta > 0);
        ofs1 = repo.putAnnotations(1, ofs2, testGameWithMoreAnnotations());

        GameMovesModel firstGame = testGame();
        repo.getAnnotations(firstGame, ofs1);
        assertEquals(testGameWithMoreAnnotations().root().toSAN(), firstGame.root().toSAN());

        GameMovesModel movedGame = testGame();
        repo.getAnnotations(movedGame, ofs2 + delta);
        assertEquals(testGameWithAnnotations().root().toSAN(), movedGame.root().toSAN());
    }
}
