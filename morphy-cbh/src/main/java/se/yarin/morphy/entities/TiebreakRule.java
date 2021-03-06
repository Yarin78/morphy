package se.yarin.morphy.entities;

public enum TiebreakRule {
    UNSPECIFIED(1, "Unspecified"),
    NOT_SET(2, "Not set"),
    SWISS_RATING_BUCHHOLZ(10, "Rating of Buchholz"),
    SWISS_FEINE_BUCHHOLZ(11, "Feine Buchholz"),
    SWISS_MEDIAN_BUCHHOLZ(12, "Median Buchholz"),
    SWISS_FORTSHRITT(13, "Fortshritt"),
    SWISS_SONNENBORNBERGER(14, "Sonnenbornberger"),
    SWISS_MEDIAN2_BUCHHOLZ(21, "Median2 Buchholz"),
    SWISS_CUT1_BUCHHOLZ(22, "Cut 1 Buchholz"),
    SWISS_CUT2_BUCHHOLZ(23, "Cut 2 Buchholz"),
    RR_SONNEBORNBERGER(200, "Sonnebornberger"),
    RR_NUM_WINS(201, "# wins"),
    RR_NUM_BLACK_WINS(202, "# black wins"),
    RR_NUM_BLACK_GAMES(203, "# black games"),
    RR_POINT_GROUP(204, "Point group"),
    RR_KOYA(206, "Koya");

    private final int id;

    private final String name;

    public int id() {
        return id;
    }

    public String tiebreakName() {
        return name;
    }

    public static TiebreakRule fromId(int id) {
        // TODO: Hashmap
        for (TiebreakRule rule : TiebreakRule.values()) {
            if (rule.id() == id) {
                return rule;
            }
        }
        return TiebreakRule.UNSPECIFIED;
    }

    TiebreakRule(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
