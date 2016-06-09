package yarin.cbhlib;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import yarin.cbhlib.annotations.*;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.*;

import java.io.IOException;
import java.util.List;

public class IntegrationTests {

    private Database db;

    @Before
    public void openTestDatabase() throws IOException, CBHFormatException {
        db = Database.open("src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh");
    }

    @After
    public void closeTestDatabase() {
        // TODO
    }

    @Test
    public void dbStatsIsOk() {
        // Don't make exact checks since the test database is growing
        Assert.assertTrue(db.getNumberOfGames() > 0);
        Assert.assertTrue(db.getNumberOfPlayers() > 0);
    }

    @Test
    public void simpleGame() throws IOException, CBHException {
        // Checks that the moves in a simple game is recorded correctly
        GameHeader gameHeader = db.getGameHeader(1);
        Assert.assertFalse(gameHeader.isDeleted());
        Assert.assertFalse(gameHeader.isGuidingText());
        Assert.assertEquals("Simple game", gameHeader.getWhitePlayer().getLastName());
        Assert.assertEquals("1-0", gameHeader.getResultString());

        Game game = gameHeader.getGame();
        Assert.assertFalse(game.isSetupPosition());
        Assert.assertFalse(game.isEndOfVariation());

        GamePosition currentPosition = game;
        Assert.assertEquals("e2-e4", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("e7-e5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Bf1-c4", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Nb8-c6", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Qd1-h5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Ng8-f6", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Qh5xf7", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertTrue(currentPosition.isEndOfVariation());
    }

    @Test
    public void testBasicGameMetadata() throws IOException, CBHException {
        // Checks that "all" different metadata set for a game is correct
        GameHeader gameHeader = db.getGameHeader(2);
        Assert.assertEquals("Mårdell", gameHeader.getWhitePlayer().getLastName());
        Assert.assertEquals("Jimmy", gameHeader.getWhitePlayer().getFirstName());
        Assert.assertEquals("Doe", gameHeader.getBlackPlayer().getLastName());
        Assert.assertEquals("John", gameHeader.getBlackPlayer().getFirstName());

        Assert.assertEquals("compensation", gameHeader.getResultString());
        Assert.assertEquals("Swedish Ch", gameHeader.getTournament().getTitle());

        Assert.assertEquals("A00", gameHeader.getECO());
        Assert.assertEquals(2150, gameHeader.getWhiteElo());
        Assert.assertEquals(2000, gameHeader.getBlackElo());
        Assert.assertEquals(1, gameHeader.getRound());
        Assert.assertEquals(2, gameHeader.getSubRound());
        Assert.assertEquals(new Date(2016, 5, 9), gameHeader.getPlayedDate());

        Assert.assertEquals("SK Rockaden Umeå", gameHeader.getWhiteTeam().getTitle());
        Assert.assertEquals("Test Team", gameHeader.getBlackTeam().getTitle());
        Assert.assertEquals("Test source", gameHeader.getSource().getTitle());
        Assert.assertEquals("Jimmy", gameHeader.getAnnotator().getName());

        Assert.assertEquals(new RatingDetails(true, RatingDetails.RatingType.Normal, 0), gameHeader.getWhiteRatingDetails());
        Assert.assertEquals(new RatingDetails(false, RatingDetails.RatingType.Blitz, 0x86), gameHeader.getBlackRatingDetails());

//        Assert.assertEquals("Test title", gameHeader.getGameTitle()); // Languages?
//        Assert.assertEquals("German title", gameHeader.getGameTitle()); // Languages?
    }


    @Test
    public void testSimpleAnnotations() throws IOException, CBHException {
        // Checks that the basic annotations work
        GameHeader gameHeader = db.getGameHeader(3);

        AnnotatedGame game = gameHeader.getGame();

        GamePosition position = game.getForwardPosition();

        Annotation a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.GoodMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.BadMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.ZugZwang2, ((SymbolAnnotation) a).getMoveComment());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.OnlyMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.Unclear, ((SymbolAnnotation) a).getPositionEval());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.WithInitiative, ((SymbolAnnotation) a).getPositionEval());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof TextAfterMoveAnnotation);
        Assert.assertEquals(" Capture", a.getPostText());

        position = position.getForwardPosition();
        position = position.getForwardPosition();
        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof TextBeforeMoveAnnotation);
        Assert.assertEquals("Fianchetto ", a.getPreText());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.WhiteHasDecisiveAdvantage, ((SymbolAnnotation) a).getPositionEval());

        position = position.getForwardPosition();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MovePrefix.BetterIs, ((SymbolAnnotation) a).getMovePrefix());

        /*
        position = game;
        while (!position.isEndOfVariation()) {
            for (Annotation a2 : game.getAnnotations(position)){
                if (a2.getPreText() != null) System.out.print(a2.getPreText() + " ");
            }
            System.out.print(position.getMainMove());
            for (Annotation a2 : game.getAnnotations(position)){
                if (a2.getPostText() != null) System.out.print(" " + a2.getPostText());
            }
            System.out.println();

            position = position.getForwardPosition();
        }
        */
    }


    @Test
    public void testDeletedGame() throws IOException, CBHException {
        // Test that a game is marked correctly as deleted
        GameHeader gameHeader = db.getGameHeader(4);
        Assert.assertTrue(gameHeader.isDeleted());
    }

    @Test
    public void testTournamentDetails() throws IOException {
        // Test that the tournament details are read correctly
        Tournament tournament = db.getTournament(2);
        Assert.assertEquals("Swedish Ch", tournament.getTitle());
        Assert.assertEquals("Stockholm", tournament.getPlace());
        Assert.assertEquals(new Date(2016, 5, 9), tournament.getTournamentDate());
        Assert.assertEquals(20, tournament.getCategory());
        Assert.assertEquals(2, tournament.getCount());
        Assert.assertEquals("#134", tournament.getNationString());
        Assert.assertEquals(4, tournament.getNoRounds());
        Assert.assertTrue(tournament.getTimeControl().contains(TournamentTimeControls.TournamentTimeControl.Rapid));
        Assert.assertEquals(TournamentType.KnockOut, tournament.getType());
        Assert.assertFalse(tournament.isComplete());
        Assert.assertTrue(tournament.isBoardPoints());
        Assert.assertTrue(tournament.isTeamTournament());
        Assert.assertTrue(tournament.isThreePointsWin());
    }

    @Test
    public void testSourceDetails() throws IOException {
        // Test that the details of a source is read correctly
        Source source = db.getSource(2);
        Assert.assertEquals("Test source", source.getTitle());
        Assert.assertEquals("Test publisher", source.getPublisher());
        Assert.assertEquals(new Date(2016, 5, 6), source.getPublication());
        Assert.assertEquals(new Date(2016, 5, 9), source.getSourceDate());
        Assert.assertEquals(3, source.getVersion());
        Assert.assertEquals(Source.Quality.Normal, source.getQuality());
        Assert.assertEquals(1, source.getCount());
    }

    @Test
    public void testTeamDetails() throws IOException {
        // Test that the details of a team is read correctly
        Team team = db.getTeam(2);
        Assert.assertEquals("SK Rockaden Umeå", team.getTitle());
        Assert.assertEquals(2, team.getTeamNumber());
        Assert.assertEquals(134, team.getNation());
        Assert.assertEquals(2016, team.getYear());
        Assert.assertTrue(team.isSeason());
    }

    @Test
    public void testSpecialMoves() throws IOException, CBHException {
        // Checks that special moves (castle, en passant, promition, null moves) work
        GameHeader gameHeader = db.getGameHeader(5);

        AnnotatedGame game = gameHeader.getGame();
        GamePosition position = game;
        Assert.assertFalse(position.getMainMove().isEnpassant());
        position = position.getForwardPosition(); // e4
        position = position.getForwardPosition(); // e6
        position = position.getForwardPosition(); // e5
        position = position.getForwardPosition(); // d5
        Assert.assertTrue(position.getMainMove().isEnpassant());
        position = position.getForwardPosition(); // exd6 ep
        position = position.getForwardPosition(); // Nf6
        position = position.getForwardPosition(); // d4
        position = position.getForwardPosition(); // Be7
        position = position.getForwardPosition(); // Nc3
        Assert.assertTrue(position.getMainMove().isCastle());
        Assert.assertEquals("O-O", position.getMainMove().toString());
        position = position.getForwardPosition(); // 0-0
        position = position.getForwardPosition(); // Bg5
        Assert.assertTrue(position.getMainMove() instanceof NullMove);
        position = position.getForwardPosition(); // --
        position = position.getForwardPosition(); // Qd2
        position = position.getForwardPosition(); // a6
        Assert.assertTrue(position.getMainMove().isCastle());
        Assert.assertEquals("O-O-O", position.getMainMove().toString());
        position = position.getForwardPosition(); // 0-0-0
        position = position.getForwardPosition(); // b5
        position = position.getForwardPosition(); // dxe7
        Assert.assertFalse(position.getMainMove().isCastle());
        position = position.getForwardPosition(); // Kh8
        Assert.assertEquals(Piece.PieceType.KNIGHT, position.getMainMove().getPromotionPiece());
        position.getForwardPosition(); // exd8=N
    }

    @Test
    public void testVariants() throws IOException, CBHException {
        // Checks that variants work
        GameHeader gameHeader = db.getGameHeader(6);

        AnnotatedGame game = gameHeader.getGame();
        GamePosition position = game;
        Assert.assertEquals(1, position.getMoves().size()); // [e4]
        position = position.getForwardPosition(); // e4
        Assert.assertEquals(3, position.getMoves().size()); // [c5, e6, Nf6]
        Assert.assertEquals("c7-c5", position.getMoves().get(0).toString());
        Assert.assertEquals("e7-e6", position.getMoves().get(1).toString());
        Assert.assertEquals("Ng8-f6", position.getMoves().get(2).toString());
        position = position.getForwardPosition(position.getMoves().get(1)); // e6
        Assert.assertEquals(1, position.getMoves().size()); // [d4]
        Assert.assertEquals("d2-d4", position.getMainMove().toString());
        position = position.getForwardPosition(); // d4
        position = position.getForwardPosition(); // d5
        Assert.assertEquals(3, position.getMoves().size()); // [Nc3, Nd2, e5]
        Assert.assertEquals("Nb1-c3", position.getMoves().get(0).toString());
        position = position.getForwardPosition(); // Nc3
        position = position.getForwardPosition(); // Nf6
        position = position.getForwardPosition(); // Bg5
        Assert.assertEquals("Bf8-b4", position.getMainMove().toString());
        position = position.getForwardPosition(); // Bb4
        Assert.assertTrue(position.isEndOfVariation());

        position = game;
        position = position.getForwardPosition(); // e4
        position = position.getForwardPosition(); // c5
        Assert.assertEquals("Ng1-f3", position.getMainMove().toString());
        for (int i = 0; i < 8; i++) {
            Assert.assertFalse(position.isEndOfVariation());
            position = position.getForwardPosition();
        }
        Assert.assertTrue(position.isEndOfVariation());
    }

    @Test
    public void testCommentsInVariants() throws IOException, CBHException {
        GameHeader gameHeader = db.getGameHeader(9);

        AnnotatedGame game = gameHeader.getGame();
        GamePosition position = game;

        position = position.getForwardPosition(); // e4

        TextAfterMoveAnnotation afterMoveAnnotation = game.getAnnotation(position, TextAfterMoveAnnotation.class);
        Assert.assertEquals("After first move comment", afterMoveAnnotation.getText());

        TextBeforeMoveAnnotation beforeMoveAnnotation = game.getAnnotation(position, TextBeforeMoveAnnotation.class);
        Assert.assertEquals("Pre-game comment", beforeMoveAnnotation.getText());

        position = position.getForwardPosition(); // e5
        position = position.getForwardPosition(); // Nf3
        position = position.getForwardPosition(); // Nc6

        List<Move> moves = position.getMoves();
        Assert.assertEquals(2, moves.size()); // [Bb5, Bc4]
        Assert.assertEquals(0, game.getAnnotations(position).size());
        Assert.assertEquals("Bf1-c4", moves.get(1).toString());
        position = position.getForwardPosition(moves.get(1)); // Bc4
        Assert.assertEquals(1, game.getAnnotations(position).size());
        beforeMoveAnnotation = game.getAnnotation(position, TextBeforeMoveAnnotation.class);
        Assert.assertEquals("Pre-variant comment", beforeMoveAnnotation.getText());
    }

    @Test
    public void testSetupPosition() throws IOException, CBHException {
        // Checks that setup positions work
        GameHeader gameHeader = db.getGameHeader(7);

        AnnotatedGame game = gameHeader.getGame();
        Assert.assertTrue(game.isSetupPosition());
        GamePosition currentPosition = game;

        Assert.assertEquals("8/4R3/8/2K5/P1N4P/pPbk4/1p3p2/5r2", currentPosition.getPosition().toString());

        Assert.assertEquals(63, currentPosition.getMoveNumber());
        Assert.assertEquals("Re2-f2", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals(63, currentPosition.getMoveNumber());
        Assert.assertEquals("f7-f5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals(64, currentPosition.getMoveNumber());
        Assert.assertEquals("Rf2-d2", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertEquals("Kd6-e7", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.getForwardPosition();
        Assert.assertTrue(currentPosition.isEndOfVariation());
    }

    @Test
    public void testGraphicalAnnotations() throws IOException, CBHException {
        GameHeader gameHeader = db.getGameHeader(13);
        AnnotatedGame game = gameHeader.getGame();

        GamePosition currentPosition = game;

        GraphicalSquaresAnnotation sqa = game.getAnnotation(currentPosition, GraphicalSquaresAnnotation.class);
        Assert.assertEquals(1, sqa.getSquares().size());
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, sqa.getSquares().get(0).getColor());
        Assert.assertEquals(35, sqa.getSquares().get(0).getSquare()); // e4

        currentPosition = currentPosition.getForwardPosition();
        GraphicalArrowsAnnotation aa = game.getAnnotation(currentPosition, GraphicalArrowsAnnotation.class);
        Assert.assertEquals(1, aa.getArrows().size());
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, aa.getArrows().get(0).getColor());
        Assert.assertEquals(35, aa.getArrows().get(0).getFromSquare()); // e4
        Assert.assertEquals(36, aa.getArrows().get(0).getToSquare()); // e5

        currentPosition = currentPosition.getForwardPosition();
        sqa = game.getAnnotation(currentPosition, GraphicalSquaresAnnotation.class);
        Assert.assertEquals(7, sqa.getSquares().size());
        Assert.assertEquals(Annotation.GraphicalColor.YELLOW, sqa.getSquares().get(0).getColor());
        Assert.assertEquals(10, sqa.getSquares().get(0).getSquare()); // b3
        Assert.assertEquals(Annotation.GraphicalColor.YELLOW, sqa.getSquares().get(1).getColor());
        Assert.assertEquals(18, sqa.getSquares().get(1).getSquare()); // c3
        Assert.assertEquals(Annotation.GraphicalColor.YELLOW, sqa.getSquares().get(2).getColor());
        Assert.assertEquals(36, sqa.getSquares().get(2).getSquare()); // e5
        Assert.assertEquals(Annotation.GraphicalColor.RED, sqa.getSquares().get(3).getColor());
        Assert.assertEquals(38, sqa.getSquares().get(3).getSquare()); // e7
        Assert.assertEquals(Annotation.GraphicalColor.RED, sqa.getSquares().get(4).getColor());
        Assert.assertEquals(46, sqa.getSquares().get(4).getSquare()); // f7
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, sqa.getSquares().get(5).getColor());
        Assert.assertEquals(52, sqa.getSquares().get(5).getSquare()); // g5
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, sqa.getSquares().get(6).getColor());
        Assert.assertEquals(60, sqa.getSquares().get(6).getSquare()); // h5

        aa = game.getAnnotation(currentPosition, GraphicalArrowsAnnotation.class);
        Assert.assertEquals(5, aa.getArrows().size());
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, aa.getArrows().get(0).getColor());
        Assert.assertEquals(48, aa.getArrows().get(0).getFromSquare()); // g1
        Assert.assertEquals(42, aa.getArrows().get(0).getToSquare()); // f3
        Assert.assertEquals(Annotation.GraphicalColor.YELLOW, aa.getArrows().get(1).getColor());
        Assert.assertEquals(25, aa.getArrows().get(1).getFromSquare()); // d2
        Assert.assertEquals(26, aa.getArrows().get(1).getToSquare()); // d3
        Assert.assertEquals(Annotation.GraphicalColor.RED, aa.getArrows().get(2).getColor());
        Assert.assertEquals(15, aa.getArrows().get(2).getFromSquare()); // b8
        Assert.assertEquals(21, aa.getArrows().get(2).getToSquare()); // c6
        Assert.assertEquals(Annotation.GraphicalColor.YELLOW, aa.getArrows().get(3).getColor());
        Assert.assertEquals(35, aa.getArrows().get(3).getFromSquare()); // e4
        Assert.assertEquals(36, aa.getArrows().get(3).getToSquare()); // e5
        Assert.assertEquals(Annotation.GraphicalColor.GREEN, aa.getArrows().get(4).getColor());
        Assert.assertEquals(31, aa.getArrows().get(4).getFromSquare()); // d8
        Assert.assertEquals(4, aa.getArrows().get(4).getToSquare()); // a5
    }

    @Test
    public void testPositionsAreUniqueRegardlessOfMoveNumberAndVariations() throws IOException, CBHException {
        GameHeader gameHeader = db.getGameHeader(14);

        AnnotatedGame game = gameHeader.getGame();
        GamePosition currentPosition = game;

        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        TextAfterMoveAnnotation annotation = game.getAnnotation(currentPosition, TextAfterMoveAnnotation.class);
        Assert.assertEquals("annotation1", annotation.getText());

        GamePosition branch = currentPosition;

        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        annotation = game.getAnnotation(currentPosition, TextAfterMoveAnnotation.class);
        Assert.assertEquals("annotation2", annotation.getText());

        currentPosition = branch.getForwardPosition(branch.getMoves().get(1));
        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        currentPosition = currentPosition.getForwardPosition();
        annotation = game.getAnnotation(currentPosition, TextAfterMoveAnnotation.class);
        Assert.assertEquals("annotation3", annotation.getText());
    }

    @Test
    public void testCriticalPositionAnnotations() throws IOException, CBHException {
        GameHeader gameHeader = db.getGameHeader(15);

        AnnotatedGame game = gameHeader.getGame();
        GamePosition currentPosition = game;

        for (int i = 0; i < 6; i++) currentPosition = currentPosition.getForwardPosition();
        CriticalPositionAnnotation cpa = game.getAnnotation(currentPosition, CriticalPositionAnnotation.class);
        Assert.assertEquals(CriticalPositionAnnotation.CriticalPositionType.OPENING, cpa.getType());

        for (int i = 0; i < 3; i++) currentPosition = currentPosition.getForwardPosition();
        cpa = game.getAnnotation(currentPosition, CriticalPositionAnnotation.class);
        Assert.assertEquals(CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME, cpa.getType());

        for (int i = 0; i < 4; i++) currentPosition = currentPosition.getForwardPosition();
        cpa = game.getAnnotation(currentPosition, CriticalPositionAnnotation.class);
        Assert.assertEquals(CriticalPositionAnnotation.CriticalPositionType.ENDGAME, cpa.getType());
    }

    @Test
    public void testGuidingTexts() throws IOException, CBHException {
        // Checks that guiding texts work
        GameHeader gameHeader = db.getGameHeader(8);
        Assert.assertTrue(gameHeader.isGuidingText());

        Assert.assertEquals("Swedish Ch", gameHeader.getTournament().getTitle());
        Assert.assertEquals(2, gameHeader.getRound());
        Assert.assertEquals("Jimmy", gameHeader.getAnnotator().getName());
        Assert.assertEquals("Test text", gameHeader.getWhitePlayerString());

        // TODO: Verify the actual contents here

    }



    // TODO: Test game title
    // TODO: Test more annotations
}
