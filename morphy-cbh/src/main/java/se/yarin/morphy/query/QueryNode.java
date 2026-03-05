package se.yarin.morphy.query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public abstract class QueryNode<T> {

  public abstract @NotNull List<QueryNode<?>> sources();

  public abstract @NotNull SortOrder<T> sortOrder();

  public abstract boolean mayContainDuplicates();

  public abstract @NotNull Stream<QueryData<T>> stream();

  public String debugString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this);
    sb.append("\n");
    for (QueryNode<?> source : sources()) {
      sb.append(indent(source.debugString())).append("\n");
    }
    return sb.toString().stripTrailing();
  }

  private static String indent(String s) {
    return Arrays.stream(s.split("\n")).map(line -> "  " + line).collect(Collectors.joining("\n"));
  }
}
