package se.yarin.morphy.cb2.games;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.jetbrains.annotations.NotNull;

/**
 * The two timestamp encodings of the format.
 *
 * <ul>
 *   <li>A <b>creation</b> timestamp counts 1/2²² seconds from 2008-12-01 00:00 Europe/Berlin. A
 *       database converted from the previous format may hold one at 1/2¹⁰ seconds instead; such a
 *       value is below 2⁴⁰.
 *   <li>A <b>last changed</b> timestamp counts 100 nanoseconds from 1582-10-15 00:00 UTC, and is 0
 *       for a record never changed.
 * </ul>
 */
public final class Timestamps {
  private Timestamps() {}

  private static final Instant CREATION_EPOCH =
      LocalDateTime.of(2008, 12, 1, 0, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant();
  private static final Instant LAST_CHANGED_EPOCH =
      LocalDateTime.of(1582, 10, 15, 0, 0).toInstant(ZoneOffset.UTC);

  private static final long OLD_SCALE_LIMIT = 1L << 40;

  /** The moment a creation timestamp stands for. */
  public static @NotNull Instant creationInstant(long value) {
    // Values below 2^40 are at 1/2^10 seconds; scale them up to 1/2^22
    long ticks = value < OLD_SCALE_LIMIT ? value << 12 : value;
    long seconds = ticks >> 22;
    long nanos = ((ticks & ((1L << 22) - 1)) * 1_000_000_000L) >> 22;
    return CREATION_EPOCH.plusSeconds(seconds).plusNanos(nanos);
  }

  /** The creation timestamp of a moment, at 1/2²² seconds. */
  public static long creation(@NotNull Instant instant) {
    long seconds = instant.getEpochSecond() - CREATION_EPOCH.getEpochSecond();
    long fraction = ((long) instant.getNano() << 22) / 1_000_000_000L;
    return (seconds << 22) + fraction;
  }

  /** The moment a last changed timestamp stands for, or null for 0. */
  public static Instant lastChangedInstant(long value) {
    if (value == 0) {
      return null;
    }
    return LAST_CHANGED_EPOCH.plusSeconds(value / 10_000_000).plusNanos((value % 10_000_000) * 100);
  }

  /** The last changed timestamp of a moment. */
  public static long lastChanged(@NotNull Instant instant) {
    long seconds = instant.getEpochSecond() - LAST_CHANGED_EPOCH.getEpochSecond();
    return seconds * 10_000_000 + instant.getNano() / 100;
  }
}
