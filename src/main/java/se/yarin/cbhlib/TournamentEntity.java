package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import se.yarin.chess.Date;

public class TournamentEntity implements Entity, Comparable<TournamentEntity> {
    @Getter
    private int id;

    @Getter @Setter
    @NonNull
    private String title;

    @Getter @Setter
    @NonNull
    private Date date = Date.today();

    @Getter @Setter
    private int category;

    @Getter @Setter
    private int rounds;

    @Getter @Setter
    @NonNull
    private TournamentType type = TournamentType.NONE;

    @Getter @Setter
    private boolean complete;

    @Getter @Setter
    private boolean threePointsWin;

    @Getter @Setter
    private boolean teamTournament;

    @Getter @Setter
    private boolean boardPoints;

    @Getter @Setter
    @NonNull
    private TournamentTimeControl timeControl = TournamentTimeControl.NORMAL;

    @Getter @Setter
    @NonNull
    private String place = "";

    @Getter @Setter
    @NonNull
    private Nation nation = Nation.NONE;

    // Missing here is City, latitude, longitude
    // Missing is also tiebreak rules
    // Maybe stored in another database?

    @Getter @Setter
    private int noGames;

    @Getter @Setter
    private int firstGameId;

    TournamentEntity(int id, @NonNull String title) {
        this.id = id;
        this.title = title;
    }

    public TournamentEntity(@NonNull String title) {
        this(-1, title);
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(TournamentEntity o) {
        return title.compareTo(o.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TournamentEntity that = (TournamentEntity) o;

        return title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }
}
