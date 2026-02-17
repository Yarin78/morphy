package se.yarin.morphy.service.sources;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.sources.dto.SourceDto;

@RestController
@RequestMapping("/api/databases/{databaseId}/sources")
public class SourcesController {
  private static final Logger log = LoggerFactory.getLogger(SourcesController.class);

  private final @NotNull SourcesService sourcesService;

  public SourcesController(@NotNull SourcesService sourcesService) {
    this.sourcesService = sourcesService;
  }

  /**
   * Get sources with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (source ID to start from)
   * @param limit Number of sources to return (default 100, max 1000)
   * @return Paginated list of sources
   */
  @GetMapping
  public ResponseEntity<SourceListResponse> getSources(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    SourceListResponse response = sourcesService.getSources(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single source by ID.
   *
   * @param databaseId The database ID
   * @param sourceId The source ID
   * @return The source information, or 404 if not found
   */
  @GetMapping("/{sourceId}")
  public ResponseEntity<SourceDto> getSource(
      @PathVariable String databaseId, @PathVariable int sourceId) {
    try {
      SourceDto source = sourcesService.getSource(databaseId, sourceId);
      if (source == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(source);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving source {} from database '{}': {}",
          sourceId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of sources in the database.
   *
   * @param databaseId The database ID
   * @return The count of sources
   */
  @GetMapping("/count")
  public ResponseEntity<SourceCountResponse> getSourceCount(@PathVariable String databaseId) {
    int count = sourcesService.getSourceCount(databaseId);
    return ResponseEntity.ok(new SourceCountResponse(count));
  }

  /**
   * Update an existing source in the database.
   *
   * @param databaseId The database ID
   * @param sourceId The ID of the source to update
   * @param sourceDto The new source data
   * @return The updated source
   */
  @PutMapping("/{sourceId}")
  public ResponseEntity<SourceDto> updateSource(
      @PathVariable String databaseId,
      @PathVariable int sourceId,
      @RequestBody SourceDto sourceDto) {
    try {
      SourceDto updatedSource = sourcesService.updateSource(databaseId, sourceId, sourceDto);
      return ResponseEntity.ok(updatedSource);
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating source {} in database '{}': {}", sourceId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error updating source {} in database '{}'", sourceId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<SourceDto>> searchSources(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String order) {
    EntitySearchRequest request = new EntitySearchRequest(filter, offset, limit, sortBy, order);
    EntitySearchResponse<SourceDto> response =
        sourcesService.searchSources(databaseId, request);
    return ResponseEntity.ok(response);
  }
}
