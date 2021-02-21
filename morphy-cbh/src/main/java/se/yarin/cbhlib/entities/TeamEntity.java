package se.yarin.cbhlib.entities;

import lombok.*;
import se.yarin.cbhlib.util.CBUtil;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TeamEntity implements Entity, Comparable<TeamEntity> {
    @Getter
    private int id;

    @Getter
    @NonNull
    private String title;

    @Getter
    private int teamNumber;

    @Getter
    private boolean season;

    @Getter
    private int year;

    @Getter
    @NonNull
    @Builder.Default
    private Nation nation = Nation.NONE;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public TeamEntity(@NonNull String title) {
        this(title, 0, false, 0, Nation.NONE);
    }

    public TeamEntity(@NonNull String title, int teamNumber, boolean season, int year, @NonNull Nation nation) {
        this.title = title;
        this.teamNumber = teamNumber;
        this.season = season;
        this.year = year;
        this.nation = nation;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(TeamEntity o) {
        int dif = CBUtil.compareStringUnsigned(title, o.title);
        if (dif != 0) {
            return dif;
        }
        if (teamNumber != o.teamNumber) {
            return teamNumber - o.teamNumber;
        }
        if (season != o.season) {
            return season ? -1 : 1;
        }
        if (year != o.year) {
            return year - o.year;
        }
        return nation.ordinal() - o.nation.ordinal();
    }

    @Override
    public TeamEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public TeamEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
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
