package se.yarin.morphy.service.databases;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.yarin.morphy.Database;

/**
 * Integration tests for DatabaseService using Spring Boot test context. These tests verify that the
 * service works correctly with Spring's dependency injection and configuration management.
 */
@SpringBootTest(classes = DatabaseService.class)
@ActiveProfiles("test")
class DatabaseServiceIntegrationTest {

  @Autowired private DatabaseService databaseService;

  @TempDir static Path tempDir;
  private static File configFile;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
    // Create a test config file
    configFile = tempDir.resolve("databases-integration-test.json").toFile();
    Files.writeString(configFile.toPath(), "{}");

    registry.add("app.databases.config", () -> configFile.getAbsolutePath());
    registry.add("app.databases.freshness-check-interval", () -> "600000");
    registry.add("app.databases.allowed-paths", () -> tempDir.toString());
    registry.add("app.databases.allowed-create-paths", () -> tempDir.toString());
  }

  @Nested
  @DisplayName("Spring Configuration Integration")
  class SpringConfigurationTests {

    @Test
    @DisplayName("should autowire DatabaseService successfully")
    void autowireDatabaseService() {
      assertNotNull(databaseService);
    }

    @Test
    @DisplayName("should load configuration from application properties")
    void loadConfiguration() {
      // Verify that the service was initialized with config from properties
      assertNotNull(databaseService);

      // The service should start with no databases (empty config file)
      assertTrue(databaseService.getAllDatabase().isEmpty());
    }
  }

  @Nested
  @DisplayName("End-to-End Database Operations")
  class EndToEndTests {

    @Test
    @DisplayName("should create, use, and unregister database end-to-end")
    void createUseAndUnregisterDatabase() throws Exception {
      File dbPath = tempDir.resolve("e2e-test.cbh").toFile();

      // Create database
      databaseService.createDatabase("e2e-db", "E2E Test Database", dbPath.getAbsolutePath());

      // Verify it's registered
      List<DatabaseDto> databases = databaseService.getAllDatabase();
      assertEquals(1, databases.size());
      assertEquals("e2e-db", databases.get(0).id());

      // Use the database - read transaction
      String result =
          databaseService.withReadTransaction(
              "e2e-db",
              txn -> {
                assertNotNull(txn);
                return "read-success";
              });
      assertEquals("read-success", result);

      // Use the database - write transaction
      databaseService.withWriteTransaction(
          "e2e-db",
          txn -> {
            assertNotNull(txn);
            // In a real scenario, we'd write data here
          });

      // Unregister
      databaseService.unregisterDatabase("e2e-db");

      // Verify it's gone
      assertTrue(databaseService.getAllDatabase().isEmpty());

      // But file should still exist
      assertTrue(dbPath.exists());
    }

    @Test
    @DisplayName("should register existing database and perform transactions")
    void registerExistingDatabaseAndUseIt() throws Exception {
      File dbPath = tempDir.resolve("existing-test.cbh").toFile();

      // Create a database file manually
      Database db = Database.create(dbPath, false);
      db.close();

      // Register it
      databaseService.registerDatabase(
          "existing-db", "Existing Test Database", dbPath.getAbsolutePath());

      // Verify we can read from it
      assertDoesNotThrow(
          () ->
              databaseService.withReadTransaction(
                  "existing-db",
                  txn -> {
                    assertNotNull(txn);
                    return null;
                  }));

      // Verify we can write to it
      assertDoesNotThrow(
          () ->
              databaseService.withWriteTransaction(
                  "existing-db",
                  txn -> {
                    assertNotNull(txn);
                  }));

      // Cleanup
      databaseService.unregisterDatabase("existing-db");
    }
  }

  @Nested
  @DisplayName("Configuration Persistence")
  class ConfigPersistenceIntegrationTests {

    @Test
    @DisplayName("should persist database registration to config file")
    void persistRegistrationToConfigFile() throws Exception {
      File dbPath = tempDir.resolve("persist-test.cbh").toFile();

      // Create and register database
      databaseService.createDatabase(
          "persist-db", "Persist Test Database", dbPath.getAbsolutePath());

      // Verify config file was updated
      String configContent = Files.readString(configFile.toPath());
      assertTrue(configContent.contains("persist-db"));
      assertTrue(configContent.contains("Persist Test Database"));

      // Cleanup
      databaseService.unregisterDatabase("persist-db");

      // Verify removal was persisted
      configContent = Files.readString(configFile.toPath());
      assertFalse(configContent.contains("persist-db"));
    }
  }

  @Nested
  @DisplayName("Database Lifecycle Management")
  class LifecycleManagementTests {

    @Test
    @DisplayName("should lazily open database on first access")
    void lazyDatabaseOpening() throws Exception {
      File dbPath = tempDir.resolve("lazy-test.cbh").toFile();

      // Register database
      databaseService.createDatabase("lazy-db", "Lazy Test Database", dbPath.getAbsolutePath());

      // At this point, database should be registered but might not be opened yet
      assertNotNull(databaseService.getDatabaseConfig("lazy-db"));

      // Accessing it should open it (no exception)
      assertDoesNotThrow(
          () ->
              databaseService.withReadTransaction(
                  "lazy-db",
                  txn -> {
                    assertNotNull(txn);
                    return null;
                  }));

      // Cleanup
      databaseService.unregisterDatabase("lazy-db");
    }

    @Test
    @DisplayName("should refresh database successfully")
    void refreshDatabase() throws Exception {
      File dbPath = tempDir.resolve("refresh-test.cbh").toFile();

      // Create and open database
      databaseService.createDatabase(
          "refresh-db", "Refresh Test Database", dbPath.getAbsolutePath());

      // Access it to open it
      databaseService.withReadTransaction("refresh-db", txn -> null);

      // Refresh should work without error
      assertDoesNotThrow(() -> databaseService.refreshDatabase("refresh-db"));

      // Database should still be accessible after refresh
      assertDoesNotThrow(() -> databaseService.withReadTransaction("refresh-db", txn -> null));

      // Cleanup
      databaseService.unregisterDatabase("refresh-db");
    }
  }

  @Nested
  @DisplayName("Multiple Database Management")
  class MultipleDatabaseTests {

    @Test
    @DisplayName("should manage multiple databases concurrently")
    void manageMultipleDatabases() throws Exception {
      File dbPath1 = tempDir.resolve("multi-1.cbh").toFile();
      File dbPath2 = tempDir.resolve("multi-2.cbh").toFile();
      File dbPath3 = tempDir.resolve("multi-3.cbh").toFile();

      // Create multiple databases
      databaseService.createDatabase("multi-db-1", "Multi DB 1", dbPath1.getAbsolutePath());
      databaseService.createDatabase("multi-db-2", "Multi DB 2", dbPath2.getAbsolutePath());
      databaseService.createDatabase("multi-db-3", "Multi DB 3", dbPath3.getAbsolutePath());

      // Verify all are registered
      List<DatabaseDto> databases = databaseService.getAllDatabase();
      assertEquals(3, databases.size());

      // Access all databases
      for (int i = 1; i <= 3; i++) {
        final int dbNum = i;
        assertDoesNotThrow(
            () ->
                databaseService.withReadTransaction(
                    "multi-db-" + dbNum,
                    txn -> {
                      assertNotNull(txn);
                      return null;
                    }));
      }

      // Write to one database shouldn't affect others
      databaseService.withWriteTransaction(
          "multi-db-2",
          txn -> {
            assertNotNull(txn);
          });

      // All should still be accessible
      assertDoesNotThrow(() -> databaseService.withReadTransaction("multi-db-1", txn -> null));
      assertDoesNotThrow(() -> databaseService.withReadTransaction("multi-db-3", txn -> null));

      // Cleanup
      databaseService.unregisterDatabase("multi-db-1");
      databaseService.unregisterDatabase("multi-db-2");
      databaseService.unregisterDatabase("multi-db-3");

      assertTrue(databaseService.getAllDatabase().isEmpty());
    }

    @Test
    @DisplayName("should maintain database order")
    void maintainDatabaseOrder() throws Exception {
      File dbPath1 = tempDir.resolve("order-1.cbh").toFile();
      File dbPath2 = tempDir.resolve("order-2.cbh").toFile();
      File dbPath3 = tempDir.resolve("order-3.cbh").toFile();

      // Create databases in specific order
      databaseService.createDatabase("order-db-1", "Order DB 1", dbPath1.getAbsolutePath());
      databaseService.createDatabase("order-db-2", "Order DB 2", dbPath2.getAbsolutePath());
      databaseService.createDatabase("order-db-3", "Order DB 3", dbPath3.getAbsolutePath());

      // Verify order is preserved
      List<DatabaseDto> databases = databaseService.getAllDatabase();
      assertEquals("order-db-1", databases.get(0).id());
      assertEquals("order-db-2", databases.get(1).id());
      assertEquals("order-db-3", databases.get(2).id());

      // Cleanup
      databaseService.unregisterDatabase("order-db-1");
      databaseService.unregisterDatabase("order-db-2");
      databaseService.unregisterDatabase("order-db-3");
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should handle transaction on unknown database gracefully")
    void unknownDatabaseError() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> databaseService.withReadTransaction("unknown-db", txn -> null));

      assertTrue(exception.getMessage().contains("Unknown database ID"));
    }

    @Test
    @DisplayName("should handle duplicate database ID gracefully")
    void duplicateDatabaseIdError() throws Exception {
      File dbPath1 = tempDir.resolve("dup-1.cbh").toFile();
      File dbPath2 = tempDir.resolve("dup-2.cbh").toFile();

      // Create first database
      databaseService.createDatabase("dup-db", "Dup DB 1", dbPath1.getAbsolutePath());

      // Try to create another with same ID
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> databaseService.createDatabase("dup-db", "Dup DB 2", dbPath2.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("already exists"));

      // Cleanup
      databaseService.unregisterDatabase("dup-db");
    }

    @Test
    @DisplayName("should validate database paths correctly")
    void pathValidationError() throws Exception {
      File invalidPath = Files.createTempDirectory("invalid").resolve("test.cbh").toFile();
      Database.create(invalidPath, false).close();

      // Try to register database outside allowed paths
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  databaseService.registerDatabase(
                      "invalid-db", "Invalid DB", invalidPath.getAbsolutePath()));

      assertTrue(exception.getMessage().contains("not allowed"));
    }
  }

  @AfterEach
  void cleanupDatabases() {
    // Clean up any remaining databases after each test
    List<DatabaseDto> databases = databaseService.getAllDatabase();
    for (DatabaseDto db : databases) {
      try {
        databaseService.unregisterDatabase(db.id());
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
  }
}
