package se.yarin.morphy.queries.visualisation;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.GameQuery;

/** Formats {@link GameQuery} and {@link EntityQuery} as human-readable multi-line descriptions. */
public class QueryDescriptionFormatter {

  public static @NotNull String format(@NotNull GameQuery query) {
    StringBuilder sb = new StringBuilder("Game Query");
    if (query.limit() > 0) sb.append(" (limit ").append(query.limit()).append(")");
    sb.append("\n");
    if (!query.gameFilters().isEmpty()) {
      sb.append("  Game filters:\n");
      for (var f : query.gameFilters()) {
        sb.append("    ").append(f).append("\n");
      }
    }
    for (var join : query.entityJoins()) {
      sb.append("  ")
          .append(join.getEntityType().nameSingularCapitalized())
          .append(" join");
      if (join.joinCondition() != null) {
        sb.append(" (").append(join.joinCondition()).append(")");
      }
      sb.append(":\n");
      for (var f : join.entityQuery().filters()) {
        sb.append("    ").append(f).append("\n");
      }
    }
    return sb.toString().stripTrailing();
  }

  public static <T extends IdObject> @NotNull String format(@NotNull EntityQuery<T> query) {
    StringBuilder sb =
        new StringBuilder(query.entityType().nameSingularCapitalized() + " Query");
    if (query.limit() > 0) sb.append(" (limit ").append(query.limit()).append(")");
    sb.append("\n");
    if (query.filters() != null && !query.filters().isEmpty()) {
      sb.append("  Entity filters:\n");
      for (var f : query.filters()) {
        sb.append("    ").append(f).append("\n");
      }
    }
    if (query.gameQuery() != null) {
      sb.append("  Via games");
      if (query.joinCondition() != null) {
        sb.append(" (").append(query.joinCondition()).append(")");
      }
      sb.append(":\n");
      sb.append("    ").append(format(query.gameQuery()).replace("\n", "\n    "));
    }
    return sb.toString().stripTrailing();
  }
}
