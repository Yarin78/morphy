package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;

@Value.Immutable
public abstract class Team extends Entity implements Comparable<Team> {
    public abstract String title();

    @Value.Default
    public int teamNumber() { return 0; }

    @Value.Default
    public boolean season() { return false; }

    @Value.Default
    public int year() { return 0; }

    @Value.Default
    public Nation nation() {
        return Nation.NONE;
    }

    @Override
    public Entity withCountAndFirstGameId(int count, int firstGameId) {
        return ImmutableTeam.builder().from(this).count(count).firstGameId(firstGameId).build();
    }

    public static Team of(String title) {
        return ImmutableTeam.builder().title(title).build();
    }

    public static Team of(String title, int teamNumber, boolean season, int year, Nation nation) {
        return ImmutableTeam.builder()
                .title(title)
                .teamNumber(teamNumber)
                .season(season)
                .year(year)
                .nation(nation)
                .build();
    }

    @Override
    public String toString() {
        return title();
    }

    @Override
    public int compareTo(Team o) {
        int dif = CBUtil.compareStringUnsigned(title(), o.title());
        if (dif != 0) {
            return dif;
        }
        if (teamNumber() != o.teamNumber()) {
            return teamNumber() - o.teamNumber();
        }
        if (season() != o.season()) {
            return season() ? -1 : 1;
        }
        if (year() != o.year()) {
            return year() - o.year();
        }
        return nation().ordinal() - o.nation().ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Team that = (Team) o;

        return title().equals(that.title()) && teamNumber() == that.teamNumber()
                && season() == that.season() && year() == that.year() && nation().equals(that.nation());
    }

    @Override
    public int hashCode() {
        return title().hashCode();
    }
}
