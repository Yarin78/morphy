package se.yarin.morphy.entities;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;

@Value.Immutable
public abstract class Tournament extends Entity implements Comparable<Tournament> {
    private static final Logger log = LoggerFactory.getLogger(Tournament.class);

    public abstract String title();

    public abstract Date date();

    @Value.Default
    public int category() { return 0; }

    @Value.Default
    public int rounds() { return 0; }

    @Value.Default
    public TournamentType type() {
        return TournamentType.NONE;
    }

    @Value.Default
    public boolean legacyComplete() { return false; }

    @Value.Default
    public boolean complete() { return false; }

    @Value.Default
    public boolean threePointsWin() { return false; }

    @Value.Default
    public boolean teamTournament() { return false; }

    @Value.Default
    public boolean boardPoints() { return false; }

    @Value.Default
    public TournamentTimeControl timeControl() {
        return TournamentTimeControl.NORMAL;
    }

    @Value.Default
    public String place() {
        return "";
    }

    @Value.Default
    public Nation nation() {
        return Nation.NONE;
    }

    @Value.Default
    public TournamentExtra extra() {
        return TournamentExtra.empty();
    }

    public static Tournament of(String title, Date date) {
        return ImmutableTournament.builder().title(title).date(date).build();
    }

    public static Tournament of(String title, String place, Date date) {
        return ImmutableTournament.builder().title(title).place(place).date(date).build();
    }

    @Override
    public String toString() {
        return this.date().year() + ": " + this.title();
    }

    /**
     * Gets a string describing the type of the tournament,
     * combining the type, time control and team status
     * @return
     */
    public String getPrettyTypeName() {
        String typeName = type().getLongName();
        if (timeControl() != TournamentTimeControl.NORMAL) {
            typeName += String.format(" (%s)", timeControl().getLongName());
        }
        if (teamTournament()) {
            if (typeName.length() == 0) {
                typeName = "Team";
            } else {
                typeName = "Team-" + typeName;
            }
        }
        return typeName;
    }

    @Override
    public int compareTo(Tournament o) {
        if (this.date().year() != o.date().year()) {
            return o.date().year() - this.date().year();
        }
        int dif = CBUtil.compareString(title(), o.title());

        if (dif != 0) return dif;
        dif = CBUtil.compareString(place(), o.place());
        if (dif != 0) return dif;

        if (this.date().month() != o.date().month()) {
            return o.date().month() - this.date().month();
        }
        return o.date().day() - this.date().day();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tournament that = (Tournament) o;

        return title().equals(that.title()) && this.date().year() == that.date().year();
    }

    @Override
    public int hashCode() {
        return title().hashCode() + this.date().year();
    }

    public String getCategoryRoman() {
        String[] roman = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        int cat = category();
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
