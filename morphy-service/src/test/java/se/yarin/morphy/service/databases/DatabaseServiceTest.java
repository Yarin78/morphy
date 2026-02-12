package se.yarin.morphy.service.databases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.service.config.DatabaseConfig;

class DatabaseServiceTest {

  @TempDir Path tempDir;

  private DatabaseService service;

  /**
   * Helper to create a test database config file
   */
  private File createConfigFile(String content) throws IOException {
    File configFile = tempDir.resolve("databases.json").toFile();
    Files.writeString(configFile.toPath(), content);
    return configFile;
  }

  @Nested
  @DisplayName("Initialization")
  class InitializationTests {

    @Test
    @DisplayName("should initialize without config file in in-memory mode")
    void init_NoConfigFile() {
      DatabaseService testService = new DatabaseService("", 600000L, null, null);

      assertDoesNotThrow(() -> testService.init());
      assertTrue(testService.getAllDatabase().isEmpty());
    }

    @Test
    @DisplayName("should throw exception if config file doesn't exist")
    void init_ConfigFileDoesNotExist() {
      DatabaseService testService =
          new DatabaseService("/nonexistent/path/databases.json", 600000L, null, null);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> testService.init());
      assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("should load databases from config file")
    void init_LoadFromConfigFile() throws Exception {
      File dbFile1 = tempDir.resolve("test1.cbh").toFile();
      File dbFile2 = tempDir.resolve("test2.cbh").toFile();

      String config =
          String.format(
              """
              {
                "db1": {
                  "displayName": "Database 1",
                  "path": "%s"
                },
                "db2": {
                  "displayName": "Database 2",
                  "path": "%s"
                }
              }
              """,
              dbFile1.getAbsolutePath().replace("\\", "\\\\"),
              dbFile2.getAbsolutePath().replace("\\", "\\\\"));

      File configFile = createConfigFile(config);
      DatabaseService testService =
          new DatabaseService(configFile.getAbsolutePath(), 600000L, null, null);

      testService.init();

      List<DatabaseDto> databases = testService.getAllDatabase();
      assertEquals(2, databases.size());
      assertEquals("db1", databases.get(0).id());
      assertEquals("Database 1", databases.get(0).displayName());
    }

    @Test
    @DisplayName("should handle empty config file")
    void init_EmptyConfig() throws Exception {
      File configFile = createConfigFile("{}");
      DatabaseService testService =
          new DatabaseService(configFile.getAbsolutePath(), 600000L, null, null);

      testService.init();

      assertTrue(testService.getAllDatabase().isEmpty());
    }

