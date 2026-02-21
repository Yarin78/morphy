package se.yarin.morphy.entities.filters;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;

public class SourceVersionFilter implements EntityFilter<Source> {
  private final int minVersion;
  private final int maxVersion;

  public SourceVersionFilter(int minVersion, int maxVersion) {
    this.minVersion = minVersion;
    this.maxVersion = maxVersion;
  }

  @Override
  public boolean matches(@NotNull Source source) {
    return source.version() >= minVersion && source.version() <= maxVersion;
  }

  @Override
  public String toString() {
    if (minVersion == 0) {
      return "version <= " + maxVersion;
    } else if (maxVersion == Integer.MAX_VALUE) {
      return "version >= " + minVersion;
    } else if (minVersion == maxVersion) {
      return "version = " + minVersion;
    } else {
      return "version >= " + minVersion + " and version <= " + maxVersion;
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
    SourceVersionFilter that = (SourceVersionFilter) o;
    return minVersion == that.minVersion && maxVersion == that.maxVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hash(minVersion, maxVersion);
  }
}
