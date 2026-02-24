package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

public class QuerySortOrder<T extends IdObject> implements Comparator<QueryData<T>> {

  public enum Direction {
    ASCENDING,
    DESCENDING;

    public String shortName() {
      return this == ASCENDING ? "asc" : "desc";
    }
  }

  private final List<QuerySortField<T>> sortFields;
  private final List<Direction> sortDirections;

  public static <T extends IdObject> QuerySortOrder<T> none() {
    return new QuerySortOrder<>();
  }

  public static <T extends IdObject> QuerySortOrder<T> byId() {
    return new QuerySortOrder<>(QuerySortField.id(), Direction.ASCENDING);
  }

  public static <T extends Entity> QuerySortOrder<T> byEntityCount() {
    return new QuerySortOrder<>(QuerySortField.entityCount(), Direction.DESCENDING);
  }

  public static QuerySortOrder<Player> byPlayerDefaultIndex() {
    return byPlayerDefaultIndex(false);
  }

  public static QuerySortOrder<Player> byPlayerDefaultIndex(boolean reverse) {
    return new QuerySortOrder<>(
        List.of(QuerySortField.playerName()),
        List.of(!reverse ? Direction.ASCENDING : Direction.DESCENDING));
  }

  public static QuerySortOrder<Player> byPlayerFirstName() {
    return byPlayerFirstName(false);
  }

  public static QuerySortOrder<Player> byPlayerFirstName(boolean reverse) {
    Direction direction = !reverse ? Direction.ASCENDING : Direction.DESCENDING;
    return new QuerySortOrder<>(
        List.of(QuerySortField.playerFirstName()),
        List.of(direction));
  }

  public static QuerySortOrder<Tournament> byTournamentDefaultIndex() {
    return byTournamentDefaultIndex(false);
  }

  public static QuerySortOrder<Tournament> byTournamentDefaultIndex(boolean reverse) {
    return new QuerySortOrder<>(
        List.of(
            QuerySortField.tournamentYear(),
            QuerySortField.tournamentTitle(),
            QuerySortField.tournamentPlace(),
            QuerySortField.tournamentStartDate()),
        List.of(
            !reverse ? Direction.DESCENDING : Direction.ASCENDING,
            !reverse ? Direction.ASCENDING : Direction.DESCENDING,
            !reverse ? Direction.ASCENDING : Direction.DESCENDING,
            !reverse ? Direction.DESCENDING : Direction.ASCENDING));
  }

  public static QuerySortOrder<Annotator> byAnnotatorDefaultIndex() {
    return byAnnotatorDefaultIndex(false);
  }

  public static QuerySortOrder<Annotator> byAnnotatorDefaultIndex(boolean reverse) {
    return new QuerySortOrder<>(
        List.of(QuerySortField.annotatorName()),
        List.of(!reverse ? Direction.ASCENDING : Direction.DESCENDING));
  }

  public static QuerySortOrder<Source> bySourceDefaultIndex() {
    return bySourceDefaultIndex(false);
  }

  public static QuerySortOrder<Source> bySourceDefaultIndex(boolean reverse) {
    return new QuerySortOrder<>(
        List.of(QuerySortField.sourceTitle()),
        List.of(!reverse ? Direction.ASCENDING : Direction.DESCENDING));
  }

  public static QuerySortOrder<GameTag> byGameTagDefaultIndex() {
    return byGameTagDefaultIndex(false);
  }

  public static QuerySortOrder<GameTag> byGameTagDefaultIndex(boolean reverse) {
    Direction direction = !reverse ? Direction.ASCENDING : Direction.DESCENDING;
    return new QuerySortOrder<>(
        List.of(
            QuerySortField.gameTagEnglishTitle(),
            QuerySortField.gameTagGermanTitle(),
            QuerySortField.gameTagFrenchTitle(),
            QuerySortField.gameTagSpanishTitle(),
            QuerySortField.gameTagItalianTitle(),
            QuerySortField.gameTagDutchTitle(),
            QuerySortField.gameTagSlovenianTitle()),
        List.of(direction, direction, direction, direction, direction, direction, direction));
  }

