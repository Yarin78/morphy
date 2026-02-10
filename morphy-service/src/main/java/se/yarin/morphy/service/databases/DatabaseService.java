package se.yarin.morphy.service.databases;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import se.yarin.morphy.service.config.DatabaseConfig;

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

  /**
   * Creates a new database at the specified path and registers it in the configuration.
   *
   * @param databaseId Unique identifier for the database
   * @param displayName Human-readable name for the database
   * @param path File system path where the database should be created
   * @throws IllegalArgumentException if databaseId already exists or is invalid
   * @throws IOException if database creation or config file update fails
   */
  public void createDatabase(
      @NotNull String databaseId, @NotNull String displayName, @NotNull String path)
      throws IOException {
    validateDatabaseId(databaseId);

    File dbFile = new File(path);

    // Create the database file
    try {
      log.info("Creating new database '{}' at: {}", databaseId, path);
      File parentDir = dbFile.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        if (!parentDir.mkdirs()) {
          throw new IOException("Failed to create parent directories for " + path);
        }
      }

      Database newDb = Database.create(dbFile, false);
      newDb.close();
      log.info("Successfully created new database '{}'", databaseId);
    } catch (IOException e) {
      log.error("Failed to create database '{}' at {}", databaseId, path, e);
      throw new IOException("Failed to create database: " + e.getMessage(), e);
    }

    // Register the database in config and memory
    registerDatabaseInternal(databaseId, displayName, path);
  }

  /**
   * Registers an existing database and adds it to the configuration.
   *
   * @param databaseId Unique identifier for the database
   * @param displayName Human-readable name for the database
   * @param path File system path to the existing database
   * @throws IllegalArgumentException if databaseId already exists, database file doesn't exist, or
   *     is invalid
   * @throws IOException if config file update fails
   */
  public void registerDatabase(
      @NotNull String databaseId, @NotNull String displayName, @NotNull String path)
      throws IOException {
    validateDatabaseId(databaseId);

    File dbFile = new File(path);
    if (!dbFile.exists()) {
      throw new IllegalArgumentException("Database file does not exist: " + path);
    }

    log.info("Registering existing database '{}' at: {}", databaseId, path);
    registerDatabaseInternal(databaseId, displayName, path);
  }

  private void validateDatabaseId(@NotNull String databaseId) {
    if (databaseId.trim().isEmpty()) {
      throw new IllegalArgumentException("Database ID cannot be empty");
    }
    if (databaseStates.containsKey(databaseId)) {
      throw new IllegalArgumentException("Database ID already exists: " + databaseId);
    }
  }

  private void registerDatabaseInternal(
      @NotNull String databaseId, @NotNull String displayName, @NotNull String path)
      throws IOException {
    // Create config
    DatabaseConfig config = new DatabaseConfig(databaseId, displayName, path);

    // Add to in-memory state
    DatabaseState state = new DatabaseState(config);
    databaseStates.put(databaseId, state);

    // Update config file
    saveDatabaseConfiguration(databaseId, config);

    log.info(
        "Successfully registered database '{}' ({}) at: {}",
        databaseId,
        displayName,
        path);
  }

  /**
   * Unregisters a database by removing it from the configuration and closing it if open. The
   * database files are not deleted from disk.
   *
   * @param databaseId The database ID to unregister
   * @throws IllegalArgumentException if the database ID is unknown
   * @throws IOException if config file update fails
   */
  public void unregisterDatabase(@NotNull String databaseId) throws IOException {
    DatabaseState state = databaseStates.get(databaseId);
    if (state == null) {
      throw new IllegalArgumentException("Unknown database ID: " + databaseId);
    }

    log.info("Unregistering database '{}'", databaseId);

    // Close the database if it's open
    if (state.database != null) {
      try {
        state.database.close();
        log.info("Closed database '{}' before unregistering", databaseId);
      } catch (Exception e) {
        log.warn("Error closing database '{}' during unregister", databaseId, e);
      }
    }

    // Remove from in-memory state
    databaseStates.remove(databaseId);

    // Remove from config file
    removeDatabaseConfiguration(databaseId);

    log.info("Successfully unregistered database '{}'", databaseId);
  }

  private void saveDatabaseConfiguration(
      @NotNull String databaseId, @NotNull DatabaseConfig config) throws IOException {
    if (databasesConfigPath == null || databasesConfigPath.isEmpty()) {
      throw new IOException("No database configuration path specified");
    }

    // Load existing configs
    Map<String, DatabaseConfig> configs = loadDatabaseConfigurations();

    // Add or update the new config
    configs.put(databaseId, config);

    // Write back to file
    Resource resource = resourceLoader.getResource(databasesConfigPath);
    File configFile = resource.getFile();

    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, configs);
      log.info("Updated database configuration file: {}", databasesConfigPath);
    } catch (IOException e) {
      log.error("Failed to write database configuration to {}", databasesConfigPath, e);
      throw new IOException("Failed to save database configuration: " + e.getMessage(), e);
    }
  }

  private void removeDatabaseConfiguration(@NotNull String databaseId) throws IOException {
    if (databasesConfigPath == null || databasesConfigPath.isEmpty()) {
      throw new IOException("No database configuration path specified");
    }

    // Load existing configs
    Map<String, DatabaseConfig> configs = loadDatabaseConfigurations();

    // Remove the config
    if (configs.remove(databaseId) == null) {
      log.warn("Database '{}' was not found in configuration file", databaseId);
    }

    // Write back to file
    Resource resource = resourceLoader.getResource(databasesConfigPath);
    File configFile = resource.getFile();

    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, configs);
      log.info("Removed database '{}' from configuration file", databaseId);
    } catch (IOException e) {
      log.error("Failed to write database configuration to {}", databasesConfigPath, e);
      throw new IOException("Failed to update database configuration: " + e.getMessage(), e);
    }
  }
}
