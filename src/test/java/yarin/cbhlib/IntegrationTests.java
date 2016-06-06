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
        Assert.assertEquals("1-0", gameHeader.getResult());

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

        Assert.assertEquals("compensation", gameHeader.getResult());
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
        GamePosition position = game;
        // TODO: Test that position is correct?
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

    // Test game title
    // Test more annotations
}
