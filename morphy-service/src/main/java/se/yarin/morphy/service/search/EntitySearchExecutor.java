package se.yarin.morphy.service.search;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;

@Component
public class EntitySearchExecutor {
  private static final Logger log = LoggerFactory.getLogger(EntitySearchExecutor.class);

  /**
   * Executes an entity search using the query planner infrastructure.
   *
   * @param txn the read transaction
   * @param entityType the entity type being searched
   * @param queryBuilder builds an EntityQuery from the filter string
   * @param sortOrderFn maps (sortBy, reverse) to a QuerySortOrder
   * @param toDtoFn converts an entity to a DTO
   * @param request the search request
   * @param <E> the entity type
   * @param <D> the DTO type
   * @return search response with matching entities
   */
  public <E extends Entity & Comparable<E>, D> EntitySearchResponse<D> executeSearch(
      @NotNull DatabaseReadTransaction txn,
      @NotNull EntityType entityType,
      @NotNull BiFunction<Database, String, EntityQuery<E>> queryBuilder,
      @NotNull BiFunction<String, Boolean, QuerySortOrder<E>> sortOrderFn,
      @NotNull Function<E, D> toDtoFn,
      @NotNull EntitySearchRequest request) {
    long startTime = System.currentTimeMillis();

    int offset = Math.max(0, request.offset());
    int limit = Math.min(1000, Math.max(1, request.limit()));
    boolean reverse = "desc".equalsIgnoreCase(request.order());

    // 1. Build EntityQuery from filter string
    EntityQuery<E> baseQuery = queryBuilder.apply(txn.database(), request.filter());

    // 2. Apply sort order
    QuerySortOrder<E> sortOrder = sortOrderFn.apply(request.sortBy(), reverse);
    EntityQuery<E> query =
        new EntityQuery<>(
            baseQuery.database(),
            baseQuery.entityType(),
            baseQuery.filters(),
            sortOrder,
            0);

    // 3. Create query context and execute via QueryPlanner
    QueryContext context = new QueryContext(txn, false);
    List<QueryOperator<E>> plans =
        txn.database()
            .queryPlanner()
            .sortQueryPlansByCost(
                txn.database().queryPlanner().getEntityQueryPlans(context, query, true));

    QueryOperator<E> bestPlan = plans.getFirst();

    log.debug(
        "Entity search [{}]: selected plan {} (cost: {})",
        entityType,
        bestPlan.getClass().getSimpleName(),
        bestPlan.getQueryCost().estimatedTotalCost());

    // 4. Execute and filter out unused entities
    List<E> entities =
        bestPlan.stream()
            .map(QueryData::data)
            .filter(e -> e.count() > 0)
            .collect(Collectors.toList());

    // 5. Apply pagination
    int totalCount = entities.size();
    int fromIndex = Math.min(offset, totalCount);
    int toIndex = Math.min(offset + limit, totalCount);
    List<E> paginatedEntities = entities.subList(fromIndex, toIndex);

    // 6. Convert to DTOs
    List<D> dtos = paginatedEntities.stream().map(toDtoFn).collect(Collectors.toList());

    long endTime = System.currentTimeMillis();

    // 7. Build metadata
    SearchMetadata metadata =
        new SearchMetadata(request.filter(), request.sortBy(), request.order(), endTime - startTime);

    return new EntitySearchResponse<>(dtos, dtos.size(), totalCount, offset, limit, metadata);
  }
}
