package se.yarin.morphy.entities;

public enum EntityType {
    PLAYER("player", "players"),
    TOURNAMENT("tournament", "tournaments"),
    ANNOTATOR("annotator", "annotators"),
    SOURCE("source", "sources"),
    TEAM("team", "teams"),
    GAME_TAG("game tag", "game tags");

    private final String nameSingular;
    private final String namePlural;

    EntityType(String nameSingular, String namePlural) {
        this.nameSingular = nameSingular;
        this.namePlural = namePlural;
    }

    public String nameSingular() {
        return nameSingular;
    }

    public String namePlural() {
        return namePlural;
    }

    public String nameSingularCapitalized() {
        return nameSingular.substring(0, 1).toUpperCase() + nameSingular.substring(1);
    }

    public String namePluralCapitalized() {
        return namePlural.substring(0, 1).toUpperCase() + namePlural.substring(1);
    }
}
