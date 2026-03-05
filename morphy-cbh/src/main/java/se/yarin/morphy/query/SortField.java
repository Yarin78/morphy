package se.yarin.morphy.query;

import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

public class SortField<T> {
  private final @NotNull Comparator<QueryData<T>> comparator;
  private final @NotNull String name;
  private final SortOrder.@NotNull Direction defaultDirection;

  public SortField(
      @NotNull Comparator<QueryData<T>> comparator,
      @NotNull String name,
      SortOrder.@NotNull Direction defaultDirection) {
    this.comparator = comparator;
    this.name = name;
    this.defaultDirection = defaultDirection;
  }

  public SortField(@NotNull Comparator<QueryData<T>> comparator, @NotNull String name) {
    this(comparator, name, SortOrder.Direction.ASCENDING);
  }

  public static <T> SortField<T> id() {
    return new SortField<>(Comparator.comparingInt(QueryData::id), "id");
  }

  public @NotNull Comparator<QueryData<T>> comparator() {
    return comparator;
  }

  public @NotNull String name() {
    return name;
  }

  public SortOrder.@NotNull Direction defaultDirection() {
    return defaultDirection;
  }

  public int compare(QueryData<T> a, QueryData<T> b) {
    return comparator.compare(a, b);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SortField<?> that = (SortField<?>) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
