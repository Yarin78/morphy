package se.yarin.morphy.service.annotators;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@RestController
@RequestMapping("/api/databases/{databaseId}/annotators")
public class AnnotatorsController {
  private static final Logger log = LoggerFactory.getLogger(AnnotatorsController.class);

  private final @NotNull AnnotatorsService annotatorsService;

  public AnnotatorsController(@NotNull AnnotatorsService annotatorsService) {
    this.annotatorsService = annotatorsService;
  }

  /**
   * Get annotators with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (annotator ID to start from)
   * @param limit Number of annotators to return (default 100, max 1000)
   * @return Paginated list of annotators
   */
  @GetMapping
  public ResponseEntity<AnnotatorListResponse> getAnnotators(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    AnnotatorListResponse response = annotatorsService.getAnnotators(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single annotator by ID.
   *
   * @param databaseId The database ID
   * @param annotatorId The annotator ID
   * @return The annotator information, or 404 if not found
   */
  @GetMapping("/{annotatorId}")
  public ResponseEntity<AnnotatorDto> getAnnotator(
      @PathVariable String databaseId, @PathVariable int annotatorId) {
    try {
      AnnotatorDto annotator = annotatorsService.getAnnotator(databaseId, annotatorId);
      if (annotator == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(annotator);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving annotator {} from database '{}': {}",
          annotatorId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of annotators in the database.
   *
   * @param databaseId The database ID
   * @return The count of annotators
   */
  @GetMapping("/count")
  public ResponseEntity<AnnotatorCountResponse> getAnnotatorCount(
      @PathVariable String databaseId) {
    int count = annotatorsService.getAnnotatorCount(databaseId);
    return ResponseEntity.ok(new AnnotatorCountResponse(count));
  }

  /**
   * Update an existing annotator in the database.
   *
   * @param databaseId The database ID
   * @param annotatorId The ID of the annotator to update
   * @param annotatorDto The new annotator data
   * @return The updated annotator
   */
  @PutMapping("/{annotatorId}")
  public ResponseEntity<AnnotatorDto> updateAnnotator(
      @PathVariable String databaseId,
      @PathVariable int annotatorId,
      @RequestBody AnnotatorDto annotatorDto) {
    try {
      AnnotatorDto updatedAnnotator =
          annotatorsService.updateAnnotator(databaseId, annotatorId, annotatorDto);
      return ResponseEntity.ok(updatedAnnotator);
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating annotator {} in database '{}': {}",
          annotatorId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error(
          "Unexpected error updating annotator {} in database '{}'", annotatorId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<AnnotatorDto>> searchAnnotators(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) Boolean debugQueryPlans,
      @RequestParam(required = false) Boolean debugExecuteAllPlans) {
    EntitySearchRequest request =
        new EntitySearchRequest(filter, offset, limit, sortBy, debugQueryPlans,
            debugExecuteAllPlans);
    EntitySearchResponse<AnnotatorDto> response =
        annotatorsService.searchAnnotators(databaseId, request);
    return ResponseEntity.ok(response);
  }
}
