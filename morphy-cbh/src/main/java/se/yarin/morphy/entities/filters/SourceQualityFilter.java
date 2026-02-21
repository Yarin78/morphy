package se.yarin.morphy.entities.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.SourceQuality;

public class SourceQualityFilter implements EntityFilter<Source> {
  private final @NotNull Set<SourceQuality> qualities;

  public SourceQualityFilter(@NotNull Set<SourceQuality> qualities) {
    this.qualities = new HashSet<>(qualities);
  }

  public SourceQualityFilter(@NotNull String qualitySpec) {
    qualities =
        Arrays.stream(qualitySpec.split("\\|"))
            .map(String::trim)
            .map(s -> {
              try {
                return SourceQuality.valueOf(s.toUpperCase());
              } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Unknown source quality: '"
                        + s
                        + "'. Available values: "
                        + Arrays.stream(SourceQuality.values())
                            .map(Enum::name)
                            .map(String::toLowerCase)
                            .collect(Collectors.joining(", ")));
              }
            })
            .collect(Collectors.toSet());
  }

  @Override
  public boolean matches(@NotNull Source source) {
    return qualities.contains(source.quality());
  }

  @Override
  public String toString() {
    if (qualities.size() == 1) {
      return "quality = '" + qualities.iterator().next().name().toLowerCase() + "'";
    }
    return "quality in ("
        + qualities.stream()
            .map(q -> "'" + q.name().toLowerCase() + "'")
            .collect(Collectors.joining(", "))
        + ")";
  }

  @Override
  public EntityType entityType() {
    return EntityType.SOURCE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourceQualityFilter that = (SourceQualityFilter) o;
    return qualities.equals(that.qualities);
  }

  @Override
  public int hashCode() {
    return qualities.hashCode();
  }
}
