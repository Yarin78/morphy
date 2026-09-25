package se.yarin.morphy.api.query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The order of a search result: a list of sort keys, most significant first. An empty sort means
 * the natural order of the target (game id for games, the entity kind's default for entities).
 *
 * <p>Which field names are valid depends on the target and the database; see {@link
 * SearchSchema#sortFields()}. A database rejects an unknown field with an {@link
 * IllegalArgumentException}.
 */
public record Sort(@NotNull List<Key> keys) {

  /** Ascending or descending order. */
  public enum Direction {
    ASCENDING,
    DESCENDING
  }

  /**
   * One sort key.
   *
   * @param field the field name, matched case-insensitively
   * @param direction the direction, or null for the field's default direction
   */
  public record Key(@NotNull String field, @Nullable Direction direction) {
    @Override
    public String toString() {
      String prefix =
          direction == null ? "" : direction == Direction.DESCENDING ? "-" : "+";
      return prefix + field;
    }
  }

  private static final Sort NATURAL = new Sort(List.of());

  public Sort {
    keys = List.copyOf(keys);
  }

  /** The natural order of the target. */
  public static @NotNull Sort natural() {
    return NATURAL;
  }

  public static @NotNull Sort by(@NotNull String field, @Nullable Direction direction) {
    return new Sort(List.of(new Key(field, direction)));
  }

  /**
   * Parses a sort spec: comma-separated field names, each optionally prefixed by {@code +}
   * (ascending) or {@code -} (descending), e.g. {@code "-date,+id"}. A blank spec, or {@code
   * "default"}, is the natural order.
   */
  public static @NotNull Sort parse(@Nullable String spec) {
    if (spec == null || spec.isBlank() || spec.trim().equalsIgnoreCase("default")) {
      return NATURAL;
    }
    List<Key> keys = new ArrayList<>();
    for (String part : spec.split(",")) {
      part = part.trim();
      if (part.isEmpty()) {
        continue;
      }
      Direction direction = null;
      if (part.startsWith("-")) {
        direction = Direction.DESCENDING;
        part = part.substring(1).trim();
      } else if (part.startsWith("+")) {
        direction = Direction.ASCENDING;
        part = part.substring(1).trim();
      }
      if (part.isEmpty()) {
        throw new IllegalArgumentException("Invalid sort spec: '" + spec + "'");
      }
      keys.add(new Key(part, direction));
    }
    return new Sort(keys);
  }

  public boolean isNatural() {
    return keys.isEmpty();
  }

  /** The spec form, the inverse of {@link #parse(String)}; {@code "default"} for natural order. */
  @Override
  public String toString() {
    return isNatural()
        ? "default"
        : keys.stream().map(Key::toString).collect(Collectors.joining(","));
  }
}
