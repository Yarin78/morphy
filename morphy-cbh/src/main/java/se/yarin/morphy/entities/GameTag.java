package se.yarin.morphy.entities;

import org.immutables.value.Value;

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

  /** Returns a space-separated list of ISO 639-2 three-letter codes for languages that have a title set. */
  public String languages() {
    var sb = new StringBuilder();
    if (!englishTitle().isEmpty()) sb.append("ENG");
    if (!germanTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("GER"); }
    if (!frenchTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("FRE"); }
    if (!spanishTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("SPA"); }
    if (!italianTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("ITA"); }
    if (!dutchTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("DUT"); }
    if (!slovenianTitle().isEmpty()) { if (!sb.isEmpty()) sb.append(' '); sb.append("SLV"); }
    return sb.toString();
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
