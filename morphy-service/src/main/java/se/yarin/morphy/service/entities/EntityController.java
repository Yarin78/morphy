package se.yarin.morphy.service.entities;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.service.CountResponse;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

/**
 * The endpoints for one kind of entity: list, get, count, update and search. A subclass per kind
 * gives the path and the {@link EntityKind}.
 *
 * @param <T> the DTO type of the entity kind
 */
public abstract class EntityController<T> {
  private static final Logger log = LoggerFactory.getLogger(EntityController.class);

  private final @NotNull EntitiesService entitiesService;
  private final @NotNull EntityKind<T> kind;

  protected EntityController(@NotNull EntitiesService entitiesService, @NotNull EntityKind<T> kind) {
    this.entitiesService = entitiesService;
    this.kind = kind;
  }

  /**
   * Lists entities in their natural order.
   *
   * @param offset number of entities to skip (default 0)
   * @param limit number of entities to return (default 100, max 1000)
   */
  @GetMapping
  public ResponseEntity<EntitySearchResponse<T>> list(
      @PathVariable String databaseId,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit) {
    return ResponseEntity.ok(entitiesService.list(databaseId, kind, offset, limit));
  }

  /** Gets one entity, or 404 if there is none with that id or no game refers to it. */
  @GetMapping("/{id}")
  public ResponseEntity<T> get(@PathVariable String databaseId, @PathVariable long id) {
    try {
      T entity = entitiesService.get(databaseId, kind, id);
      return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving {} {} from database '{}': {}", kind, id, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /** The number of entities that games refer to. */
  @GetMapping("/count")
  public ResponseEntity<CountResponse> count(@PathVariable String databaseId) {
    return ResponseEntity.ok(new CountResponse(entitiesService.count(databaseId, kind)));
  }

  /** Updates an entity; 404 if it doesn't exist or can't be updated. */
  @PutMapping("/{id}")
  public ResponseEntity<T> update(
      @PathVariable String databaseId, @PathVariable long id, @RequestBody T entity) {
    try {
      return ResponseEntity.ok(entitiesService.update(databaseId, kind, id, entity));
    } catch (MorphyServiceException e) {
      log.error("Error updating {} {} in database '{}': {}", kind, id, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error updating {} {} in database '{}'", kind, id, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Searches entities.
   *
   * @param filter filter expression; a bare term matches the kind's default field
   * @param offset number of entities to skip (default 0)
   * @param limit number of entities to return (default 50, max 1000)
   * @param sortBy comma-separated sort fields with optional +/- prefix; "default" for the natural
   *     order
   */
  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<T>> search(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) Boolean debugQueryPlans,
      @RequestParam(required = false) Boolean debugExecuteAllPlans,
      @RequestParam(required = false) Boolean debugRawData) {
    EntitySearchRequest request =
        new EntitySearchRequest(
            filter, offset, limit, sortBy, debugQueryPlans, debugExecuteAllPlans, debugRawData);
    return ResponseEntity.ok(entitiesService.search(databaseId, kind, request));
  }
}
