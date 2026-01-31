package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Value.Immutable
public abstract class ExtendedGameHeader {

  /** Base date for creation timestamps: December 1st 2008 in Europe/Berlin timezone */
  private static final Instant CREATION_BASE_DATE =
      ZonedDateTime.of(2008, 12, 1, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant();

  /** Base date for last-changed timestamps: October 15th 1582 in UTC */
  private static final Instant LAST_CHANGED_BASE_DATE =
      ZonedDateTime.of(1582, 10, 15, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant();

  @Value.Default
  public int whiteTeamId() {
    return -1;
  }

  @Value.Default
  public int blackTeamId() {
    return -1;
  }

  @Value.Default
  public int mediaOffset() {
    // Offset into the .cbm file. Only used by guiding texts
    return -1;
  }

  @Value.Default
  public long annotationOffset() {
    // Should be same as GameHeader#annotationOffset. If different, the GH one will be used.
    return 0;
  }

  @Value.Default
  public boolean finalMaterial() {
    // If true, the FinalMaterial values below are set and non-empty
    return false;
  }

  @Value.Default
  public FinalMaterial materialPlayer1() {
    // Not necessarily the white player!
    return FinalMaterial.decode(0);
  }

  @Value.Default
  public FinalMaterial materialPlayer2() {
    // Sorted by encoding order: pawns, queens and so on
    return FinalMaterial.decode(0);
  }

  @Value.Default
  public FinalMaterial materialTotal() {
    return FinalMaterial.decode(0);
  }

  @Value.Default
  public long movesOffset() {
    // Same as GameHeader#movesOffset. If different, the GH one will be used.
    return 0;
  }

  @Value.Default
  public @NotNull RatingType whiteRatingType() {
    return RatingType.unspecified();
  }

  @Value.Default
  public @NotNull RatingType blackRatingType() {
    return RatingType.unspecified();
  }

  @Value.Default
  public int unknown1() {
    // Quite often set, no idea for what. Probably something encoded.
    return 0;
  }

  @Value.Default
  public int unknown2() {
    // Quite often set, no idea for what. Probably something encoded.
    return 0;
  }

  @Value.Default
  public int gameVersion() {
    return 0;
  }

  @Value.Default
  public long creationTimestamp() {
    // 1/1024th seconds since December 1st 2008
    return 0;
  }

  @Value.Default
  public EndgameInfo endgameInfo() {
    return EndgameInfo.empty();
  }

  @Value.Default
  public long lastChangedTimestamp() {
    // 1/10,000,000th seconds since October 15th 1582
    return 0;
  }

  @Value.Default
  public int gameTagId() {
    return -1;
  }

  public static ImmutableExtendedGameHeader empty(long annotationOffset, long movesOffset) {
    return ImmutableExtendedGameHeader.builder()
        .annotationOffset(annotationOffset)
        .movesOffset(movesOffset)
        .build();
  }

  public static ImmutableExtendedGameHeader empty(GameHeader header) {
    return empty(header.annotationOffset(), header.movesOffset());
  }

  /**
   * Gets the date and time when this game was first created.
   *
   * @return an Instant representing when the game was created
   */
  public @NotNull Instant creationTime() {
      return CREATION_BASE_DATE.plusSeconds(creationTimestamp() / 1024);
  }

  /**
   * Gets the date and time when this game was last changed. If the game has never changed since it
   * was created, the value is undefined.
   *
   * @return an Instant representing when the game was last changed
   */
  public @NotNull Instant lastChangedTime() {
      return LAST_CHANGED_BASE_DATE.plusSeconds(lastChangedTimestamp() / 10000000L);
  }

  /**
   * Calculates the current time as a creation timestamp.
   * Creation timestamp is 1/1024th seconds since December 1st 2008 in Europe/Berlin timezone.
   *
   * @return the current time as a creation timestamp
   */
  public static long currentCreationTimestamp() {
    long secondsSinceBase = Instant.now().getEpochSecond() - CREATION_BASE_DATE.getEpochSecond();
    return secondsSinceBase * 1024;
  }

  /**
   * Calculates the current time as a last-changed timestamp.
   * Last changed timestamp is 1/10,000,000th seconds since October 15th 1582 in UTC.
   *
   * @return the current time as a last-changed timestamp
   */
  public static long currentLastChangedTimestamp() {
    long secondsSinceBase = Instant.now().getEpochSecond() - LAST_CHANGED_BASE_DATE.getEpochSecond();
    return secondsSinceBase * 10000000L;
  }
}
