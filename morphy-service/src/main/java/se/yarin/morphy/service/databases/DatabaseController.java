package se.yarin.morphy.service.databases;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    try {
      databaseService.refreshDatabase(databaseId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      log.warn("Database not found: {}", databaseId);
      return ResponseEntity.notFound().build();
    }
  }
}
