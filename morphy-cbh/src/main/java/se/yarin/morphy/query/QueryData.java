package se.yarin.morphy.query;

import java.util.function.BiFunction;
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

  public static <T> BiFunction<QueryData<T>, QueryData<T>, QueryData<T>> merger() {
    return (q1, q2) -> {
      assert q1.id() == q2.id();
      return new QueryData<>(
          q1.id(), q1.data() == null ? q2.data() : q1.data(), q1.extra() != null ? q1.extra() : q2.extra());
    };
  }
}
