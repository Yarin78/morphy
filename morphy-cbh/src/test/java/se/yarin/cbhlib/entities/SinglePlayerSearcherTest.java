package se.yarin.cbhlib.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.games.GameHeader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SinglePlayerSearcherTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private PlayerBase playerBase;

    @Before
    public void setupEntityTest() throws IOException {
        File playerFile = ResourceLoader.materializeStream(
                GameHeader.class.getResourceAsStream("World-ch/World-ch.cbp"),
                folder.newFile("World-ch.cp"));
        playerBase = PlayerBase.openInMemory(playerFile);
    }

    @Test
    public void testNameParser() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "Carlsen, Magnus", true, true);
        assertEquals("Carlsen", searcher.getLastName());
        assertEquals("Magnus", searcher.getFirstName());

        searcher = new SinglePlayerSearcher(playerBase, "Garry Kasparov", true, true);
        assertEquals("Kasparov", searcher.getLastName());
        assertEquals("Garry", searcher.getFirstName());

        searcher = new SinglePlayerSearcher(playerBase, "Maxime Vachier Lagrave", true, true);
        assertEquals("Vachier Lagrave", searcher.getLastName());
        assertEquals("Maxime", searcher.getFirstName());

        searcher = new SinglePlayerSearcher(playerBase, "  c  d ,  a   b ", true, true);
        assertEquals("c  d", searcher.getLastName());
        assertEquals("a   b", searcher.getFirstName());

        searcher = new SinglePlayerSearcher(playerBase, "foo,bar,xyz", true, true);
        assertEquals("foo", searcher.getLastName());
        assertEquals("bar,xyz", searcher.getFirstName());
    }

    @Test
    public void testCaseSensitiveExactMatch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "Carlsen, Magnus", true, true);

        assertTrue(searcher.matches(new PlayerEntity("Carlsen", "Magnus")));
        assertFalse(searcher.matches(new PlayerEntity("Carlsen", "M")));
        assertFalse(searcher.matches(new PlayerEntity("carlsen", "Magnus")));
    }

    @Test
    public void testCaseInsensitiveExactMatch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "garry kasparov", false, true);

        assertTrue(searcher.matches(new PlayerEntity("Kasparov", "Garry")));
        assertTrue(searcher.matches(new PlayerEntity("kasparov", "gaRRy")));
        assertFalse(searcher.matches(new PlayerEntity("Kasparow", "garry")));
        assertFalse(searcher.matches(new PlayerEntity("kasparov", "")));
    }

    @Test
    public void testCaseSensitivePrefixMatch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "Carlsen, M", true, false);

        assertTrue(searcher.matches(new PlayerEntity("Carlsen", "Magnus")));
        assertFalse(searcher.matches(new PlayerEntity("carlsen", "Magnus")));
        assertTrue(searcher.matches(new PlayerEntity("Carlsen", "Maud")));

        searcher = new SinglePlayerSearcher(playerBase, "Carl", true, false);
        assertTrue(searcher.matches(new PlayerEntity("Carlsen", "Magnus")));
        assertTrue(searcher.matches(new PlayerEntity("Carlsson", "Pontus")));
        assertFalse(searcher.matches(new PlayerEntity("Johansson", "Carl")));
        assertTrue(searcher.matches(new PlayerEntity("Carl", "")));
        assertFalse(searcher.matches(new PlayerEntity("carl", "")));
    }

    @Test
    public void testCaseInsensitivePrefixMatch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "carlsen", false, false);

        assertTrue(searcher.matches(new PlayerEntity("Carlsen", "Magnus")));
        assertFalse(searcher.matches(new PlayerEntity("Carl", "")));

        searcher = new SinglePlayerSearcher(playerBase, "foo", false, false);

        assertTrue(searcher.matches(new PlayerEntity("FoOBaR", "")));
        assertTrue(searcher.matches(new PlayerEntity("foor", "xyz")));
        assertFalse(searcher.matches(new PlayerEntity("fo", "foo")));
        assertFalse(searcher.matches(new PlayerEntity("fop", "")));
    }

    @Test
    public void testSearch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "Car", true, false);

        assertEquals(2, searcher.search().count());  // Carlsen, Caruana
        assertTrue(searcher.search().allMatch(hit -> hit.getPlayer().getLastName().startsWith("Car")));
    }

    @Test
    public void testQuickSearch() {
        SinglePlayerSearcher searcher = new SinglePlayerSearcher(playerBase, "Car", true, false);
        List<PlayerEntity> playerEntities = searcher.quickSearch();
        assertEquals(2, playerEntities.size());
        List<String> names = playerEntities.stream().map(PlayerEntity::getFullName).sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("Carlsen, Magnus", "Caruana, Fabiano"), names);
    }
}
