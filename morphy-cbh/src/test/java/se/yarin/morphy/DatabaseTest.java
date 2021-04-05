package se.yarin.morphy;

import org.junit.Test;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DatabaseTest {

    private int countDatabaseFiles(File file) {
        return file.getParentFile().listFiles().length;
    }

    @Test
    public void openVeryOldDatabaseInReadOnly() throws IOException {
        // Open a database with only the minimum files, check that it works and nothing new is created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");
        int numFiles = countDatabaseFiles(file);

        Database.open(file, Set.of(READ));

        assertEquals(numFiles, countDatabaseFiles(file));
    }

    @Test
    public void openVeryOldDatabase() throws IOException {
        // Open a database with only the minimum files, check that it works and that the missing files are created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");

        int numFiles = countDatabaseFiles(file);
        assertFalse(CBUtil.fileWithExtension(file, ".cbj").exists());

        Database.open(file, Set.of(READ, WRITE));

        assertTrue(CBUtil.fileWithExtension(file, ".cbj").exists());
        assertTrue(countDatabaseFiles(file) > numFiles);
    }

    @Test
    public void openOldDatabaseInMemory() {
        // Open a database with only the minimum files; make sure no new files are created

    }

    @Test(expected = IOException.class)
    public void openDatabaseWithMissingEssentialFiles() throws IOException {
        // Try opening the database without the source index should fail
        List<String> extensions = Database.MANDATORY_EXTENSIONS.stream().filter(x -> !x.equals(".cbs")).collect(Collectors.toList());
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2", extensions);
        Database.open(file, Set.of(READ));
    }

    @Test
    public void openOldDatabaseInReadOnly() throws IOException {
        // Open a database with old version of cbj and cbtt file, check that it works and files are not upgraded
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");
        int numFiles = countDatabaseFiles(file);
        long oldExtendedHeaderFileSize = CBUtil.fileWithExtension(file, ".cbj").length();
        long oldTournamentExtraFileSize = CBUtil.fileWithExtension(file, ".cbtt").length();

        Database.open(file, Set.of(READ));

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

        Database.open(file, Set.of(READ, WRITE));

        assertEquals(numFiles, countDatabaseFiles(file));
        assertTrue(oldFileSize < CBUtil.fileWithExtension(file, ".cbj").length());
        assertTrue(CBUtil.fileWithExtension(file, ".cbtt").exists());
    }

    @Test
    public void createDatabase() {

    }

    @Test(expected = IOException.class)
    public void createDatabaseButExists() {

    }

    @Test
    public void createAndOverwriteDatabase() {

    }

    @Test
    public void deleteDatabase() {

    }

}
