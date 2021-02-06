package se.yarin.cbhlib.games;

import org.junit.BeforeClass;
import org.junit.Test;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TextContentsModelTest {

    private static Database textDatabase;

    @BeforeClass
    public static void setupTextDb() throws IOException {
        Path textPath = Files.createTempDirectory("textdb");
        File file = ResourceLoader.materializeDatabaseStream(
                GameHeader.class,
                "text/text",
                textPath.toFile(),
                "textdb");
        textDatabase = Database.open(file);
    }

    @Test
    public void testDeserializeSimpleText() throws ChessBaseException {
        Game game = textDatabase.getGame(1);
        assertTrue(game.isGuidingText());
        TextContentsModel text = game.getTextModel().getContents();
        assertEquals("My first text", text.getTitle());
        assertTrue(text.getContents().contains("Simple text in English"));
    }

    @Test
    public void testTextMetadata() throws ChessBaseException {
        Game game1 = textDatabase.getGame(1);
        assertEquals("Mårdell, Jimmy", game1.getAnnotator().getName());

        Game game3 = textDatabase.getGame(3);
        assertEquals("Stockholm", game3.getTournament().getTitle());
        assertEquals(3, game3.getRound());

        TextHeaderModel headerModel = textDatabase.getTextModel(game3).getHeader();
        assertEquals("Stockholm", headerModel.getTournament());
        assertEquals(3, headerModel.getRound());
    }

    @Test
    public void testDeserializeMultipleLanguages() throws ChessBaseException {
        Game game = textDatabase.getGame(2);

        TextContentsModel text = game.getTextModel().getContents();
        assertTrue(text.getContents(TextLanguage.ENGLISH).contains("English"));
        assertTrue(text.getContents(TextLanguage.GERMAN).contains("Deutsch"));
        assertTrue(text.getContents(TextLanguage.FRENCH).contains("Franc"));
        assertTrue(text.getContents(TextLanguage.SPANISH).contains("Esp"));
        assertTrue(text.getContents(TextLanguage.ITALIAN).contains("Ital"));
        assertTrue(text.getContents(TextLanguage.DUTCH).contains("Ned"));
        assertTrue(text.getContents(TextLanguage.PORTUGUESE).contains("Port"));
    }

    @Test
    public void testDeserializeMultipleTitles() throws ChessBaseException {
        Game game = textDatabase.getGame(7);

        TextContentsModel text = game.getTextModel().getContents();
        assertTrue(text.getTitle(TextLanguage.ENGLISH).equals("Staunton"));
        assertTrue(text.getTitle(TextLanguage.GERMAN).equals("German title"));
        assertTrue(text.getTitle(TextLanguage.FRENCH).equals("Fra title"));
        assertTrue(text.getTitle(TextLanguage.SPANISH).equals("Esp title"));
        assertTrue(text.getTitle(TextLanguage.ITALIAN).equals("Ita title"));
        assertTrue(text.getTitle(TextLanguage.DUTCH).equals("Ned title"));
    }

    @Test
    public void testSerializeText() throws ChessBaseInvalidDataException {
        Database database = new Database();

        TextHeaderModel header = new TextHeaderModel();
        header.setTournament("Rilton Cup");
        header.setTournamentYear(2020);
        header.setAnnotator("Mårdell");
        header.setRound(1);

        TextContentsModel contents = new TextContentsModel();
        contents.setTitle("default title");
        contents.setTitle(TextLanguage.DUTCH, "dutch title");
        contents.setContents("some html");

        TextModel textModel = new TextModel(header, contents);

        Game game = database.addText(textModel);

        assertEquals(1, game.getId());
        assertTrue(game.isGuidingText());
        assertFalse(game.isDeleted());

        TournamentEntity tournament = game.getTournament();
        assertEquals(0, tournament.getId());
        assertEquals("Rilton Cup", tournament.getTitle());
        assertEquals(2020, tournament.getDate().year());
        assertEquals(1, tournament.getFirstGameId());
        assertEquals(1, tournament.getCount());

        assertEquals("", game.getSource().getTitle());
        assertEquals(1, game.getRound());
        assertEquals(0, game.getSubRound());
    }

    @Test
    public void testReserialize() throws ChessBaseException {
        for (Game game : textDatabase.getGames()) {
            if (!game.isGuidingText()) {
                continue;
            }
            TextModel textModel = game.getTextModel();
            byte[] expected = new byte[game.getMovesBlob().limit()];
            game.getMovesBlob().get(expected);
            byte[] actual = textModel.getContents().serialize().array();
            assertArrayEquals(expected, actual);
        }
    }
}
