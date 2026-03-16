package se.yarin.morphy.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record QueryData<T>(int id, @Nullable T data, @Nullable Object extra) {
  public QueryData {
    if (id < 0) {
      throw new IllegalArgumentException("Id must be non-negative");
    }
  }

  public QueryData(int id) {
    this(id, null, null);
  }

  public QueryData(int id, @NotNull T data) {
    this(id, data, null);
  }

  public <E> @Nullable E extra(@NotNull Class<E> type) {
    return type.isInstance(extra) ? type.cast(extra) : null;
  }

  public QueryData<T> withExtra(@Nullable Object extra) {
    return new QueryData<>(id, data, extra);
  }

  public QueryData<T> withData(@Nullable T data) {
    return new QueryData<>(id, data, extra);
  }

  @SuppressWarnings("unchecked")
  static <L, R> QueryData<L> combine(QueryData<L> left, QueryData<R> right, boolean sameType) {
    if (sameType) {
      QueryData<L> r = (QueryData<L>) (QueryData<?>) right;
      return new QueryData<>(
          left.id(),
          left.data() != null ? left.data() : r.data(),
          left.extra() != null ? left.extra() : r.extra());
    } else {
      return left.withExtra(right.data());
    }
  }
}
