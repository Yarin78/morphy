package se.yarin.morphy.entities;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.util.CBUtil;

@Value.Immutable
public abstract class Team extends Entity implements Comparable<Team> {
    @Value.Default
    @NotNull
    public String title() { return "";}

    @Value.Default
    public int teamNumber() { return 0; }

    @Value.Default
    public boolean season() { return false; }

    @Value.Default
    public int year() { return 0; }

    @Value.Default
    public @NotNull Nation nation() {
        return Nation.NONE;
    }

    @Override
    public Entity withCountAndFirstGameId(int count, int firstGameId) {
        return ImmutableTeam.builder().from(this).count(count).firstGameId(firstGameId).build();
    }

    public static Team of(@Nullable String title) {
        ImmutableTeam.Builder builder = ImmutableTeam.builder();
        if (title != null) {
            builder.title(title);
        }
        return builder.build();
    }

    public static Team of(@Nullable String title, int teamNumber, boolean season, int year, @Nullable Nation nation) {
        ImmutableTeam.Builder builder = ImmutableTeam.builder();
        if (title != null) {
            builder.title(title);
        }
        if (nation != null) {
            builder.nation(nation);
        }
        return builder
            .teamNumber(teamNumber)
            .season(season)
            .year(year)
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
