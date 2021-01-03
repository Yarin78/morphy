package se.yarin.cbhlib;

import lombok.*;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.chess.Date;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TournamentEntity implements Entity, Comparable<TournamentEntity> {
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

    // Missing here is City, latitude, longitude
    // Missing is also tiebreak rules
    // Maybe stored in another database?

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public TournamentEntity(@NonNull String title, @NonNull Date date) {
        // Tournaments are keyed (and sorted) by year + title
        this.title = title;
        this.date = date;
        this.type = TournamentType.NONE;
        this.timeControl = TournamentTimeControl.NORMAL;
        this.place = "";
        this.nation = Nation.NONE;
    }

    @Override
    public String toString() {
        return this.date.year() + ": " + this.title;
    }

    @Override
    public int compareTo(TournamentEntity o) {
        if (this.date.year() != o.date.year()) {
            return o.date.year() - this.date.year();
        }
        return CBUtil.compareString(title, o.title);
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
}
