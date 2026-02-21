package se.yarin.morphy.service.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;
import se.yarin.morphy.queries.filter.AbstractEntityQueryBuilder;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.queries.visualisation.QueryDescriptionFormatter;
import se.yarin.morphy.service.queryplans.QueryPlanDebugInfo;
import se.yarin.morphy.service.queryplans.QueryPlanDto;
import se.yarin.morphy.service.queryplans.QueryPlanDtoConverter;

@Component
public class EntitySearchExecutor {
  private static final Logger log = LoggerFactory.getLogger(EntitySearchExecutor.class);
  private static final long MAX_PLAN_COST = 100_000;

  private final QueryPlanDtoConverter queryPlanDtoConverter;

  public EntitySearchExecutor(QueryPlanDtoConverter queryPlanDtoConverter) {
    this.queryPlanDtoConverter = queryPlanDtoConverter;
  }

  public <E extends Entity & Comparable<E>, D> EntitySearchResponse<D> executeSearch(
      @NotNull DatabaseReadTransaction txn,
      @NotNull EntityType entityType,
      @NotNull AbstractEntityQueryBuilder<E> queryBuilder,
      @NotNull Function<E, D> toDtoFn,
      @NotNull EntitySearchRequest request) {
    long startTime = System.currentTimeMillis();

    int offset = Math.max(0, request.offset());
    int limit = Math.min(1000, Math.max(1, request.limit()));
    boolean debugPlans = request.debugQueryPlans();
    boolean executeAll = request.debugExecuteAllPlans();

    // 1. Build EntityQuery from filter string
    EntityQuery<E> baseQuery = queryBuilder.buildQuery(txn.database(), request.filter());

    // 2. Apply sort order
    QuerySortOrder<E> sortOrder = queryBuilder.buildSortOrder(request.sortBy());
    EntityQuery<E> query =
        new EntityQuery<>(
            baseQuery.database(),
            baseQuery.entityType(),
            baseQuery.filters(),
            sortOrder,
            0);

    // 3. Create query context and execute via QueryPlanner
    QueryContext context = new QueryContext(txn, debugPlans);
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
    List<E> entities;
    List<QueryData<E>> profiledResults = null;
    if (debugPlans) {
      profiledResults = bestPlan.executeProfiled();
      entities =
          profiledResults.stream()
              .map(QueryData::data)
              .filter(e -> e.count() > 0)
              .collect(Collectors.toList());
    } else {
      entities =
          bestPlan.stream()
              .map(QueryData::data)
              .filter(e -> e.count() > 0)
              .collect(Collectors.toList());
    }

    // 5. Build debug info if requested
    QueryPlanDebugInfo debugInfo = null;
    if (debugPlans) {
      debugInfo = buildDebugInfo(txn, query, queryBuilder, plans, profiledResults,
          executeAll, request);
    }

    // 6. Apply pagination
    int totalCount = entities.size();
    int fromIndex = Math.min(offset, totalCount);
    int toIndex = Math.min(offset + limit, totalCount);
    List<E> paginatedEntities = entities.subList(fromIndex, toIndex);

    // 7. Convert to DTOs
    List<D> dtos = paginatedEntities.stream().map(toDtoFn).collect(Collectors.toList());

    long endTime = System.currentTimeMillis();

    // 8. Build metadata
    SearchMetadata metadata =
        new SearchMetadata(request.filter(), request.sortBy(), endTime - startTime);

    return new EntitySearchResponse<>(dtos, dtos.size(), totalCount, offset, limit, metadata,
        debugInfo);
  }

  private <E extends Entity & Comparable<E>> @NotNull QueryPlanDebugInfo buildDebugInfo(
      @NotNull DatabaseReadTransaction txn,
      @NotNull EntityQuery<E> query,
      @NotNull AbstractEntityQueryBuilder<E> queryBuilder,
      @NotNull List<QueryOperator<E>> plans,
      @NotNull List<QueryData<E>> bestPlanResults,
      boolean executeAll,
      @NotNull EntitySearchRequest request) {
    String queryDescription = QueryDescriptionFormatter.format(query);

    // Collect result IDs from the best plan for comparison
    List<Integer> bestPlanResultIds = null;
    if (executeAll) {
      bestPlanResultIds = bestPlanResults.stream()
          .map(QueryData::id)
          .sorted()
          .collect(Collectors.toList());
    }

    List<QueryPlanDto> planDtos = new ArrayList<>();
    Boolean allPlansAgree = executeAll ? true : null;

    for (int i = 0; i < plans.size(); i++) {
      QueryOperator<E> plan = plans.get(i);
      String label = "Plan " + (i + 1);

      if (i == 0) {
        // Best plan (cheapest) was already executed via executeProfiled
        int resultCount = bestPlanResults.size();
        planDtos.add(queryPlanDtoConverter.convertPlan(label, plan, true, resultCount, null));
      } else if (executeAll) {
        long estCost = (long) plan.getQueryCost().estimatedTotalCost();
        if (estCost >= MAX_PLAN_COST) {
          log.debug("Skipping plan {} (estimated cost {})", i + 1, estCost);
          planDtos.add(queryPlanDtoConverter.convertPlan(label, plan, false, null, null));
        } else {
          // Execute alternative plan with a fresh context to avoid shared operator state
          EntityQuery<E> freshBaseQuery = queryBuilder.buildQuery(txn.database(), request.filter());
          QuerySortOrder<E> freshSortOrder = queryBuilder.buildSortOrder(request.sortBy());
          EntityQuery<E> freshQuery =
              new EntityQuery<>(
                  freshBaseQuery.database(),
                  freshBaseQuery.entityType(),
                  freshBaseQuery.filters(),
                  freshSortOrder,
                  0);

          QueryContext freshContext = new QueryContext(txn, true);
          var planner = txn.database().queryPlanner();
          List<QueryOperator<E>> freshPlans = planner.sortQueryPlansByCost(
              planner.getEntityQueryPlans(freshContext, freshQuery, true));
          QueryOperator<E> freshPlan = freshPlans.get(i);
          List<QueryData<E>> altResults = freshPlan.executeProfiled();
          int resultCount = altResults.size();

          List<Integer> altResultIds = altResults.stream()
              .map(QueryData::id)
              .sorted()
              .collect(Collectors.toList());
          boolean differs = !altResultIds.equals(bestPlanResultIds);
          if (differs) {
            allPlansAgree = false;
          }

          planDtos.add(queryPlanDtoConverter.convertPlan(label, freshPlan, true, resultCount,
              differs));
        }
      } else {
        // Not executed — estimates only
        planDtos.add(queryPlanDtoConverter.convertPlan(label, plan, false, null, null));
      }
    }

    return new QueryPlanDebugInfo(queryDescription, 0, allPlansAgree, planDtos);
  }
}
