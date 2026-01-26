package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameResult;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Basic smoke tests for PGN parsing and export.
 * For comprehensive tests, see:
 * - PgnHeaderTest - header parsing and export
 * - PgnMoveTreeTest - move tree, variations, and annotations
 * - PgnRoundTripTest - round-trip testing and random games
 * - PgnEdgeCasesTest - edge cases and special positions
 */
public class PgnParserTest {

    @Test
    public void testBasicParsing() throws PgnFormatException {
        String pgn = """
                [Event "Test Event"]
                [Site "Test Site"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """;

        GameModel game = new PgnParser().parseGame(pgn);

        assertNotNull(game);
        assertEquals("Test Event", game.header().getEvent());
        assertEquals("Test Site", game.header().getEventSite());
        assertEquals("Player 1", game.header().getWhite());
        assertEquals("Player 2", game.header().getBlack());
        assertEquals(GameResult.WHITE_WINS, game.header().getResult());
        assertEquals(6, game.moves().countPly(false));
    }

    @Test
    public void testBasicParsingExportRoundTrip() throws PgnFormatException {
        String pgn = """
                [Event "Test Event"]
                [Site "Test Site"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 e5 (1... c5) 2. Nf3 (2. Nc3 $3) 2... Nc6 { Black forfeited } 1-0
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        PgnExporter exporter = new PgnExporter(PgnFormatOptions.DEFAULT_WITHOUT_PLYCOUNT);
        String exported = exporter.exportGame(game);
        assertEquals(pgn, exported);
    }

    @Test
    public void testParseMultipleGames() {
        String pgn = """
                [Event "Test Event"]
                [Site "Test Site"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 e5 (1... c5) 2. Nf3 (2. Nc3 $3) Nc6 { Black forfeited } 1-0
                
                [Event "WorldCh"]
                [Site "Somewhere"]
                [Date "2025.02.13"]
                [Round "2"]
                [White "Kasparov"]
                [Black "Anand"]
                [Result "1/2-1/2"]

                1. d4 d5 1/2-1/2
                """;
        var games = new PgnParser().parseGames(new StringReader(pgn)).toList();

        assertEquals(2, games.size());

        assertEquals("Player 1", games.get(0).header().getWhite());
        assertEquals("e4", games.get(0).moves().root().mainMove().toSAN());

        assertEquals("Anand", games.get(1).header().getBlack());
        assertEquals("d4", games.get(1).moves().root().mainMove().toSAN());
    }
}
