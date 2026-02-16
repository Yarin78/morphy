package se.yarin.morphy.service.queryplans;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import se.yarin.morphy.queries.operations.OperatorCost;
import se.yarin.morphy.queries.operations.QueryCost;
import se.yarin.morphy.queries.operations.QueryOperator;

@Component
public class QueryPlanDtoConverter {

  public @NotNull QueryPlanDto convertPlan(
      @NotNull String label,
      @NotNull QueryOperator<?> plan,
      boolean executed,
      @Nullable Integer resultCount,
      @Nullable Boolean resultsDifferFromSelected) {
    List<QueryOperatorNodeDto> nodes = new ArrayList<>();
    List<QueryPlanEdgeDto> edges = new ArrayList<>();
    Map<QueryOperator<?>, Integer> visited = new IdentityHashMap<>();
    collectGraph(plan, nodes, edges, visited, executed);

    QueryCost totalCost = plan.getQueryCost();
    QueryCostDto costDto = convertQueryCost(totalCost, executed);

    return new QueryPlanDto(label, nodes, edges, costDto, executed, resultCount,
        resultsDifferFromSelected);
  }

  private void collectGraph(
      @NotNull QueryOperator<?> op,
      @NotNull List<QueryOperatorNodeDto> nodes,
      @NotNull List<QueryPlanEdgeDto> edges,
      @NotNull Map<QueryOperator<?>, Integer> visited,
      boolean includeActuals) {
    if (visited.containsKey(op)) return;

    int id = nodes.size();
    visited.put(op, id);

    OperatorCost cost = op.getOperatorCost();
    String fullLabel = op.toString();
    String name;
    String params = "";
    int parenIdx = fullLabel.indexOf('(');
    if (parenIdx >= 0 && fullLabel.endsWith(")")) {
      name = fullLabel.substring(0, parenIdx);
      params = fullLabel.substring(parenIdx + 1, fullLabel.length() - 1);
    } else {
      name = fullLabel;
    }

    OperatorCostDto costDto = convertOperatorCost(cost, includeActuals);

    nodes.add(new QueryOperatorNodeDto(
        id,
        name,
        params,
        categorize(op),
        op.hasFullData(),
        !op.sortOrder().isNone(),
        op.sortOrder().toString(),
        op.mayContainDuplicates(),
        costDto));

    for (QueryOperator<?> source : op.sources()) {
      collectGraph(source, nodes, edges, visited, includeActuals);
      edges.add(new QueryPlanEdgeDto(visited.get(source), id));
    }
  }

  private static @NotNull String categorize(@NotNull QueryOperator<?> op) {
    String cls = op.getClass().getSimpleName();
    if (cls.contains("Scan") || cls.equals("Manual")) return "source";
    if (cls.contains("Lookup")) return "lookup";
    if (cls.contains("Join") || cls.contains("IdsByEntities") || cls.contains("IdsByGames"))
      return "join";
    return "utility";
  }

  private static @NotNull OperatorCostDto convertOperatorCost(
      @NotNull OperatorCost cost, boolean includeActuals) {
    return new OperatorCostDto(
        cost.estimateRows(),
        cost.estimateDeserializations(),
        cost.estimatePageReads(),
        includeActuals ? cost.actualRows() : null,
        includeActuals ? cost.actualDeserializations() : null,
        includeActuals ? cost.actualPhysicalPageReads() : null,
        includeActuals ? cost.actualLogicalPageReads() : null,
        includeActuals ? cost.actualIsDuplicate() : null);
  }

  private static @NotNull QueryCostDto convertQueryCost(
      @NotNull QueryCost cost, boolean includeActuals) {
    return new QueryCostDto(
        cost.estimatedRows(),
        cost.estimatedPageReads(),
        cost.estimatedDeserializations(),
        cost.estimatedCpuCost(),
        cost.estimatedIOCost(),
        cost.estimatedTotalCost(),
        includeActuals ? cost.actualRows() : null,
        includeActuals ? cost.actualPhysicalPageReads() : null,
        includeActuals ? cost.actualLogicalPageReads() : null,
        includeActuals ? cost.actualDeserializations() : null,
        includeActuals ? cost.actualWallClockTime() : null);
  }
}
