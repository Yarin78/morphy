package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameTagLanguage;
import se.yarin.morphy.chessbase.Nation;

/**
 * A game tag, which is also the title of a guiding text or an analysis: one title per language.
 * What it is used as depends only on what refers to it.
 *
 * @param titles the titles, in ascending order of language
 */
public record GameTag(@NotNull List<Title> titles) implements Entity {

  /** The languages ChessBase offers, as nation codes: English, Spanish, French, German, ... */
  public static final int[] OFFERED_LANGUAGES = {42, 43, 49, 53, 70, 103, 117};

  /** The code of English, the default language. */
  public static final int ENGLISH = 42;

  /**
   * A title in one language.
   *
   * @param language the language, as a nation code
   * @param text the title, empty if there is none in this language
   */
  public record Title(int language, @NotNull String text) {}

  public GameTag {
    titles = List.copyOf(titles);
  }

  /**
   * A game tag with a title in one language, and the empty entries ChessBase writes for the other
   * languages it offers.
   */
  public static @NotNull GameTag of(int language, @NotNull String text) {
    List<Title> titles = new ArrayList<>();
    boolean added = false;
    for (int offered : OFFERED_LANGUAGES) {
      if (!added && language < offered) {
        titles.add(new Title(language, text));
        added = true;
      }
      titles.add(new Title(offered, offered == language ? text : ""));
      added |= offered == language;
    }
    if (!added) {
      titles.add(new Title(language, text));
    }
    return new GameTag(titles);
  }

  /**
   * A game tag with titles in several languages, and the empty entries ChessBase writes for the
   * other languages it offers.
   */
  public static @NotNull GameTag of(@NotNull Map<GameTagLanguage, String> titles) {
    Map<Integer, String> byCode = new TreeMap<>();
    for (int offered : OFFERED_LANGUAGES) {
      byCode.put(offered, "");
    }
    titles.forEach((language, text) -> byCode.put(code(language), text));
    List<Title> list = new ArrayList<>();
    byCode.forEach((language, text) -> list.add(new Title(language, text)));
    return new GameTag(list);
  }

  /** The code of a language: the code of the nation that stands for it. */
  public static int code(@NotNull GameTagLanguage language) {
    return Nation.fromIOC(language.nation()).ordinal();
  }

  /** The language with a code; null if it's none of GameTagLanguage. */
  public static @Nullable GameTagLanguage language(int code) {
    for (GameTagLanguage language : GameTagLanguage.values()) {
      if (code(language) == code) {
        return language;
      }
    }
    return null;
  }

  /** A game tag with an English title. */
  public static @NotNull GameTag of(@NotNull String text) {
    return of(ENGLISH, text);
  }

  /** The placeholder game tag, with no titles at all. */
  public static @NotNull GameTag empty() {
    return new GameTag(List.of());
  }

  /** The first title that isn't empty, preferring English; empty if there is none. */
  public @NotNull String title() {
    for (Title t : titles) {
      if (t.language() == ENGLISH && !t.text().isEmpty()) {
        return t.text();
      }
    }
    for (Title t : titles) {
      if (!t.text().isEmpty()) {
        return t.text();
      }
    }
    return "";
  }

  /** The first title stored, whether empty or not, as used by the sort order. */
  public @NotNull Title firstTitle() {
    return titles.isEmpty() ? new Title(0, "") : titles.getFirst();
  }

  @Override
  public @NotNull EntityType type() {
    return EntityType.GAME_TAG;
  }

  @Override
  public boolean isEmpty() {
    return title().isEmpty();
  }

  static @NotNull GameTag read(@NotNull ByteBuffer buf) {
    int count = buf.getInt();
    List<Title> titles = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int language = buf.getInt();
      titles.add(new Title(language, Entity.getString(buf)));
    }
    return new GameTag(titles);
  }

  @Override
  public byte @NotNull [] encode() {
    int size = 4;
    for (Title t : titles) {
      size += 4 + Entity.stringSize(t.text());
    }
    ByteBuffer buf = Entity.recordBuffer(size);
    buf.putInt(titles.size());
    for (Title t : titles) {
      buf.putInt(t.language());
      Entity.putString(buf, t.text());
    }
    return Entity.finish(buf);
  }
}
