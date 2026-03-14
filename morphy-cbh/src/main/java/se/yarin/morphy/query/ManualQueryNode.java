package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ManualQueryNode<T> extends QueryNode<T> {
  private final @NotNull List<QueryData<T>> data;
  private final @NotNull SortOrder<T> sortOrder;
  private final boolean duplicates;

  public ManualQueryNode(
      @NotNull List<QueryData<T>> data,
      @NotNull SortOrder<T> sortOrder,
      boolean duplicates) {
    this.data = List.copyOf(data);
    this.sortOrder = sortOrder;
    this.duplicates = duplicates;
  }

  public static <T> ManualQueryNode<T> verified(
      @NotNull List<QueryData<T>> data,
      @NotNull SortOrder<T> sortOrder,
      boolean duplicates) {
    if (!sortOrder.isNone()) {
      for (int i = 1; i < data.size(); i++) {
        if (sortOrder.compare(data.get(i - 1), data.get(i)) > 0) {
          throw new IllegalArgumentException(
              "Data is not sorted according to the specified sort order at index " + i);
        }
      }
    }
    if (!duplicates) {
      for (int i = 1; i < data.size(); i++) {
        if (data.get(i).id() == data.get(i - 1).id()) {
          throw new IllegalArgumentException(
              "Data contains duplicate id " + data.get(i).id() + " at index " + i);
        }
      }
    }
    return new ManualQueryNode<>(data, sortOrder, duplicates);
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return sortOrder;
  }

  @Override
  public boolean mayContainDuplicates() {
    return duplicates;
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    return data.stream();
  }

  @Override
  public String toString() {
    return "Manual[" + data.size() + " rows]";
  }
}