    @Test
    @DisplayName("should throw exception if config path is a directory")
    void init_ConfigPathIsDirectory() {
      File dir = tempDir.resolve("config-dir").toFile();
      dir.mkdir();

      DatabaseService testService = new DatabaseService(dir.getAbsolutePath(), 600000L, null, null);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> testService.init());
      assertTrue(exception.getMessage().contains("not a file"));
    }
  }

  @Nested
  @DisplayName("Database Registration")
  class RegistrationTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      service.init();
    }

    @Test
    @DisplayName("should register existing database successfully")
    void registerDatabase_Success() throws Exception {
      File dbFile = tempDir.resolve("existing.cbh").toFile();

      // Create a real database file
      Database db = Database.create(dbFile, false);
      db.close();

      assertTrue(dbFile.exists());

      service.registerDatabase("test-db", "Test Database", dbFile.getAbsolutePath());

      List<DatabaseDto> databases = service.getAllDatabase();
      assertEquals(1, databases.size());
      assertEquals("test-db", databases.get(0).id());
      assertEquals("Test Database", databases.get(0).displayName());
    }

    @Test
    @DisplayName("should reject duplicate database ID")
    void registerDatabase_DuplicateId() throws Exception {
      File dbFile1 = tempDir.resolve("db1.cbh").toFile();
      File dbFile2 = tempDir.resolve("db2.cbh").toFile();

      Database.create(dbFile1, false).close();
      Database.create(dbFile2, false).close();

      service.registerDatabase("test-db", "Database 1", dbFile1.getAbsolutePath());

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.registerDatabase("test-db", "Database 2", dbFile2.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("should reject registration of non-existent database")
    void registerDatabase_FileDoesNotExist() {
      File dbFile = tempDir.resolve("nonexistent.cbh").toFile();

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("should reject empty database ID")
    void registerDatabase_EmptyId() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      dbFile.createNewFile();

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.registerDatabase("", "Test", dbFile.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("cannot be empty"));
    }

    @Test
    @DisplayName("should reject registration outside allowed paths")
    void registerDatabase_PathNotAllowed() throws Exception {
      File otherDir = Files.createTempDirectory("other").toFile();
      File dbFile = new File(otherDir, "test.cbh");

      Database.create(dbFile, false).close();

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("not allowed"));
    }
  }

  @Nested
  @DisplayName("Database Creation")
  class CreationTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 600000L, null, List.of(tempDir.toString()));
      service.init();
    }

    @Test
    @DisplayName("should create new database successfully")
    void createDatabase_Success() throws Exception {
      File dbFile = tempDir.resolve("new.cbh").toFile();

      try (MockedStatic<Database> dbMock = mockStatic(Database.class)) {
        Database mockDb = mock(Database.class);
        dbMock.when(() -> Database.create(eq(dbFile), eq(false))).thenReturn(mockDb);

        service.createDatabase("new-db", "New Database", dbFile.getAbsolutePath());

        dbMock.verify(() -> Database.create(eq(dbFile), eq(false)));
        verify(mockDb).close();
      }

      List<DatabaseDto> databases = service.getAllDatabase();
      assertEquals(1, databases.size());
      assertEquals("new-db", databases.get(0).id());
    }

    @Test
    @DisplayName("should create parent directories if needed")
    void createDatabase_CreateParentDirs() throws Exception {
      File subDir = tempDir.resolve("subdir").toFile();
      File dbFile = new File(subDir, "new.cbh");

      // Create a service with the subdirectory in allowed create paths
      DatabaseService testService =
          new DatabaseService("", 600000L, null, List.of(tempDir.toString(), subDir.toString()));
      testService.init();

      try (MockedStatic<Database> dbMock = mockStatic(Database.class)) {
        Database mockDb = mock(Database.class);
        dbMock.when(() -> Database.create(eq(dbFile), eq(false))).thenReturn(mockDb);

        testService.createDatabase("new-db", "New Database", dbFile.getAbsolutePath());

        assertTrue(subDir.exists());
        assertTrue(subDir.isDirectory());
      }
    }

    @Test
    @DisplayName("should reject creation in non-allowed path")
    void createDatabase_PathNotAllowed() throws Exception {
      File otherDir = Files.createTempDirectory("other").toFile();
      File dbFile = new File(otherDir, "test.cbh");

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.createDatabase("test-db", "Test", dbFile.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("not allowed"));
    }

    @Test
    @DisplayName("should reject duplicate database ID")
    void createDatabase_DuplicateId() throws Exception {
      File dbFile1 = tempDir.resolve("db1.cbh").toFile();
      File dbFile2 = tempDir.resolve("db2.cbh").toFile();

      try (MockedStatic<Database> dbMock = mockStatic(Database.class)) {
        Database mockDb = mock(Database.class);
        dbMock.when(() -> Database.create(any(File.class), eq(false))).thenReturn(mockDb);

        service.createDatabase("test-db", "Database 1", dbFile1.getAbsolutePath());

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createDatabase("test-db", "Database 2", dbFile2.getAbsolutePath()));

        assertTrue(exception.getMessage().contains("already exists"));
      }
    }
  }

  @Nested
  @DisplayName("Database Unregistration")
  class UnregistrationTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      service.init();
    }

    @Test
    @DisplayName("should unregister database successfully")
    void unregisterDatabase_Success() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();

      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());
      assertEquals(1, service.getAllDatabase().size());

      service.unregisterDatabase("test-db");

      assertTrue(service.getAllDatabase().isEmpty());
      assertTrue(dbFile.exists(), "Database file should not be deleted");
    }

    @Test
    @DisplayName("should close database before unregistering")
    void unregisterDatabase_ClosesDatabase() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Force database to open by accessing it
      service.withReadTransaction("test-db", txn -> null);

      // Unregister should not throw exception
      assertDoesNotThrow(() -> service.unregisterDatabase("test-db"));

      // Verify database is no longer accessible
      assertEquals(0, service.getAllDatabase().size());
      assertNull(service.getDatabaseConfig("test-db"));
    }

    @Test
    @DisplayName("should reject unregistering unknown database")
    void unregisterDatabase_UnknownDatabase() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> service.unregisterDatabase("unknown-db"));

      assertTrue(exception.getMessage().contains("Unknown database ID"));
    }
  }

  @Nested
  @DisplayName("Transaction Operations")
  class TransactionTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      service.init();
    }

    @Test
    @DisplayName("should execute read transaction successfully")
    void withReadTransaction_Success() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Execute a real transaction and verify it works
      String result =
          service.withReadTransaction(
              "test-db",
              txn -> {
                assertNotNull(txn);
                return "success";
              });

      assertEquals("success", result);
    }

    @Test
    @DisplayName("should execute write transaction and commit")
    void withWriteTransaction_Success() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Execute a real write transaction - should not throw exception
      assertDoesNotThrow(
          () ->
              service.withWriteTransaction(
                  "test-db",
                  txn -> {
                    assertNotNull(txn);
                  }));

      // Verify database is still accessible after write
      assertDoesNotThrow(() -> service.withReadTransaction("test-db", txn -> null));
    }

    @Test
    @DisplayName("should execute write transaction with return value")
    void withWriteTransactionWithReturn_Success() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Execute a write transaction with return value
      Integer result =
          service.withWriteTransaction(
              "test-db",
              txn -> {
                assertNotNull(txn);
                return 42;
              });

      assertEquals(42, result);
    }

    @Test
    @DisplayName("should throw exception for unknown database")
    void transaction_UnknownDatabase() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.withReadTransaction("unknown-db", txn -> null));

      assertTrue(exception.getMessage().contains("Unknown database ID"));
    }
  }

  @Nested
  @DisplayName("Database Lifecycle")
  class LifecycleTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 100L, List.of(tempDir.toString()), null); // Short interval for testing
      service.init();
    }

    @Test
    @DisplayName("should lazy-open database on first access")
    void lazyOpening() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Access the database - this should trigger lazy opening without exception
      assertDoesNotThrow(() -> service.withReadTransaction("test-db", txn -> {
        assertNotNull(txn);
        return null;
      }));

      // Verify database is now accessible
      assertNotNull(service.getDatabaseConfig("test-db"));
    }

    @Test
    @DisplayName("should manually refresh database")
    void manualRefresh() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();
      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test", dbFile.getAbsolutePath());

      // Open database by accessing it
      service.withReadTransaction("test-db", txn -> null);

      // Manually refresh - should not throw exception
      assertDoesNotThrow(() -> service.refreshDatabase("test-db"));

      // Verify database is still accessible after refresh
      service.withReadTransaction("test-db", txn -> null);
    }

    @Test
    @DisplayName("should reject refresh of unknown database")
    void refreshDatabase_UnknownDatabase() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> service.refreshDatabase("unknown-db"));

      assertTrue(exception.getMessage().contains("Unknown database ID"));
    }
  }

  @Nested
  @DisplayName("Configuration Persistence")
  class ConfigPersistenceTests {

    @Test
    @DisplayName("should persist registration to config file")
    void registerDatabase_PersistsToFile() throws Exception {
      File configFile = tempDir.resolve("databases.json").toFile();
      configFile.createNewFile();
      Files.writeString(configFile.toPath(), "{}");

      DatabaseService testService =
          new DatabaseService(
              configFile.getAbsolutePath(), 600000L, List.of(tempDir.toString()), null);
      testService.init();

      File dbFile = tempDir.resolve("test.cbh").toFile();

      Database.create(dbFile, false).close();

      testService.registerDatabase("test-db", "Test Database", dbFile.getAbsolutePath());

      // Verify config file was updated
      String configContent = Files.readString(configFile.toPath());
      assertTrue(configContent.contains("test-db"));
      assertTrue(configContent.contains("Test Database"));
    }

    @Test
    @DisplayName("should persist unregistration to config file")
    void unregisterDatabase_PersistsToFile() throws Exception {
      File configFile = tempDir.resolve("databases.json").toFile();
      File dbFile = tempDir.resolve("test.cbh").toFile();

      String initialConfig =
          String.format(
              """
              {
                "test-db": {
                  "displayName": "Test",
                  "path": "%s"
                }
              }
              """,
              dbFile.getAbsolutePath().replace("\\", "\\\\"));

      Files.writeString(configFile.toPath(), initialConfig);

      Database.create(dbFile, false).close();

      DatabaseService testService =
          new DatabaseService(
              configFile.getAbsolutePath(), 600000L, List.of(tempDir.toString()), null);
      testService.init();

      testService.unregisterDatabase("test-db");

      // Verify config file was updated (should be empty)
      String configContent = Files.readString(configFile.toPath());
      assertFalse(configContent.contains("test-db"));
    }

    @Test
    @DisplayName("should skip persistence in in-memory mode")
    void inMemoryMode_SkipsPersistence() throws Exception {
      DatabaseService testService =
          new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      testService.init();

      File dbFile = tempDir.resolve("test.cbh").toFile();

      Database.create(dbFile, false).close();

      // Should not throw exception even though no config file exists
      assertDoesNotThrow(
          () -> testService.registerDatabase("test-db", "Test", dbFile.getAbsolutePath()));
    }
  }

  @Nested
  @DisplayName("Database Retrieval")
  class RetrievalTests {

    @BeforeEach
    void setUp() {
      service = new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      service.init();
    }

    @Test
    @DisplayName("should get all databases in order")
    void getAllDatabase_ReturnsInOrder() throws Exception {
      File dbFile1 = tempDir.resolve("db1.cbh").toFile();
      File dbFile2 = tempDir.resolve("db2.cbh").toFile();
      File dbFile3 = tempDir.resolve("db3.cbh").toFile();

      Database.create(dbFile1, false).close();
      Database.create(dbFile2, false).close();
      Database.create(dbFile3, false).close();

      service.registerDatabase("db1", "Database 1", dbFile1.getAbsolutePath());
      service.registerDatabase("db2", "Database 2", dbFile2.getAbsolutePath());
      service.registerDatabase("db3", "Database 3", dbFile3.getAbsolutePath());

      List<DatabaseDto> databases = service.getAllDatabase();

      assertEquals(3, databases.size());
      assertEquals("db1", databases.get(0).id());
      assertEquals("db2", databases.get(1).id());
      assertEquals("db3", databases.get(2).id());
    }

    @Test
    @DisplayName("should get database config")
    void getDatabaseConfig() throws Exception {
      File dbFile = tempDir.resolve("test.cbh").toFile();

      Database.create(dbFile, false).close();

      service.registerDatabase("test-db", "Test Database", dbFile.getAbsolutePath());

      DatabaseConfig config = service.getDatabaseConfig("test-db");

      assertNotNull(config);
      assertEquals("test-db", config.getId());
      assertEquals("Test Database", config.getDisplayName());
      assertEquals(dbFile.getAbsolutePath(), config.getPath());
    }

    @Test
    @DisplayName("should return null for unknown database config")
    void getDatabaseConfig_UnknownDatabase() {
      DatabaseConfig config = service.getDatabaseConfig("unknown-db");
      assertNull(config);
    }
  }

  @Nested
  @DisplayName("Cleanup")
  class CleanupTests {

    @Test
    @DisplayName("should close all databases on cleanup")
    void cleanup_ClosesAllDatabases() throws Exception {
      DatabaseService testService =
          new DatabaseService("", 600000L, List.of(tempDir.toString()), null);
      testService.init();

      File dbFile1 = tempDir.resolve("db1.cbh").toFile();
      File dbFile2 = tempDir.resolve("db2.cbh").toFile();

      Database.create(dbFile1, false).close();
      Database.create(dbFile2, false).close();

      testService.registerDatabase("db1", "DB 1", dbFile1.getAbsolutePath());
      testService.registerDatabase("db2", "DB 2", dbFile2.getAbsolutePath());

      // Open both databases by accessing them
      testService.withReadTransaction("db1", txn -> null);
      testService.withReadTransaction("db2", txn -> null);

      // Cleanup should not throw exception
      assertDoesNotThrow(() -> testService.cleanup());

      // Verify databases are cleared
      assertTrue(testService.getAllDatabase().isEmpty());
    }
  }
}
