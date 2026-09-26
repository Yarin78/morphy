package se.yarin.morphy.cb2.indexes;

import java.text.Normalizer;
import org.jetbrains.annotations.NotNull;

/**
 * How ChessBase compares text in its sort orders, which resembles the Windows {@code
 * CompareString} collation rather than a byte comparison. Case is ignored, and accents are ignored
 * but for breaking ties, the unaccented text first. Symbols sort before digits and digits before
 * letters; symbols follow ASCII order except that {@code + < = >} come after all other
 * punctuation; figurine characters sort after the letters. Spaces always count, while apostrophes
 * and hyphens may be ignored depending on the entity type.
 *
 * <p>This is the best understanding of the collation; a few neighbouring pairs in large databases
 * are known not to fit it.
 */
public final class Collation {

  private static final int SYMBOL = 0, DIGIT = 1, LETTER = 2, OTHER_LETTER = 3, FIGURINE = 4;

  private final boolean ignoreApostrophes;
  private final boolean ignoreHyphens;

  /** Apostrophes are ignored when comparing the names of players and annotators. */
  public static final Collation PLAYERS = new Collation(true, false);
  /** Apostrophes and hyphens are ignored when comparing tournaments. */
  public static final Collation TOURNAMENTS = new Collation(true, true);
  /** Nothing is ignored for other entity types. */
  public static final Collation DEFAULT = new Collation(false, false);

  private Collation(boolean ignoreApostrophes, boolean ignoreHyphens) {
    this.ignoreApostrophes = ignoreApostrophes;
    this.ignoreHyphens = ignoreHyphens;
  }

  /** Compares two texts: negative if the first sorts first. */
  public int compare(@NotNull String a, @NotNull String b) {
    String fa = fold(a), fb = fold(b);
    int i = 0, j = 0;
    while (i < fa.length() && j < fb.length()) {
      char ca = fa.charAt(i), cb = fb.charAt(j);
      if (ignored(ca)) {
        i++;
        continue;
      }
      if (ignored(cb)) {
        j++;
        continue;
      }
      int c = Integer.compare(weight(ca), weight(cb));
      if (c != 0) {
        return c;
      }
      i++;
      j++;
    }
    while (i < fa.length() && ignored(fa.charAt(i))) i++;
    while (j < fb.length() && ignored(fb.charAt(j))) j++;
    if (i < fa.length()) {
      return 1;
    }
    if (j < fb.length()) {
      return -1;
    }
    // Equal but for accents: the unaccented one first
    return Boolean.compare(hasAccents(a), hasAccents(b));
  }

  private boolean ignored(char c) {
    return (ignoreApostrophes && (c == '\'' || c == '’')) || (ignoreHyphens && c == '-');
  }

  /** Lower case, without accents. */
  static @NotNull String fold(@NotNull String s) {
    String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
    StringBuilder sb = new StringBuilder(decomposed.length());
    for (int k = 0; k < decomposed.length(); k++) {
      char c = decomposed.charAt(k);
      if (Character.getType(c) == Character.NON_SPACING_MARK) {
        continue;
      }
      sb.append(Character.toLowerCase(c));
    }
    return sb.toString();
  }

  private static boolean hasAccents(String s) {
    String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
    for (int k = 0; k < decomposed.length(); k++) {
      if (Character.getType(decomposed.charAt(k)) == Character.NON_SPACING_MARK) {
        return true;
      }
    }
    return false;
  }

  private static int weight(char c) {
    return (category(c) << 16) | order(c);
  }

  private static int category(char c) {
    if (c >= '' && c <= '') {
      return FIGURINE;
    }
    if (c >= '0' && c <= '9') {
      return DIGIT;
    }
    if (c >= 'a' && c <= 'z') {
      return LETTER;
    }
    if (Character.isLetter(c)) {
      return OTHER_LETTER;
    }
    return SYMBOL;
  }

  private static int order(char c) {
    // + < = > come after all other punctuation
    return switch (c) {
      case '+' -> 0xFF00;
      case '<' -> 0xFF01;
      case '=' -> 0xFF02;
      case '>' -> 0xFF03;
      default -> c;
    };
  }
}
