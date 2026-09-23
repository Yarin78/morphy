package se.yarin.chess;

import org.jetbrains.annotations.NotNull;

/** Represents an Eco classification */
public class Eco {

  private int eco;
  private int subEco;

  private Eco(int eco, int subEco) {
    this.eco = eco;
    this.subEco = subEco;
  }

  /**
   * Creates an instance of an Eco given a string
   *
   * @param eco the Eco code, A00-E99
   * @throws IllegalArgumentException if the Eco code is invalid
   */
  public Eco(@NotNull String eco) {
    if (eco.length() == 6 && eco.charAt(3) == '/') {
      int q1 = eco.charAt(4) - '0';
      int q2 = eco.charAt(5) - '0';
      if (q1 < 0 || q2 < 0 || q1 > 9 || q2 > 9) {
        throw new IllegalArgumentException("Invalid SubEco: " + eco);
      }
      this.subEco = q1 * 10 + q2;
      eco = eco.substring(0, 3);
    }
    if (eco.length() != 3) {
      throw new IllegalArgumentException("Invalid Eco: " + eco);
    }
    int p1 = eco.charAt(0) - 'A';
    int p2 = eco.charAt(1) - '0';
    int p3 = eco.charAt(2) - '0';
    if (p1 < 0 || p2 < 0 || p3 < 0 || p1 > 4 || p2 > 9 || p3 > 9) {
      throw new IllegalArgumentException("Invalid Eco: " + eco);
    }
    this.eco = p1 * 100 + p2 * 10 + p3;
  }

  /**
   * Creates an Eco from an integer between 0 and 499. 0 = A00, 1 = A01, 100 = B00 etc
   *
   * @param eco the Eco integer
   * @return an instance of a {@link Eco}
   */
  public static Eco fromInt(int eco) {
    return fromInt(eco, 0);
  }

  /**
   * Creates an Eco from an integer between 0 and 499. 0 = A00, 1 = A01, 100 = B00 etc
   *
   * @param eco the Eco integer
   * @return an instance of a {@link Eco}
   */
  public static Eco fromInt(int eco, int subEco) {
    if (eco < 0 || eco >= 500) {
      throw new IllegalArgumentException("Invalid Eco number: " + eco);
    }
    if (subEco < 0 || subEco >= 100) {
      throw new IllegalArgumentException("Invalid sub Eco number: " + subEco);
    }
    return new Eco(eco, subEco);
  }

  public static Eco unset() {
    return new Eco(-1, 0);
  }

  /**
   * @return true if the Eco is set to some value
   */
  public boolean isSet() {
    return eco >= 0;
  }

  /**
   * Gets an integer between 0 and 499 representing the Eco codes between A00 and E99, respectively.
   *
   * @return the Eco code as an integer, or -1 if not set
   */
  public int getInt() {
    return eco;
  }

  /**
   * Gets an integer between 0 and 99 representing the sub Eco codes between 0 and 99, inclusive.
   *
   * @return the sub Eco code as an integer
   */
  public int getSubEco() {
    return subEco;
  }

  @Override
  public String toString() {
    if (eco < 0) {
      return "???";
    } else {
      String ecoText = String.format("%c%02d", (char) ('A' + eco / 100), eco % 100);
      if (subEco > 0) {
        ecoText += String.format("/%02d", subEco);
      }
      return ecoText;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Eco eco1 = (Eco) o;

    return eco == eco1.eco && subEco == eco1.subEco;
  }

  @Override
  public int hashCode() {
    return eco * 100 + subEco;
  }
}
