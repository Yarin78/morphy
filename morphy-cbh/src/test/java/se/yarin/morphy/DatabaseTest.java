package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DatabaseTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private int countDatabaseFiles(@NotNull File file) {
        String baseName = CBUtil.baseName(file).toLowerCase(Locale.ROOT);
        return file.getParentFile().listFiles((dir, name) -> name.toLowerCase().startsWith(baseName)).length;
    }

    @Test
    public void openVeryOldDatabaseInReadOnly() throws IOException {
        // Open a database with only the minimum files, check that it works and nothing new is created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");
        int numFiles = countDatabaseFiles(file);

        Database.open(file, DatabaseMode.READ_ONLY);

        assertEquals(numFiles, countDatabaseFiles(file));
    }

    @Test
    public void openVeryOldDatabaseInMemory() throws IOException {
        // Open a database with only the minimum files; make sure no new files are created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");

        int numFiles = countDatabaseFiles(file);

        Database.open(file, DatabaseMode.IN_MEMORY);

        assertEquals(numFiles, countDatabaseFiles(file));
    }

    @Test
    public void openVeryOldDatabase() throws IOException {
        // Open a database with only the minimum files, check that it works and that the missing files are created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");

        int numFiles = countDatabaseFiles(file);
        assertFalse(CBUtil.fileWithExtension(file, ".cbj").exists());

        Database.open(file, DatabaseMode.READ_WRITE);

        assertTrue(CBUtil.fileWithExtension(file, ".cbj").exists());
        assertTrue(countDatabaseFiles(file) > numFiles);
    }

    @Test(expected = IOException.class)
    public void openDatabaseWithMissingEssentialFiles() throws IOException {
        // Try opening the database without the source index should fail
        List<String> extensions = Database.MANDATORY_EXTENSIONS.stream().filter(x -> !x.equals(".cbs")).collect(Collectors.toList());
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2", extensions);
        Database.open(file, DatabaseMode.READ_ONLY);
    }

    @Test
    public void openOldDatabaseInReadOnly() throws IOException {
        // Open a database with old version of cbj and cbtt file, check that it works and files are not upgraded
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");
        int numFiles = countDatabaseFiles(file);
        long oldExtendedHeaderFileSize = CBUtil.fileWithExtension(file, ".cbj").length();
        long oldTournamentExtraFileSize = CBUtil.fileWithExtension(file, ".cbtt").length();

        Database.open(file, DatabaseMode.READ_ONLY);

        assertEquals(numFiles, countDatabaseFiles(file));
        assertEquals(oldExtendedHeaderFileSize, CBUtil.fileWithExtension(file, ".cbj").length());
        assertEquals(oldTournamentExtraFileSize, CBUtil.fileWithExtension(file, ".cbtt").length());
    }

    @Test
    public void openOldDatabase() throws IOException {
        // Open a database with old version of cbj and no cbtt file, check that it works and files are upgraded
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");
        int numFiles = countDatabaseFiles(file);
        long oldFileSize = CBUtil.fileWithExtension(file, ".cbj").length();
        assertFalse(CBUtil.fileWithExtension(file, ".cbtt").exists());

        Database.open(file, DatabaseMode.READ_WRITE);

        assertEquals(numFiles + 1, countDatabaseFiles(file));  // the .cbtt file got created
        assertTrue(oldFileSize < CBUtil.fileWithExtension(file, ".cbj").length());
        assertTrue(CBUtil.fileWithExtension(file, ".cbtt").exists());
    }

    @Test
    public void createDatabase() throws IOException {
        File file = folder.newFile("new_db.cbh");
        file.delete();

        assertEquals(0, countDatabaseFiles(file));

        Database.create(file, false);

        assertEquals(11, countDatabaseFiles(file));
    }

    @Test(expected = IOException.class)
    public void createDatabaseButExists() throws IOException {
        File file = folder.newFile("new_db.cbh");

        assertEquals(1, countDatabaseFiles(file));

        Database.create(file, false);
    }

    @Test(expected = IOException.class)
    public void createDatabaseStrayFileExists() throws IOException {
        folder.newFile("my-Database.stray");
        File file = folder.newFile("MY-database.cbh");
        file.delete();

        assertEquals(1, countDatabaseFiles(file));

        Database.create(file, false);
    }

    @Test
    public void createAndOverwriteDatabase() throws IOException {
        File file = folder.newFile("new_db.cbh");
        File secondFile = folder.newFile("new_db.test");

        assertEquals(2, countDatabaseFiles(file));

        Database.create(file, true);

        assertEquals(11, countDatabaseFiles(file));
    }

    @Test
    public void deleteDatabase() throws IOException {
        File file = ResourceLoader.materializeStreamPath(Database.class, "database/World-ch");
        assertEquals(18, file.listFiles().length);

        Database.delete(new File(file, "World-ch.cbh"));

        assertEquals(0, file.listFiles().length);
    }

    @Test
    public void deletePartialDeletedDatabase() throws IOException {
        File file = ResourceLoader.materializeStreamPath(Database.class, "database/World-ch");
        File cbhFile = new File(file, "World-ch.cbh");
        cbhFile.delete();
        assertEquals(17, file.listFiles().length);

        Database.delete(cbhFile);

        assertEquals(0, file.listFiles().length);
    }


    @Test
    public void getSingleGame() {
        Database database = ResourceLoader.openWorldChDatabase();

        Game game = database.getGame(73);
        assertEquals("Chigorin, Mikhail Ivanovich", game.white().getFullName());
        assertEquals("Steinitz, William", game.black().getFullName());
        assertEquals("World-ch04 Steinitz-Chigorin +10-8=5", game.tournament().title());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getGame0() {
        Database database = ResourceLoader.openWorldChDatabase();
        database.getGame(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getSingleMissingGame() {
        Database database = ResourceLoader.openWorldChDatabase();
        database.getGame(100000);
    }

    // TODO: Some tests when opening a database where the filenames have different casing
    // This is not uncommon on Windows system

}
