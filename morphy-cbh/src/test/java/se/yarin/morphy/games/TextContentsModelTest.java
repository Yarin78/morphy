package se.yarin.morphy.games;

import org.junit.BeforeClass;
import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.text.*;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class TextContentsModelTest {

    private static Database textDatabase;

    @BeforeClass
    public static void setupTextDb() throws IOException {
        File file = ResourceLoader.materializeDatabaseStream(Database.class, "database/text", "text");
        textDatabase = Database.open(file);
    }

    @Test
    public void testDeserializeSimpleText() throws MorphyException {
        Game game = textDatabase.getGame(1);
        assertTrue(game.guidingText());
        TextContentsModel text = game.getTextModel().contents();
        assertEquals("My first text", text.getTitle());
        assertTrue(text.getContents().contains("Simple text in English"));
    }

    @Test
    public void testTextMetadata() throws MorphyException {
        Game game1 = textDatabase.getGame(1);
        assertEquals("Mårdell, Jimmy", game1.annotator().name());

        Game game3 = textDatabase.getGame(3);
        assertEquals("Stockholm", game3.tournament().title());
        assertEquals(3, game3.round());

        TextHeaderModel headerModel = textDatabase.getTextModel(game3).header();
        assertEquals("Stockholm", headerModel.tournament());
        assertEquals(3, headerModel.round());
    }

    @Test
    public void testDeserializeMultipleLanguages() throws MorphyException {
        Game game = textDatabase.getGame(2);

        TextContentsModel text = game.getTextModel().contents();
        assertTrue(text.getContents(TextLanguage.ENGLISH).contains("English"));
        assertTrue(text.getContents(TextLanguage.GERMAN).contains("Deutsch"));
        assertTrue(text.getContents(TextLanguage.FRENCH).contains("Franc"));
        assertTrue(text.getContents(TextLanguage.SPANISH).contains("Esp"));
        assertTrue(text.getContents(TextLanguage.ITALIAN).contains("Ital"));
        assertTrue(text.getContents(TextLanguage.DUTCH).contains("Ned"));
        assertTrue(text.getContents(TextLanguage.PORTUGUESE).contains("Port"));
    }

    @Test
    public void testDeserializeMultipleTitles() throws MorphyException {
        Game game = textDatabase.getGame(7);

        TextContentsModel text = game.getTextModel().contents();
        assertTrue(text.getTitle(TextLanguage.ENGLISH).equals("Staunton"));
        assertTrue(text.getTitle(TextLanguage.GERMAN).equals("German title"));
        assertTrue(text.getTitle(TextLanguage.FRENCH).equals("Fra title"));
        assertTrue(text.getTitle(TextLanguage.SPANISH).equals("Esp title"));
        assertTrue(text.getTitle(TextLanguage.ITALIAN).equals("Ita title"));
        assertTrue(text.getTitle(TextLanguage.DUTCH).equals("Ned title"));
    }

    @Test
    public void testSerializeText() throws MorphyInvalidDataException {
        Database database = new Database();

        TextHeaderModel header = ImmutableTextHeaderModel.builder()
            .tournament("Rilton Cup")
            .tournamentYear(2020)
            .annotator("Mårdell")
            .round(1)
            .build();

        TextContentsModel contents = new TextContentsModel();
        contents.setTitle("default title");
        contents.setTitle(TextLanguage.DUTCH, "dutch title");
        contents.setContents("some html");

        TextModel textModel = ImmutableTextModel.builder().header(header).contents(contents).build();

        Game game = null; //database.addText(textModel);

        assertEquals(1, game.id());
        assertTrue(game.guidingText());
        assertFalse(game.deleted());

        Tournament tournament = game.tournament();
        assertEquals(0, tournament.id());
        assertEquals("Rilton Cup", tournament.title());
        assertEquals(2020, tournament.date().year());
        assertEquals(1, tournament.firstGameId());
        assertEquals(1, tournament.count());

        assertEquals("", game.source().title());
        assertEquals(1, game.round());
        assertEquals(0, game.subRound());
    }

    @Test
    public void testReserialize() throws MorphyException {
        GameSearcher gameSearcher = new GameSearcher(textDatabase);
        for (Game game : gameSearcher.getAll()) {
            if (!game.guidingText()) {
                continue;
            }
            TextModel textModel = game.getTextModel();
            byte[] expected = new byte[game.getMovesBlob().limit()];
            game.getMovesBlob().get(expected);
            byte[] actual = textModel.contents().serialize().array();
            assertArrayEquals(expected, actual);
        }
    }
}
