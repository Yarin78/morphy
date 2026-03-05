package se.yarin.morphy.query;

import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class SortOrder<T> implements Comparator<QueryData<T>> {

  public enum Direction {
    ASCENDING,
    DESCENDING;

    public String shortName() {
      return this == ASCENDING ? "asc" : "desc";
    }
  }

  private final @NotNull List<SortField<T>> sortFields;
  private final @NotNull List<Direction> sortDirections;

  private SortOrder(
      @NotNull List<SortField<T>> sortFields, @NotNull List<Direction> sortDirections) {
    if (sortFields.size() != sortDirections.size()) {
      throw new IllegalArgumentException("sortFields and sortDirections must have the same size");
    }
    this.sortFields = List.copyOf(sortFields);
    this.sortDirections = List.copyOf(sortDirections);
  }

  public static <T> SortOrder<T> none() {
    return new SortOrder<>(List.of(), List.of());
  }

  public static <T> SortOrder<T> byId() {
    return new SortOrder<>(List.of(SortField.id()), List.of(Direction.ASCENDING));
  }

  public static <T> SortOrder<T> of(@NotNull SortField<T> field, @NotNull Direction direction) {
    return new SortOrder<>(List.of(field), List.of(direction));
  }

  public static <T> SortOrder<T> of(
      @NotNull List<SortField<T>> fields, @NotNull List<Direction> directions) {
    return new SortOrder<>(fields, directions);
  }

  public @NotNull List<SortField<T>> sortFields() {
    return sortFields;
  }

  public @NotNull List<Direction> sortDirections() {
    return sortDirections;
  }

  public boolean isNone() {
    return sortFields.isEmpty();
  }

  public boolean isSameOrStronger(@NotNull SortOrder<T> other) {
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

  @Override
  public String toString() {
    if (isNone()) return "none";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sortFields.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(sortFields.get(i).name()).append(" ").append(sortDirections.get(i).shortName());
    }
    return sb.toString();
  }
}
