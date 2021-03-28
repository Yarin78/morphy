package se.yarin.morphy.text;

public enum TextLanguage {
    ENGLISH("English"),
    GERMAN("Deutsch"),
    FRENCH("Francais"),
    SPANISH("Espanol"),
    ITALIAN("Italiano"),
    DUTCH("Nederlands"),
    PORTUGUESE("Portuguese"),
    UNKNOWN("Unknown");  // Stored in file but not possible to set!?

    private final String name;

    public String getName() {
        return name;
    }

    TextLanguage(String name) {
        this.name = name;
    }
}
