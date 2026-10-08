package se.yarin.morphy.service.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a position search used its index.
 *
 * @param status {@code ready}: the index was used and is up to date; {@code stale}: it was used,
 *     but its database has changed since it was built; {@code missing}: there is no index to use
 *     (it hasn't been built, can't be read or was built with another filter), so every game was
 *     played through instead
 * @param message why it's missing
 * @param missingGames when stale, the games added to the database since it was built, so not in
 *     it; for an index of a filter, not all of them would be. 0 when games were only changed or
 *     deleted
 */
public record PositionIndexState(
    @NotNull String status, @Nullable String message, @Nullable Long missingGames) {

  static final PositionIndexState READY = new PositionIndexState("ready", null, null);

  static @NotNull PositionIndexState stale(long missingGames) {
    return new PositionIndexState("stale", null, missingGames);
  }

  static @NotNull PositionIndexState missing(@NotNull String why) {
    return new PositionIndexState("missing", why, null);
  }
}
