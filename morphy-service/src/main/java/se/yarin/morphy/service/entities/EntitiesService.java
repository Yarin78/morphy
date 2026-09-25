package se.yarin.morphy.service.entities;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.CbhDiagnostics;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.queryplans.QueryPlanDebugInfo;
import se.yarin.morphy.service.queryplans.QueryPlanDtoConverter;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.search.SearchMetadata;

/**
 * Reads, searches and updates the entities of a database: players, tournaments, annotators,
 * sources, teams and game tags, all through the same {@link EntityKind}-typed operations.
 *
 * <p>An entity that no game refers to doesn't exist here, as in the {@link
 * se.yarin.morphy.api.Database} it comes from.
 */
@Service
public class EntitiesService {
  private static final Logger log = LoggerFactory.getLogger(EntitiesService.class);

  /** The largest page a list or search may ask for. */
  public static final int MAX_LIMIT = 1000;

  private final DatabaseService databaseService;
  private final QueryPlanDtoConverter queryPlanDtoConverter;

  public EntitiesService(
      DatabaseService databaseService, QueryPlanDtoConverter queryPlanDtoConverter) {
    this.databaseService = databaseService;
    this.queryPlanDtoConverter = queryPlanDtoConverter;
  }

  /** Lists the entities of a kind in their natural order. */
  public <T> EntitySearchResponse<T> list(
      @NotNull String databaseId, @NotNull EntityKind<T> kind, int offset, int limit) {
    return search(
        databaseId,
        kind,
        new EntitySearchRequest(null, offset, limit, null, false, false, false));
  }

  /**
   * Gets an entity.
   *
   * @return the entity, or null if there is none with that id or no game refers to it
   */
  public <T> @Nullable T get(@NotNull String databaseId, @NotNull EntityKind<T> kind, long id) {
    return databaseService.read(databaseId, db -> db.getEntity(kind, id));
  }

  /** The number of entities of a kind that games refer to. */
  public long count(@NotNull String databaseId, @NotNull EntityKind<?> kind) {
    return databaseService.read(databaseId, db -> db.entityCount(kind));
  }

  /**
   * Updates an entity.
   *
   * @return the updated entity
   * @throws IllegalArgumentException if there is no such entity, or the update would make it equal
   *     to another existing one
   * @throws MorphyServiceException if the update fails otherwise
   */
  public <T> T update(
      @NotNull String databaseId, @NotNull EntityKind<T> kind, long id, @NotNull T entity) {
    try {
      T updated = databaseService.write(databaseId, db -> db.updateEntity(kind, id, entity));
      log.info("Successfully updated {} {} in database '{}'", kind, id, databaseId);
      return updated;
    } catch (IllegalArgumentException | MorphyServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update " + kind + " " + id + " in database '" + databaseId + "'", e);
    }
  }

  /** Searches for entities of a kind. */
  public <T> EntitySearchResponse<T> search(
      @NotNull String databaseId,
      @NotNull EntityKind<T> kind,
      @NotNull EntitySearchRequest request) {
    long startTime = System.currentTimeMillis();
    Query query =
        new Query(
            request.filter(),
            List.of(),
            Sort.parse(request.sortBy()),
            Math.max(0, request.offset()),
            Math.min(MAX_LIMIT, Math.max(1, request.limit())));

    return databaseService.read(
        databaseId,
        db -> {
          ResultPage<T> page = db.findEntities(kind, query);
          QueryPlanDebugInfo debugInfo =
              request.debugQueryPlans()
                  ? db.extension(CbhDiagnostics.class)
                      .map(d -> d.explainEntities(kind, query, request.debugExecuteAllPlans()))
                      .map(queryPlanDtoConverter::toDebugInfo)
                      .orElse(null)
                  : null;
          SearchMetadata metadata =
              new SearchMetadata(
                  page.appliedFilter(),
                  query.sort().toString(),
                  System.currentTimeMillis() - startTime);
          return new EntitySearchResponse<>(
              page.items(),
              page.items().size(),
              page.total() == null ? null : page.total().intValue(),
              page.offset(),
              page.limit(),
              metadata,
              debugInfo);
        });
  }
}
