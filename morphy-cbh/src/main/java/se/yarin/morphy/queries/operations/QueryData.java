package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;

import java.util.function.BiFunction;

public record QueryData<T extends IdObject>(int id, @Nullable T data, @Nullable Object extra) implements IdObject {
  public QueryData {
    if (data != null && data.id() != id) {
      throw new IllegalArgumentException("The id must much that of the data");
    }
    if (id < 0) {
      // Implies we're streaming non-entities which indicates an error elsewhere
      throw new IllegalArgumentException("Id must be non-negative");
    }
  }

  public QueryData(int id) {
    this(id, null, null);
  }

  public QueryData(@NotNull T data) {
    this(data.id(), data, null);
  }

  public QueryData(@NotNull T data, @Nullable Object extra) {
    this(data.id(), data, extra);
  }

  public <E> @Nullable E extra(@NotNull Class<E> type) {
    return type.isInstance(extra) ? type.cast(extra) : null;
  }

  public QueryData<T> withExtra(@Nullable Object extra) {
    return new QueryData<>(id, data, extra);
  }

  public static <K extends IdObject> BiFunction<QueryData<K>, QueryData<K>, QueryData<K>> merger() {
    return (q1, q2) -> {
      assert q1.id() == q2.id();
      return new QueryData<>(q1.id, q1.data == null ? q2.data : q1.data, q1.extra != null ? q1.extra : q2.extra);
    };
  }
}
