package se.yarin.morphy.queries.visualisation;

import java.util.*;
import se.yarin.morphy.queries.operations.OperatorCost;
import se.yarin.morphy.queries.operations.QueryCost;
import se.yarin.morphy.queries.operations.QueryOperator;

/**
 * Traverses a query operator DAG and serializes the graph structure to JSON for the HTML
 * visualizer.
 */
class QueryPlanGraphBuilder {

  record PlanEntry(String label, QueryOperator<?> plan) {}

  static String buildPlansJson(List<PlanEntry> plans) {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 0; i < plans.size(); i++) {
      if (i > 0) sb.append(",\n");
      PlanEntry pe = plans.get(i);

      List<Map<String, Object>> nodes = new ArrayList<>();
      List<int[]> edges = new ArrayList<>();
      Map<QueryOperator<?>, Integer> visited = new IdentityHashMap<>();
      collectGraph(pe.plan, nodes, edges, visited);
      QueryCost totalCost = pe.plan.getQueryCost();

      sb.append("  {\"label\":\"").append(escapeJson(pe.label)).append("\",");
      sb.append("\"nodes\":").append(toNodesJson(nodes)).append(",");
      sb.append("\"edges\":").append(toEdgesJson(edges)).append(",");
      sb.append("\"totalCost\":").append(toTotalCostJson(totalCost)).append("}");
    }
    sb.append("\n]");
    return sb.toString();
  }

  static String buildResultsJson(List<String> columns, List<List<String>> rows) {
    StringBuilder sb = new StringBuilder("{\"columns\":[");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("\"").append(escapeJson(columns.get(i))).append("\"");
    }
    sb.append("],\"rows\":[");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("[");
      List<String> row = rows.get(i);
      for (int j = 0; j < row.size(); j++) {
        if (j > 0) sb.append(",");
        sb.append("\"").append(escapeJson(row.get(j))).append("\"");
      }
      sb.append("]");
    }
    sb.append("]}");
    return sb.toString();
  }

  // --- Graph traversal ---

  private static void collectGraph(
      QueryOperator<?> op,
      List<Map<String, Object>> nodes,
      List<int[]> edges,
      Map<QueryOperator<?>, Integer> visited) {
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

    Map<String, Object> nd = new LinkedHashMap<>();
    nd.put("id", id);
    nd.put("name", name);
    nd.put("params", params);
    nd.put("type", categorize(op));
    nd.put("hasFullData", op.hasFullData());
    nd.put("sorted", !op.sortOrder().isNone());
    nd.put("sortOrder", op.sortOrder().toString());
    nd.put("mayDuplicate", op.mayContainDuplicates());
    nd.put("estRows", cost.estimateRows());
    nd.put("estDeser", cost.estimateDeserializations());
    nd.put("estPageReads", cost.estimatePageReads());
    nd.put("actRows", cost.actualRows());
    nd.put("actDeser", cost.actualDeserializations());
    nd.put("actPhysReads", cost.actualPhysicalPageReads());
    nd.put("actLogReads", cost.actualLogicalPageReads());
    nd.put("actDuplicate", cost.actualIsDuplicate());
    nodes.add(nd);

    for (QueryOperator<?> source : op.sources()) {
      collectGraph(source, nodes, edges, visited);
      edges.add(new int[] {visited.get(source), id});
    }
  }

  private static String categorize(QueryOperator<?> op) {
    String cls = op.getClass().getSimpleName();
    if (cls.contains("Scan") || cls.equals("Manual")) return "source";
    if (cls.contains("Lookup")) return "lookup";
    if (cls.contains("Join") || cls.contains("IdsByEntities") || cls.contains("IdsByGames"))
      return "join";
    return "utility";
  }

  // --- JSON helpers ---

  private static String toNodesJson(List<Map<String, Object>> nodes) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < nodes.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append(mapToJson(nodes.get(i)));
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toEdgesJson(List<int[]> edges) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < edges.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append(String.format("{\"from\":%d,\"to\":%d}", edges.get(i)[0], edges.get(i)[1]));
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toTotalCostJson(QueryCost c) {
    return String.format(
        Locale.US,
        "{\"estRows\":%d,\"estPageReads\":%d,\"estDeser\":%d,"
            + "\"estCpuCost\":%.1f,\"estIOCost\":%.1f,\"estTotalCost\":%.1f,"
            + "\"actRows\":%d,\"actPhysReads\":%d,\"actLogReads\":%d,"
            + "\"actDeser\":%d,\"actWallClock\":%d}",
        c.estimatedRows(),
        c.estimatedPageReads(),
        c.estimatedDeserializations(),
        c.estimatedCpuCost(),
        c.estimatedIOCost(),
        c.estimatedTotalCost(),
        c.actualRows(),
        c.actualPhysicalPageReads(),
        c.actualLogicalPageReads(),
        c.actualDeserializations(),
        c.actualWallClockTime());
  }

  private static String mapToJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (var entry : map.entrySet()) {
      if (!first) sb.append(",");
      first = false;
      sb.append("\"").append(entry.getKey()).append("\":");
      Object v = entry.getValue();
      if (v instanceof String s) {
        sb.append("\"").append(escapeJson(s)).append("\"");
      } else if (v instanceof Boolean b) {
        sb.append(b);
      } else {
        sb.append(v);
      }
    }
    sb.append("}");
    return sb.toString();
  }

  static String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
