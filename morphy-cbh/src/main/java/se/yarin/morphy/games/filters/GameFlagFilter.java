package se.yarin.morphy.games.filters;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

public class GameFlagFilter implements ItemStorageFilter<GameHeader>, GameFilter {

  public enum Flag {
    SETUP_POSITION,
    VARIATIONS,
    COMMENTARY,
    SYMBOLS,
    TRAINING,
    ANNOTATIONS,
    DELETED,
    CHESS960,
    CORRESPONDENCE,
    MEDIA
  }

  private final @NotNull Flag flag;
  private final boolean expected;

  public GameFlagFilter(@NotNull Flag flag, boolean expected) {
    this.flag = flag;
    this.expected = expected;
  }

  public @NotNull Flag flag() {
    return flag;
  }

  public boolean expected() {
    return expected;
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    boolean value =
        switch (flag) {
          case SETUP_POSITION ->
              gameHeader.flags().contains(GameHeaderFlags.SETUP_POSITION);
          case VARIATIONS -> gameHeader.variationsMagnitude() > 0;
          case COMMENTARY -> gameHeader.commentariesMagnitude() > 0;
          case SYMBOLS -> gameHeader.symbolsMagnitude() > 0;
          case TRAINING -> gameHeader.trainingMagnitude() > 0;
          case ANNOTATIONS ->
              gameHeader.graphicalArrowsMagnitude() > 0
                  || gameHeader.graphicalSquaresMagnitude() > 0;
          case DELETED -> gameHeader.deleted();
          case CHESS960 -> gameHeader.flags().contains(GameHeaderFlags.UNORTHODOX);
          case CORRESPONDENCE ->
              gameHeader.flags().contains(GameHeaderFlags.CORRESPONDENCE_HEADER);
          case MEDIA ->
              gameHeader.flags().contains(GameHeaderFlags.EMBEDDED_AUDIO)
                  || gameHeader.flags().contains(GameHeaderFlags.EMBEDDED_PICTURE)
                  || gameHeader.flags().contains(GameHeaderFlags.EMBEDDED_VIDEO)
                  || gameHeader.flags().contains(GameHeaderFlags.ANNO_TYPE_1A);
        };
    return value == expected;
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    if (flag == Flag.DELETED) {
      boolean deleted = (ByteBufferUtil.getUnsignedByte(buf, 0) & 128) != 0;
      return deleted == expected;
    }

    int flagInt = ByteBufferUtil.getIntB(buf, 39);
    boolean value =
        switch (flag) {
          case SETUP_POSITION -> (flagInt & GameHeaderFlags.SETUP_POSITION.getValue()) != 0;
          case VARIATIONS -> (flagInt & GameHeaderFlags.VARIATIONS.getValue()) != 0;
          case COMMENTARY -> (flagInt & GameHeaderFlags.COMMENTARY.getValue()) != 0;
          case SYMBOLS -> (flagInt & GameHeaderFlags.SYMBOLS.getValue()) != 0;
          case TRAINING -> (flagInt & GameHeaderFlags.TRAINING.getValue()) != 0;
          case ANNOTATIONS ->
              (flagInt
                      & (GameHeaderFlags.GRAPHICAL_ARROWS.getValue()
                          | GameHeaderFlags.GRAPHICAL_SQUARES.getValue()))
                  != 0;
          case CHESS960 -> (flagInt & GameHeaderFlags.UNORTHODOX.getValue()) != 0;
          case CORRESPONDENCE ->
              (flagInt & GameHeaderFlags.CORRESPONDENCE_HEADER.getValue()) != 0;
          case MEDIA ->
              (flagInt
                      & (GameHeaderFlags.EMBEDDED_AUDIO.getValue()
                          | GameHeaderFlags.EMBEDDED_PICTURE.getValue()
                          | GameHeaderFlags.EMBEDDED_VIDEO.getValue()
                          | GameHeaderFlags.ANNO_TYPE_1A.getValue()))
                  != 0;
          case DELETED -> throw new IllegalStateException("Handled above");
        };
    return value == expected;
  }

  @Override
  public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return this;
  }

  @Override
  public String toString() {
    return flag.name().toLowerCase() + ":" + expected;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GameFlagFilter that = (GameFlagFilter) o;
    return flag == that.flag && expected == that.expected;
  }

  @Override
  public int hashCode() {
    return Objects.hash(flag, expected);
  }
}
