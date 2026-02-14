package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Eco;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

/**
 * Filters games by ECO code.
 *
 * <p>Supports exact matching (e.g., "B90") and wildcard matching (e.g., "B9*" matches B90-B99).
 */
public class EcoFilter extends IsGameFilter {
  private final @Nullable Eco exactEco;
  private final int minEco;
  private final int maxEco;
  private final boolean isWildcard;

  /**
   * Creates an ECO filter from a pattern string.
   *
   * @param ecoPattern ECO pattern like "B90" (exact), "B9*" (wildcard for B90-B99), or "B*"
   *     (wildcard for B00-B99)
   * @throws IllegalArgumentException if the pattern is invalid
   */
  public EcoFilter(@NotNull String ecoPattern) {
    if (ecoPattern.endsWith("*")) {
      this.isWildcard = true;
      this.exactEco = null;
      String prefix = ecoPattern.substring(0, ecoPattern.length() - 1);

      if (prefix.length() == 0) {
        // "*" matches all ECO codes A00-E99
        this.minEco = 0;
        this.maxEco = 499;
      } else if (prefix.length() == 1) {
        // "B*" matches B00-B99
        char letter = prefix.charAt(0);
        if (letter < 'A' || letter > 'E') {
          throw new IllegalArgumentException("Invalid ECO letter: " + letter);
        }
        int base = (letter - 'A') * 100;
        this.minEco = base;
        this.maxEco = base + 99;
      } else if (prefix.length() == 2) {
        // "B9*" matches B90-B99
        Eco minEcoParsed = new Eco(prefix + "0");
        this.minEco = minEcoParsed.getInt();
        this.maxEco = this.minEco + 9;
      } else {
        throw new IllegalArgumentException("Invalid ECO wildcard pattern: " + ecoPattern);
      }
    } else {
      this.isWildcard = false;
      this.exactEco = new Eco(ecoPattern);
      this.minEco = this.exactEco.getInt();
      this.maxEco = this.minEco;
    }
  }

  /**
   * Creates an ECO filter for an exact ECO code.
   *
   * @param eco the exact ECO code to match
   */
  public EcoFilter(@NotNull Eco eco) {
    this.isWildcard = false;
    this.exactEco = eco;
    this.minEco = eco.getInt();
    this.maxEco = this.minEco;
  }

  /**
   * Creates an ECO filter for a range of ECO codes.
   *
   * @param minEco minimum ECO code (inclusive)
   * @param maxEco maximum ECO code (inclusive)
   */
  public EcoFilter(@NotNull Eco minEco, @NotNull Eco maxEco) {
    this.isWildcard = true;
    this.exactEco = null;
    this.minEco = minEco.getInt();
    this.maxEco = maxEco.getInt();
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader header) {
    if (!super.matches(id, header)) {
      return false;
    }
    Eco eco = header.eco();
    if (!eco.isSet()) {
      return false;
    }
    int ecoInt = eco.getInt();
    return ecoInt >= minEco && ecoInt <= maxEco;
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    if (!super.matchesSerialized(id, buf)) {
      return false;
    }
    int ecoInt = ByteBufferUtil.getUnsignedShortB(buf, 35);
    if (ecoInt == 0xFFFF) {
      // Unset ECO code
      return false;
    }
    return ecoInt >= minEco && ecoInt <= maxEco;
  }

  @Override
  public String toString() {
    if (exactEco != null) {
      return "eco = '" + exactEco + "'";
    } else if (minEco == 0 && maxEco == 499) {
      return "eco = *";
    } else {
      return "eco in [" + Eco.fromInt(minEco) + ", " + Eco.fromInt(maxEco) + "]";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EcoFilter ecoFilter = (EcoFilter) o;
    return minEco == ecoFilter.minEco
        && maxEco == ecoFilter.maxEco
        && isWildcard == ecoFilter.isWildcard
        && java.util.Objects.equals(exactEco, ecoFilter.exactEco);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(exactEco, minEco, maxEco, isWildcard);
  }
}
