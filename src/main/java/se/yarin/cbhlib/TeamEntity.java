package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import se.yarin.chess.Date;

public class TeamEntity implements Entity, Comparable<TeamEntity> {
    @Getter
    private int id;

    @Getter @Setter
    @NonNull
    private String title;

    @Getter @Setter
    private int teamNumber;

    @Getter @Setter
    private boolean season;

    @Getter @Setter
    private int year;

    @Getter @Setter
    @NonNull
    private Nation nation = Nation.NONE;

    @Getter @Setter
    private int noGames;

    @Getter @Setter
    private int firstGameId;

    TeamEntity(int id, @NonNull String title) {
        this.id = id;
        this.title = title;
    }

    public TeamEntity(@NonNull String title) {
        this(-1, title);
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(TeamEntity o) {
        return title.compareTo(o.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeamEntity that = (TeamEntity) o;

        return title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }
}
