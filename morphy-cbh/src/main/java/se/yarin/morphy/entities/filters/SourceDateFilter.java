package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;

public class SourceDateFilter implements EntityFilter<Source> {
  private final @NotNull Date fromDate;
  private final @NotNull Date toDate;

  public SourceDateFilter(@NotNull Date fromDate, @NotNull Date toDate) {
    this.fromDate = fromDate;
    this.toDate = toDate;
  }

  private boolean matches(@NotNull Date date) {
    return (fromDate.isUnset() || fromDate.compareTo(date) <= 0)
        && (toDate.isUnset() || toDate.compareTo(date) >= 0);
  }

  @Override
  public boolean matches(@NotNull Source source) {
    return matches(source.date());
  }

  @Override
  public String toString() {
    String fromDateStr = fromDate.isUnset() ? null : ("fromDate >= '" + fromDate + "'");
    String toDateStr = toDate.isUnset() ? null : ("toDate <= '" + toDate + "'");
    if (fromDateStr == null && toDateStr == null) {
      return "true";
    } else if (fromDateStr != null && toDateStr != null) {
      return fromDateStr + " and " + toDateStr;
    } else if (fromDateStr != null) {
      return fromDateStr;
    } else {
      return toDateStr;
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.SOURCE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourceDateFilter that = (SourceDateFilter) o;
    return fromDate.equals(that.fromDate) && toDate.equals(that.toDate);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(fromDate, toDate);
  }
}
