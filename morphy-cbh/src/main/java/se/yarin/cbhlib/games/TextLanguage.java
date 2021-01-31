package se.yarin.cbhlib.games;

import lombok.Getter;

public enum TextLanguage {
    ENGLISH("English"),
    GERMAN("Deutsch"),
    FRENCH("Francais"),
    SPANISH("Espanol"),
    ITALIAN("Italiano"),
    DUTCH("Nederlands"),
    PORTUGUESE("Portuguese"),
    UNKNOWN("Unknown");  // Stored in file but not possible to set!?

    @Getter
    private final String name;

    TextLanguage(String name) {
        this.name = name;
    }
}
