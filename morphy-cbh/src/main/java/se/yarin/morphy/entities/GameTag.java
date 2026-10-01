package se.yarin.morphy.entities;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import se.yarin.chess.GameTagLanguage;
import se.yarin.morphy.chessbase.Nation;

@Value.Immutable
public abstract class GameTag extends Entity implements Comparable<GameTag> {
  @Value.Default
  public String englishTitle() {
    return "";
  }
  ;

  @Value.Default
  public String germanTitle() {
    return "";
  }
  ;

  @Value.Default
  public String frenchTitle() {
    return "";
  }
  ;

  @Value.Default
  public String spanishTitle() {
    return "";
  }
  ;

  @Value.Default
  public String italianTitle() {
    return "";
  }
  ;

  @Value.Default
  public String dutchTitle() {
    return "";
  }
  ;

  @Value.Default
  public String slovenianTitle() {
    return "";
  }
  ;

  @Value.Default
  public String resTitle() {
    return "";
  }
  ;

  /** The title in a language; empty if it has none, as always in Portuguese, which v1 lacks. */
  public String title(GameTagLanguage language) {
    return switch (language) {
      case ENGLISH -> englishTitle();
      case GERMAN -> germanTitle();
      case FRENCH -> frenchTitle();
      case SPANISH -> spanishTitle();
      case ITALIAN -> italianTitle();
      case DUTCH -> dutchTitle();
      case SLOVENIAN -> slovenianTitle();
      case PORTUGUESE -> "";
    };
  }

  /**
   * The languages that have a title, as the nation codes that stand for them, in the order v2 keeps
   * them: "ENG ESP GER".
   */
  public String languages() {
    return Arrays.stream(GameTagLanguage.values())
        .filter(language -> !title(language).isEmpty())
        .sorted(Comparator.comparingInt(language -> Nation.fromIOC(language.nation()).ordinal()))
        .map(GameTagLanguage::nation)
        .collect(Collectors.joining(" "));
  }

  /** Returns the number of languages that have a title set. */
  public int languageCount() {
    int count = 0;
    if (!englishTitle().isEmpty()) count++;
    if (!germanTitle().isEmpty()) count++;
    if (!frenchTitle().isEmpty()) count++;
    if (!spanishTitle().isEmpty()) count++;
    if (!italianTitle().isEmpty()) count++;
    if (!dutchTitle().isEmpty()) count++;
    if (!slovenianTitle().isEmpty()) count++;
    return count;
  }

  /** Returns the first non-empty language-specific title, or empty string if all are empty. */
  public String title() {
    if (!englishTitle().isEmpty()) return englishTitle();
    if (!germanTitle().isEmpty()) return germanTitle();
    if (!frenchTitle().isEmpty()) return frenchTitle();
    if (!spanishTitle().isEmpty()) return spanishTitle();
    if (!italianTitle().isEmpty()) return italianTitle();
    if (!dutchTitle().isEmpty()) return dutchTitle();
    if (!slovenianTitle().isEmpty()) return slovenianTitle();
    if (!resTitle().isEmpty()) return resTitle();
    return "";
  }

  @Override
  public Entity withCountAndFirstGameId(int count, int firstGameId) {
    return ImmutableGameTag.builder().from(this).count(count).firstGameId(firstGameId).build();
  }

  public static GameTag of(String englishTitle) {
    return ImmutableGameTag.builder().englishTitle(englishTitle).build();
  }

  @Override
  public int compareTo(GameTag o) {
    int comp = englishTitle().compareTo(o.englishTitle());
    if (comp != 0) return comp;
    comp = germanTitle().compareTo(o.germanTitle());
    if (comp != 0) return comp;
    comp = frenchTitle().compareTo(o.frenchTitle());
    if (comp != 0) return comp;
    comp = spanishTitle().compareTo(o.spanishTitle());
    if (comp != 0) return comp;
    comp = italianTitle().compareTo(o.italianTitle());
    if (comp != 0) return comp;
    comp = dutchTitle().compareTo(o.dutchTitle());
    if (comp != 0) return comp;
    comp = slovenianTitle().compareTo(o.slovenianTitle());
    if (comp != 0) return comp;
    return resTitle().compareTo(o.resTitle());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GameTag that = (GameTag) o;

    return englishTitle().equals(that.englishTitle())
        && germanTitle().equals(that.germanTitle())
        && frenchTitle().equals(that.frenchTitle())
        && spanishTitle().equals(that.spanishTitle())
        && italianTitle().equals(that.italianTitle())
        && dutchTitle().equals(that.dutchTitle())
        && slovenianTitle().equals(that.slovenianTitle())
        && resTitle().equals(that.resTitle());
  }

  @Override
  public int hashCode() {
    return englishTitle().hashCode()
        ^ germanTitle().hashCode()
        ^ frenchTitle().hashCode()
        ^ spanishTitle().hashCode()
        ^ italianTitle().hashCode()
        ^ dutchTitle().hashCode()
        ^ slovenianTitle().hashCode()
        ^ resTitle().hashCode();
  }
}
