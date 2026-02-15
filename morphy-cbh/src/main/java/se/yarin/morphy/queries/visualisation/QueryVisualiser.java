package se.yarin.morphy.queries.visualisation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;

/**
 * Generates a self-contained HTML page visualizing one or more query execution plans as left-to-right
 * DAGs. Supports tabbed view for comparing plans, a logical query description, and a sample results
 * table.
 *
 * <p>Usage (high-level):
 *
 * <pre>
 * QueryVisualiser.builder()
 *     .addLogicalQuery(gameQuery)
 *     .addGameQueryPlans(plans)
 *     .addGameResults(results)
 *     .writeHtmlFile(Path.of("/tmp/plan.html"));
 * </pre>
 *
 * <p>Usage (low-level):
 *
 * <pre>
 * QueryVisualiser.builder()
 *     .queryDescription("Player Query\n  Game join: ...")
 *     .addPlan("Plan 1", executedPlan1)
 *     .results(List.of("Name","Games"), List.of(List.of("Carlsen","2814")))
 *     .writeHtmlFile(Path.of("/tmp/plan.html"));
 * </pre>
 */
public class QueryVisualiser {

  private static String htmlTemplate;

  public static Builder builder() {
    return new Builder();
  }

  /** Convenience: single plan, no query description or results. */
  public static void writeHtmlFile(QueryOperator<?> root, Path outputPath) throws IOException {
    builder().addPlan("Plan", root).writeHtmlFile(outputPath);
  }

  public static class Builder {
    private String queryDescription = "";
    private final List<QueryPlanGraphBuilder.PlanEntry> plans = new ArrayList<>();
    private List<String> resultColumns = List.of();
    private List<List<String>> resultRows = List.of();

    // --- High-level API ---

    public Builder addLogicalQuery(@NotNull GameQuery gameQuery) {
      this.queryDescription = QueryDescriptionFormatter.format(gameQuery);
      return this;
    }

    public <T extends IdObject> Builder addLogicalQuery(@NotNull EntityQuery<T> entityQuery) {
      this.queryDescription = QueryDescriptionFormatter.format(entityQuery);
      return this;
    }

    public Builder addGameQueryPlans(@NotNull List<QueryOperator<Game>> queryPlans) {
      for (int i = 0; i < queryPlans.size(); i++) {
        plans.add(new QueryPlanGraphBuilder.PlanEntry("Plan " + (i + 1), queryPlans.get(i)));
      }
      return this;
    }

    public <T extends IdObject> Builder addEntityQueryPlans(
        @NotNull List<QueryOperator<T>> queryPlans) {
      for (int i = 0; i < queryPlans.size(); i++) {
        plans.add(new QueryPlanGraphBuilder.PlanEntry("Plan " + (i + 1), queryPlans.get(i)));
      }
      return this;
    }

    public Builder addGameResults(@NotNull List<QueryData<Game>> results) {
      this.resultColumns = QueryResultFormatter.gameColumns();
      this.resultRows = QueryResultFormatter.formatGameRows(results);
      return this;
    }

    public <T extends IdObject> Builder addEntityResults(
        @NotNull List<QueryData<T>> results, @NotNull EntityType entityType) {
      this.resultColumns = QueryResultFormatter.entityColumns(entityType);
      this.resultRows = QueryResultFormatter.formatEntityRows(results, entityType);
      return this;
    }

    // --- Low-level API ---

    public Builder queryDescription(String description) {
      this.queryDescription = description;
      return this;
    }

    public Builder addPlan(String label, QueryOperator<?> plan) {
      plans.add(new QueryPlanGraphBuilder.PlanEntry(label, plan));
      return this;
    }

    public Builder results(List<String> columns, List<List<String>> rows) {
      this.resultColumns = List.copyOf(columns);
      this.resultRows = rows.stream().map(List::copyOf).toList();
      return this;
    }

    public void writeHtmlFile(Path outputPath) throws IOException {
      Files.writeString(outputPath, buildHtml());
    }

    public String buildHtml() {
      String plansJson = QueryPlanGraphBuilder.buildPlansJson(plans);
      String resultsJson =
          QueryPlanGraphBuilder.buildResultsJson(resultColumns, resultRows);

      return loadTemplate()
          .replace(
              "/*{{QUERY_DESC}}*/",
              "\"" + QueryPlanGraphBuilder.escapeJson(queryDescription) + "\"")
          .replace("/*{{PLANS}}*/", plansJson)
          .replace("/*{{RESULTS}}*/", resultsJson);
    }
  }

  private static synchronized String loadTemplate() {
    if (htmlTemplate == null) {
      try (InputStream is =
          QueryVisualiser.class.getResourceAsStream("query-plan-template.html")) {
        if (is == null) {
          throw new IllegalStateException(
              "query-plan-template.html resource not found on classpath");
        }
        htmlTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load query-plan-template.html", e);
      }
    }
    return htmlTemplate;
  }
}
