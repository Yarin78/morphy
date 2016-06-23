package se.yarin.chess;

import lombok.NonNull;

/**
 * Represents an Eco classification
 */
public class Eco {
    // TODO: Support sub eco and update {@link se.yarin.cbhlib.CBUtil#decodeEco}
    // TODO: Reconsider if unset should be supported
    private int eco;

    private Eco(int eco) {
        this.eco = eco;
    }

    /**
     * Creates an instance of an Eco given a string
     * @param eco
     * @throws IllegalArgumentException if the Eco code is invalid
     */
    public Eco(@NonNull String eco) {
        if (eco.length() != 3) {
            throw new IllegalArgumentException("Invalid Eco: " + eco);
        }
        int p1 = eco.charAt(0) - 'A';
        int p2 = eco.charAt(1) - '0';
        int p3 = eco.charAt(2) - '0';
        if (p1 < 0 || p2 < 0 || p3 < 0 || p1 > 4 || p2 > 9 || p3 > 9) {
            throw new IllegalArgumentException("Invalid Eco: " + eco);
        }
        this.eco = p1*100+p2*10+p3;
    }

    /**
     * Creates an Eco from an integer between 0 and 499.
     * 0 = A00, 1 = A01, 100 = B00 etc
     * @param eco the Eco integer
     * @return an instance of a {@link Eco}
     */
    public static Eco fromInt(int eco) {
        if (eco < 0 || eco >= 500) {
            throw new IllegalArgumentException("Invalid Eco number");
        }
        return new Eco(eco);
    }

    public static Eco unset() {
        return new Eco(-1);
    }

    /**
     * @return true if the Eco is set to some value
     */
    public boolean isSet() {
        return eco >= 0;
    }

    /**
     * Gets an integer between 0 and 499 representing the Eco codes between A00 and E99, respectively.
     * @return the Eco code as an integer, or -1 if not set
     */
    public int getInt() {
        return eco;
    }

    @Override
    public String toString() {
        if (eco < 0) {
            return "???";
        } else {
            return String.format("%c%02d", (char)('A' + eco / 100), eco % 100);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Eco eco1 = (Eco) o;

        return eco == eco1.eco;

    }

    @Override
    public int hashCode() {
        return eco;
    }
}
