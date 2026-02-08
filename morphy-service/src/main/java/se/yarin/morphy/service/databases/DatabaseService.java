package se.yarin.morphy.service.databases;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.config.DatabaseConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.DatabaseWriteTransaction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class DatabaseService {
  private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final Map<String, DatabaseState> databaseStates = new LinkedHashMap<>();

  @Value("${app.databases.config:}")
  private String databasesConfigPath;

  @Value("${app.databases.freshness-check-interval:600000}") // 10 minutes in milliseconds
  private long freshnessCheckInterval;

  public DatabaseService(@NotNull ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Execute a read-only operation within a database transaction. The transaction is automatically
   * managed and closed.
   *
   * @param databaseId The database ID to use
   * @param operation The operation to execute within the transaction
   * @param <T> The return type of the operation
   * @return The result of the operation
   */
  public <T> T withReadTransaction(
      @NotNull String databaseId, @NotNull Function<DatabaseReadTransaction, T> operation) {
    Database db = getDatabase(databaseId);
    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(db)) {
      return operation.apply(txn);
    }
  }

  /**
   * Execute a write operation within a database transaction. The transaction is automatically
   * committed and closed. Updates the lastModifiedTime to prevent unnecessary reopening.
   *
   * @param databaseId The database ID to use
   * @param operation The operation to execute within the transaction
   */
  public void withWriteTransaction(
      @NotNull String databaseId, @NotNull Consumer<DatabaseWriteTransaction> operation) {
    Database db = getDatabase(databaseId);
    DatabaseState state = databaseStates.get(databaseId);
    assert state != null;

    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(db)) {
      operation.accept(txn);
      txn.commit();

      // Update lastModifiedTime to prevent unnecessary reopening after internal write
      File dbFile = new File(state.config.getPath());
      state.lastModifiedTime = dbFile.lastModified();
    }
  }

  /**
   * Execute a write operation within a database transaction and return a result. The transaction is
   * automatically committed and closed. Updates the lastModifiedTime to prevent unnecessary
   * reopening.
   *
   * @param databaseId The database ID to use
   * @param operation The operation to execute within the transaction
   * @param <T> The return type of the operation
   * @return The result of the operation
   */
  public <T> T withWriteTransaction(
      @NotNull String databaseId, @NotNull Function<DatabaseWriteTransaction, T> operation) {
    Database db = getDatabase(databaseId);
    DatabaseState state = databaseStates.get(databaseId);
    assert state != null;

    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(db)) {
      T result = operation.apply(txn);
      txn.commit();

      // Update lastModifiedTime to prevent unnecessary reopening after internal write
      File dbFile = new File(state.config.getPath());
      state.lastModifiedTime = dbFile.lastModified();

      return result;
    }
  }

  private @NotNull Database getDatabase(@NotNull String databaseId) {
    ensureDatabaseIsOpenAndFresh(databaseId);

    DatabaseState state = databaseStates.get(databaseId);
    if (state == null) {
      throw new IllegalArgumentException("Unknown database ID: " + databaseId);
    }
    if (state.database == null) {
      throw new IllegalStateException("Database '" + databaseId + "' failed to open");
    }

    state.lastAccessTime = System.currentTimeMillis();
    return state.database;
  }

  /**
   * Get all configured databases.
   *
   * @return List of database DTOs in the order they were configured
   */
  public List<DatabaseDto> getAllDatabase() {
    // LinkedHashMap preserves insertion order
    return databaseStates.values().stream()
        .map(
            state ->
                new DatabaseDto(
                    state.config.getId(), state.config.getDisplayName(), state.config.getPath()))
        .toList();
  }

  /**
   * Force a database to refresh by closing and reopening it. Useful when you know the database has
   * been modified externally.
   *
   * @param databaseId The database ID to refresh
   * @throws IllegalArgumentException if the database ID is unknown
   */
  public void refreshDatabase(@NotNull String databaseId) {
    DatabaseState state = databaseStates.get(databaseId);
    if (state == null) {
      throw new IllegalArgumentException("Unknown database ID: " + databaseId);
    }

    log.info("Manually refreshing database '{}'", databaseId);
    reopenDatabaseFile(databaseId, state);
  }

  private static class DatabaseState {
    final @NotNull DatabaseConfig config;
    @Nullable Database database; // null = not yet opened or was closed
    long lastModifiedTime;

    long lastAccessTime;

    DatabaseState(@NotNull DatabaseConfig config) {
      this.config = config;
      this.database = null;
      this.lastModifiedTime = 0;
      this.lastAccessTime = System.currentTimeMillis();
    }
  }

  private String formatTimestamp(long timestamp) {
    if (timestamp == 0) {
      return "N/A";
    }
    LocalDateTime dateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return dateTime.format(ISO_FORMATTER);
  }

  @PostConstruct
  public void init() {
    if (databasesConfigPath == null || databasesConfigPath.isEmpty()) {
      log.warn(
          "No database configuration path specified. Set app.databases.config in application.properties");
      return;
    }

    try {
      Map<String, DatabaseConfig> configs = loadDatabaseConfigurations();

      if (configs.isEmpty()) {
        log.warn("No chess databases configured in {}", databasesConfigPath);
        return;
      }

      // Create database states for all configured databases (lazy opening)
      for (Map.Entry<String, DatabaseConfig> entry : configs.entrySet()) {
        String databaseId = entry.getKey();
        DatabaseConfig config = entry.getValue();
        config.setId(databaseId); // Set the ID from the map key
        databaseStates.put(databaseId, new DatabaseState(config));
      }

      log.info(
          "Initialized {} chess database configuration(s) (databases will be opened on first access)",
          databaseStates.size());
    } catch (Exception e) {
      log.error("Failed to load database configurations from {}", databasesConfigPath, e);
    }
  }

  private Map<String, DatabaseConfig> loadDatabaseConfigurations() throws IOException {
    if (databasesConfigPath == null || databasesConfigPath.isEmpty()) {
      return new LinkedHashMap<>();
    }

    Resource resource = resourceLoader.getResource(Objects.requireNonNull(databasesConfigPath));

    if (!resource.exists()) {
      log.error("Database configuration file not found: {}", databasesConfigPath);
      return new LinkedHashMap<>();
    }

    try (InputStream inputStream = resource.getInputStream()) {
      // Use LinkedHashMap to preserve insertion order from JSON
      Map<String, DatabaseConfig> configs =
          objectMapper.readValue(
              inputStream, new TypeReference<LinkedHashMap<String, DatabaseConfig>>() {});
      log.info("Loaded {} database configuration(s) from {}", configs.size(), databasesConfigPath);
      return configs;
    }
  }

  private void openDatabaseFile(@NotNull String databaseId, @NotNull DatabaseState state) {
    File dbFile = new File(state.config.getPath());

    // Create database file if it doesn't exist
    if (!dbFile.exists()) {
      try {
        log.info(
            "Database file not found for '{}', creating new database at: {}",
            databaseId,
            state.config.getPath());
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
          if (!parentDir.mkdirs()) {
            log.warn("Failed to create parent directories for '{}'", databaseId);
          }
        }
        Database newDb = Database.create(dbFile, false);
        newDb.close();
        log.info("Successfully created new database '{}'", databaseId);
      } catch (Exception e) {
        log.error("Failed to create database '{}': {}", databaseId, state.config.getPath(), e);
        return;
      }
    }

    try {
      state.database = Database.open(dbFile, DatabaseMode.READ_WRITE);
      state.lastModifiedTime = dbFile.lastModified();
      log.info(
          "Successfully opened chess database '{}' ({}): {} (last modified: {})",
          databaseId,
          state.config.getDisplayName(),
          state.config.getPath(),
          formatTimestamp(state.lastModifiedTime));
    } catch (Exception e) {
      log.error("Failed to open chess database '{}': {}", databaseId, state.config.getPath(), e);
      state.database = null;
    }
  }

  private void reopenDatabaseFile(@NotNull String databaseId, @NotNull DatabaseState state) {
    if (state.database != null) {
      try {
        state.database.close();
        log.info("Closed database '{}' before reopening", databaseId);
      } catch (Exception e) {
        log.warn("Error closing database '{}' before reopening", databaseId, e);
      }
      state.database = null;
    }

    openDatabaseFile(databaseId, state);
  }

  private void ensureDatabaseIsOpenAndFresh(@NotNull String databaseId) {
    DatabaseState state = databaseStates.get(databaseId);
    if (state == null) {
      throw new IllegalArgumentException("Unknown database ID: " + databaseId);
    }

    // If database is not open yet, open it (lazy opening)
    if (state.database == null) {
      log.info("Database '{}' not yet opened, opening lazily", databaseId);
      openDatabaseFile(databaseId, state);
      return;
    }

    // Check if file was deleted (edge case)
    File dbFile = new File(state.config.getPath());
    if (!dbFile.exists()) {
      if (state.database != null) {
        try {
          state.database.close();
          log.info("Database file deleted for '{}', closed database", databaseId);
        } catch (Exception e) {
          log.warn("Error closing database '{}' after file deletion", databaseId, e);
        }
        state.database = null;
      }
      return;
    }

    // Check if enough time has passed since last access - reopen for freshness
    long timeSinceLastAccess = System.currentTimeMillis() - state.lastAccessTime;
    if (timeSinceLastAccess >= freshnessCheckInterval) {
      log.info(
          "Database '{}' hasn't been accessed in {} ms, reopening for freshness",
          databaseId,
          timeSinceLastAccess);
      reopenDatabaseFile(databaseId, state);
    }
  }

  @PreDestroy
  public void cleanup() {
    synchronized (databaseStates) {
      for (Map.Entry<String, DatabaseState> entry : databaseStates.entrySet()) {
        String databaseId = entry.getKey();
        DatabaseState state = entry.getValue();
        if (state.database != null) {
          try {
            state.database.close();
            log.info("Closed chess database '{}'", databaseId);
          } catch (Exception e) {
            log.error("Error closing database '{}'", databaseId, e);
          }
        }
      }
      databaseStates.clear();
    }
  }

  public DatabaseConfig getDatabaseConfig(@NotNull String databaseId) {
    DatabaseState state = databaseStates.get(databaseId);
    return state != null ? state.config : null;
  }
}
