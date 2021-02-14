package se.yarin.cbhlib.entities;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TournamentEntity implements Entity, Comparable<TournamentEntity> {
    private static final Logger log = LoggerFactory.getLogger(TournamentEntity.class);

    @Getter
    private int id;

    @Getter private byte[] raw;  // For debugging purposes

    @Getter
    @NonNull
    private String title;

    @Getter
    @NonNull
    @Builder.Default
    private Date date;

    @Getter
    private int category;

    @Getter
    private int rounds;

    @Getter
    @NonNull
    @Builder.Default
    private TournamentType type = TournamentType.NONE;

    @Getter
    private boolean legacyComplete;

    @Getter
    private boolean complete;

    @Getter
    private boolean threePointsWin;

    @Getter
    private boolean teamTournament;

    @Getter
    private boolean boardPoints;

    @Getter
    @NonNull
    @Builder.Default
    private TournamentTimeControl timeControl = TournamentTimeControl.NORMAL;

    @Getter
    @NonNull
    @Builder.Default
    private String place = "";

    @Getter
    @NonNull
    @Builder.Default
    private Nation nation = Nation.NONE;

    // Missing here is latitude, longitude, end date
    // Missing is also tiebreak rules
    // Maybe stored in another database?

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public TournamentEntity(@NonNull String title, @NonNull Date date) {
        // Tournaments are keyed (and sorted) by year + title
        this(title, "", date);
    }

    public TournamentEntity(@NonNull String title, @NonNull String place, @NonNull Date date) {
        // Tournaments are keyed (and sorted) by year + title + place + month + day
        this.title = title;
        this.date = date;
        this.type = TournamentType.NONE;
        this.timeControl = TournamentTimeControl.NORMAL;
        this.place = place;
        this.nation = Nation.NONE;
    }

    @Override
    public String toString() {
        return this.date.year() + ": " + this.title;
    }

    /**
     * Gets a string describing the type of the tournament,
     * combining the type, time control and team status
     * @return
     */
    public String getPrettyTypeName() {
        String typeName = type.getLongName();
        if (timeControl != TournamentTimeControl.NORMAL) {
            typeName += String.format(" (%s)", timeControl.getLongName());
        }
        if (teamTournament) {
            if (typeName.length() == 0) {
                typeName = "Team";
            } else {
                typeName = "Team-" + typeName;
            }
        }
        return typeName;
    }

    @Override
    public int compareTo(TournamentEntity o) {
        if (this.date.year() != o.date.year()) {
            return o.date.year() - this.date.year();
        }
        int dif = CBUtil.compareString(title, o.title);

        if (dif != 0) return dif;
        dif = CBUtil.compareString(place, o.place);
        if (dif != 0) return dif;

        if (this.date.month() != o.date.month()) {
            return o.date.month() - this.date.month();
        }
        return o.date.day() - this.date.day();
    }

    @Override
    public TournamentEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public TournamentEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TournamentEntity that = (TournamentEntity) o;

        return title.equals(that.title) && this.date.year() == that.date.year();
    }

    @Override
    public int hashCode() {
        return title.hashCode() + this.date.year();
    }

    public String getCategoryRoman() {
        String[] roman = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        int cat = getCategory();
        if (cat < 0) {
            return "";
        }
        if (cat > 39) {
            log.warn("Unexpected tournament category: " + cat);
            return Integer.toString(cat);
        }
        return "X".repeat(cat / 10) + roman[cat % 10];
    }
}
