package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameResult;

import static org.junit.Assert.*;

/**
 * Tests for PGN header parsing and export.
 */
public class PgnHeaderTest {

    @Test
    public void testSevenTagRoster() throws PgnFormatException {
        String pgn = """
                [Event "F/S Return Match"]
                [Site "Belgrade, Serbia JUG"]
                [Date "1992.11.04"]
                [Round "29"]
                [White "Fischer, Robert J."]
                [Black "Spassky, Boris V."]
                [Result "1/2-1/2"]

                1. e4 e5 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        GameHeaderModel header = game.header();

        assertEquals("F/S Return Match", header.getEvent());
        assertEquals("Belgrade, Serbia JUG", header.getEventSite());
        assertEquals(new Date(1992, 11, 4), header.getDate());
        assertEquals(Integer.valueOf(29), header.getRound());
        assertEquals("Fischer, Robert J.", header.getWhite());
        assertEquals("Spassky, Boris V.", header.getBlack());
        assertEquals(GameResult.DRAW, header.getResult());
    }

    @Test
    public void testOptionalHeaders() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Online"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player A"]
                [Black "Player B"]
                [Result "*"]
                [WhiteElo "2500"]
                [BlackElo "2450"]
                [ECO "C42"]
                [Annotator "Test Annotator"]
                [EventCountry "USA"]
                [TimeControl "40/7200:3600"]

                1. e4 e5 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        GameHeaderModel header = game.header();

        assertEquals(Integer.valueOf(2500), header.getWhiteElo());
        assertEquals(Integer.valueOf(2450), header.getBlackElo());
        assertEquals(new Eco("C42"), header.getEco());
        assertEquals("Test Annotator", header.getAnnotator());
        assertEquals("USA", header.getEventCountry());
        assertEquals("40/7200:3600", header.getEventTimeControl());
    }

    @Test
    public void testCustomHeaders() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]
                [CustomField1 "Value1"]
                [CustomField2 "Value2"]

                1. e4 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        GameHeaderModel header = game.header();

        assertEquals("Value1", header.getField("CustomField1"));
        assertEquals("Value2", header.getField("CustomField2"));
    }

    @Test
    public void testDateParsing() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "2024.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        Date date = game.header().getDate();

        assertEquals(2024, date.year());
        assertEquals(0, date.month());
        assertEquals(0, date.day());
    }

    @Test
    public void testRoundWithSubRound() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "5.2"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        GameHeaderModel header = game.header();

        assertEquals(Integer.valueOf(5), header.getRound());
        assertEquals(Integer.valueOf(2), header.getSubRound());
    }

    @Test
    public void testHeaderExport() throws PgnFormatException {
        GameModel game = new GameModel();
        game.header().setEvent("Test Event");
        game.header().setEventSite("Test Site");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setRound(3);
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setResult(GameResult.WHITE_WINS);
        game.header().setWhiteElo(2600);
        game.header().setBlackElo(2550);

        PgnExporter exporter = new PgnExporter();
        String pgn = exporter.exportGame(game);

        assertEquals("""
            [Event "Test Event"]
            [Site "Test Site"]
            [Date "2024.01.15"]
            [Round "3"]
            [White "Player 1"]
            [Black "Player 2"]
            [Result "1-0"]
            [WhiteElo "2600"]
            [BlackElo "2550"]
            [PlyCount "0"]
            
             1-0
            """, pgn);
    }

    @Test
    public void testHeaderRoundTrip() throws PgnFormatException {
        String originalPgn = """
                [Event "World Championship"]
                [Site "London"]
                [Date "1851.07.21"]
                [Round "1"]
                [White "Anderssen"]
                [Black "Kieseritzky"]
                [Result "1-0"]
                [WhiteElo "2600"]
                [BlackElo "2500"]
                [ECO "C33"]
                [Opening "Kings pawn opening"]

                1. e4 *
                """;

        GameModel game = PgnParser.parseGame(originalPgn);
        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game);

        // Parse again
        GameModel game2 = PgnParser.parseGame(exportedPgn);

        // Verify headers match
        assertEquals(game.header().getEvent(), game2.header().getEvent());
        assertEquals(game.header().getEventSite(), game2.header().getEventSite());
        assertEquals(game.header().getDate(), game2.header().getDate());
        assertEquals(game.header().getRound(), game2.header().getRound());
        assertEquals(game.header().getWhite(), game2.header().getWhite());
        assertEquals(game.header().getBlack(), game2.header().getBlack());
        assertEquals(game.header().getResult(), game2.header().getResult());
        assertEquals(game.header().getWhiteElo(), game2.header().getWhiteElo());
        assertEquals(game.header().getBlackElo(), game2.header().getBlackElo());
        assertEquals(game.header().getEco(), game2.header().getEco());
    }
}
