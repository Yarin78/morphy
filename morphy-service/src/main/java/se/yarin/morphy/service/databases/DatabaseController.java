package se.yarin.morphy.service.databases;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.databases.DatabaseDto;
import se.yarin.morphy.service.databases.DatabaseListResponse;
import se.yarin.morphy.service.databases.DatabaseResponse;
import se.yarin.morphy.service.databases.DatabaseService;

@RestController
@RequestMapping("/api/databases")
public class DatabaseController {
  private static final Logger log = LoggerFactory.getLogger(DatabaseController.class);

  private final DatabaseService databaseService;

  public DatabaseController(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  /** Get all configured databases. */
  @GetMapping
  public ResponseEntity<DatabaseListResponse> getAllDatabases() {
    List<DatabaseDto> databases = databaseService.getAllDatabase();
    List<DatabaseResponse> response =
        databases.stream()
            .map(dto -> new DatabaseResponse(dto.id(), dto.displayName(), dto.path()))
            .toList();
    return ResponseEntity.ok(new DatabaseListResponse(response));
  }

  /** Get database details by ID. */
  @GetMapping("/{databaseId}")
  public ResponseEntity<DatabaseResponse> getDatabase(@PathVariable String databaseId) {
    var config = databaseService.getDatabaseConfig(databaseId);
    if (config == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(
        new DatabaseResponse(config.getId(), config.getDisplayName(), config.getPath()));
  }

  /** Refresh a database by forcing it to reload from disk. */
  @PostMapping("/{databaseId}/refresh")
  public ResponseEntity<Void> refreshDatabase(@PathVariable String databaseId) {
    databaseService.refreshDatabase(databaseId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Create a new database at the specified path and register it in the configuration.
   *
   * @param request Request containing database ID, display name, and path
   * @return The created database details
   */
  @PostMapping
  public ResponseEntity<DatabaseResponse> createDatabase(@RequestBody CreateDatabaseRequest request) {
    try {
      databaseService.createDatabase(request.id(), request.displayName(), request.path());
      var config = databaseService.getDatabaseConfig(request.id());
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new DatabaseResponse(config.getId(), config.getDisplayName(), config.getPath()));
    } catch (IOException e) {
      log.error("Failed to create database: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Register an existing database and add it to the configuration.
   *
   * @param request Request containing database ID, display name, and path
   * @return The registered database details
   */
  @PostMapping("/register")
  public ResponseEntity<DatabaseResponse> registerDatabase(@RequestBody RegisterDatabaseRequest request) {
    try {
      databaseService.registerDatabase(request.id(), request.displayName(), request.path());
      var config = databaseService.getDatabaseConfig(request.id());
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new DatabaseResponse(config.getId(), config.getDisplayName(), config.getPath()));
    } catch (IOException e) {
      log.error("Failed to register database: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Unregister a database by removing it from the configuration. The database files are not
   * deleted from disk.
   *
   * @param databaseId The database ID to unregister
   * @return 204 No Content on success
   */
  @DeleteMapping("/{databaseId}")
  public ResponseEntity<Void> unregisterDatabase(@PathVariable String databaseId) {
    try {
      databaseService.unregisterDatabase(databaseId);
      return ResponseEntity.noContent().build();
    } catch (IOException e) {
      log.error("Failed to unregister database: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
