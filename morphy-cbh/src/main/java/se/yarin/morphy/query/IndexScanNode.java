package se.yarin.morphy.query;

import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IndexScanNode<T, K> {
  @NotNull Stream<QueryData<T>> streamRange(@Nullable K start, @Nullable K end);

  @Nullable QueryData<T> getByKey(@NotNull K key);
}
