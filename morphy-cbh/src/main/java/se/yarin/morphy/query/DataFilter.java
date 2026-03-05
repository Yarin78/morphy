package se.yarin.morphy.query;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface DataFilter<T> {
  boolean matches(@NotNull T data);
}
