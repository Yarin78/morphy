package se.yarin.morphy.entities.filters;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;

public class SourcePublicationFilter implements EntityFilter<Source> {
  private final @NotNull Date fromDate;
  private final @NotNull Date toDate;

  public SourcePublicationFilter(@NotNull Date fromDate, @NotNull Date toDate) {
    this.fromDate = fromDate;
    this.toDate = toDate;
  }

  @Override
  public boolean matches(@NotNull Source source) {
    Date pub = source.publication();
    return (fromDate.isUnset() || fromDate.compareTo(pub) <= 0)
        && (toDate.isUnset() || toDate.compareTo(pub) >= 0);
  }

  @Override
  public String toString() {
    String fromStr = fromDate.isUnset() ? null : ("publication >= '" + fromDate + "'");
    String toStr = toDate.isUnset() ? null : ("publication <= '" + toDate + "'");
    if (fromStr == null && toStr == null) {
      return "true";
    } else if (fromStr != null && toStr != null) {
      return fromStr + " and " + toStr;
    } else {
      return fromStr != null ? fromStr : toStr;
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
    SourcePublicationFilter that = (SourcePublicationFilter) o;
    return fromDate.equals(that.fromDate) && toDate.equals(that.toDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromDate, toDate);
  }
}