  public static QuerySortOrder<Team> byTeamDefaultIndex() {
    return byTeamDefaultIndex(false);
  }

  public static QuerySortOrder<Team> byTeamDefaultIndex(boolean reverse) {
    Direction direction = !reverse ? Direction.ASCENDING : Direction.DESCENDING;
    return new QuerySortOrder<>(
        List.of(
            QuerySortField.teamTitle(),
            QuerySortField.teamNumber(),
            QuerySortField.teamSeason(),
            QuerySortField.teamYear(),
            QuerySortField.teamNation()),
        List.of(direction, direction, direction, direction, direction));
  }

  public static <T extends Entity & Comparable<T>> QuerySortOrder<?> byEntityDefaultIndex(
      EntityType entityType, boolean reverse) {
    return switch (entityType) {
      case PLAYER -> byPlayerDefaultIndex(reverse);
      case TOURNAMENT -> byTournamentDefaultIndex(reverse);
      case ANNOTATOR -> byAnnotatorDefaultIndex(reverse);
      case SOURCE -> bySourceDefaultIndex(reverse);
      case GAME_TAG -> byGameTagDefaultIndex(reverse);
      case TEAM -> byTeamDefaultIndex(reverse);
      default ->
          throw new IllegalArgumentException("No default index for entity type " + entityType);
    };
  }

  private QuerySortOrder() {
    this.sortFields = List.of();
    this.sortDirections = List.of();
  }

  public QuerySortOrder(@NotNull QuerySortField<T> key, @NotNull Direction direction) {
    this(List.of(key), List.of(direction));
  }

  public QuerySortOrder(
      @NotNull QuerySortField<T> primaryKey,
      @NotNull Direction primaryDirection,
      @NotNull QuerySortField<T> secondaryKey,
      @NotNull Direction secondaryDirection) {
    this(List.of(primaryKey, secondaryKey), List.of(primaryDirection, secondaryDirection));
  }

  public QuerySortOrder(
      @NotNull List<QuerySortField<T>> keys, @NotNull List<Direction> directions) {
    long lookupCount = keys.stream().filter(f -> f.requiredLookup() != null).distinct().count();
    if (lookupCount > 1) {
      throw new IllegalArgumentException(
          "At most one distinct required lookup is allowed in a sort order");
    }
    this.sortFields = List.copyOf(keys);
    this.sortDirections = List.copyOf(directions);
  }

  @Override
  public int compare(@NotNull QueryData<T> o1, @NotNull QueryData<T> o2) {
    for (int i = 0; i < sortFields.size(); i++) {
      int comp = sortFields.get(i).compare(o1, o2);
      if (comp != 0) {
        return sortDirections.get(i) == Direction.ASCENDING ? comp : -comp;
      }
    }
    return 0;
  }

  public boolean isNone() {
    return sortFields.isEmpty();
  }

  /** Determines if this sort order is identical or stronger than the other sort order */
  public boolean isSameOrStronger(@NotNull QuerySortOrder<T> other) {
    if (this.sortFields.size() < other.sortFields.size()) {
      return false;
    }
    for (int i = 0; i < other.sortFields.size(); i++) {
      if (!this.sortFields.get(i).equals(other.sortFields.get(i))) {
        return false;
      }
      if (!this.sortDirections.get(i).equals(other.sortDirections.get(i))) {
        return false;
      }
    }
    return true;
  }

  public @Nullable BiFunction<QueryContext, QueryOperator<T>, QueryOperator<T>> requiredLookup() {
    return sortFields.stream()
        .map(QuerySortField::requiredLookup)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  public boolean requiresData() {
    return sortFields.stream().anyMatch(QuerySortField::requiresData);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sortFields.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(sortFields.get(i).toString())
          .append(" ")
          .append(sortDirections.get(i) == Direction.ASCENDING ? "asc" : "desc");
    }
    return sb.toString();
  }
}
