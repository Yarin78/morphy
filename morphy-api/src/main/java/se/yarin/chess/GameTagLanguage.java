package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A language a game tag can have a title in. Each format offers some of them: ChessBase v1 all but
 * Portuguese, v2 all but Slovenian.
 */
public enum GameTagLanguage {
  ENGLISH("English", "ENG"),
  GERMAN("German", "GER"),
  FRENCH("French", "FRA"),
  SPANISH("Spanish", "ESP"),
  ITALIAN("Italian", "ITA"),
  DUTCH("Dutch", "NED"),
  SLOVENIAN("Slovenian", "SLO"),
  PORTUGUESE("Portuguese", "POR");

  private final @NotNull String displayName;
  private final @NotNull String nation;

  GameTagLanguage(@NotNull String displayName, @NotNull String nation) {
    this.displayName = displayName;
    this.nation = nation;
  }

  public @NotNull String displayName() {
    return displayName;
  }

  /** The IOC code of the nation that stands for the language, as ChessBase has it. */
  public @NotNull String nation() {
    return nation;
  }

  /** The name of the {@link GameHeaderModel} field with a game tag's title in this language. */
  public @NotNull String headerField() {
    return "gameTag" + displayName;
  }

  /** The language whose title a header field holds; null if it's not such a field. */
  public static @Nullable GameTagLanguage ofHeaderField(@NotNull String name) {
    for (GameTagLanguage language : values()) {
      if (language.headerField().equals(name)) {
        return language;
      }
    }
    return null;
  }
}
