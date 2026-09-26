package se.yarin.morphy.chessbase.text;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A model of a Text entry in a ChessBase database TODO: Embedded media (in the .html folder) is not
 * handled
 */
public class TextContentsModel {
  private static final TextLanguage DEFAULT_LANGUAGE = TextLanguage.ENGLISH;

  private final int format;
  private final int unknown;
  @NotNull private final Map<TextLanguage, String> titles;
  // Maybe this should be ByteBuffer instead, and allow the rendered to interpret the encoding
  @NotNull private final Map<TextLanguage, String> contents;
  @NotNull private final Map<TextLanguage, byte[]> formatting;

  public TextContentsModel() {
    this.titles = new HashMap<>();
    this.contents = new HashMap<>();
    this.format = 3;
    this.formatting = new HashMap<>();
    this.unknown = 1;
  }

  public TextContentsModel(
      int format,
      @NotNull Map<TextLanguage, String> titles,
      @NotNull Map<TextLanguage, String> contents,
      @NotNull Map<TextLanguage, byte[]> formatting,
      int unknown) {
    this.format = format; // 1 (old) or 3 (new)
    this.titles = titles;
    this.contents = contents;
    this.formatting = formatting; // Only if format = 1
    this.unknown = unknown;
  }

  /** The v1 text format: 1 (old), 2 or 3 (new). */
  public int format() {
    return format;
  }

  /** A v1 byte of unknown meaning, kept so a text can be written back unchanged. */
  public int unknown() {
    return unknown;
  }

  public @NotNull Map<TextLanguage, String> titles() {
    return titles;
  }

  public @NotNull Map<TextLanguage, String> contents() {
    return contents;
  }

  /** v1 formatting data per language, only present in format 1 and 2. */
  public @NotNull Map<TextLanguage, byte[]> formatting() {
    return formatting;
  }

  public @NotNull Set<TextLanguage> titleLanguages() {
    return this.titles.keySet();
  }

  public @NotNull Set<TextLanguage> contentLanguages() {
    return this.contents.keySet();
  }

  public String getTitle() {
    return getTitle(DEFAULT_LANGUAGE);
  }

  public String getTitle(@NotNull TextLanguage language) {
    String title = titles.get(language);
    if (title == null) {
      if (titles.size() == 0) {
        title = "Untitled";
      } else {
        title = titles.values().stream().findFirst().get();
      }
    }
    return title;
  }

  public void setTitle(@NotNull String title) {
    setTitle(DEFAULT_LANGUAGE, title);
  }

  public void setTitle(@NotNull TextLanguage language, @NotNull String title) {
    this.titles.put(language, title);
  }

  public String getContents() {
    return getContents(DEFAULT_LANGUAGE);
  }

  public String getContents(@NotNull TextLanguage language) {
    String html = this.contents.get(language);
    if (html == null) {
      if (this.contents.size() == 0) {
        html = "Untitled";
      } else {
        html = this.contents.values().stream().findFirst().get();
      }
    }
    return html;
  }

  public void setContents(@NotNull String contents) {
    setContents(DEFAULT_LANGUAGE, contents);
  }

  public void setContents(@NotNull TextLanguage language, @NotNull String html) {
    this.contents.put(language, html);
  }

}
