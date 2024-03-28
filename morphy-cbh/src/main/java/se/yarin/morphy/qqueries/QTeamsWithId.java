package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Team;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QTeamsWithId extends ItemQuery<Team> {
    private final @NotNull Set<Team> teams;
    private final @NotNull Set<Integer> teamIds;

    public QTeamsWithId(@NotNull Collection<Team> teams) {
        this.teams = new HashSet<>(teams);
        this.teamIds = teams.stream().map(Team::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Team team) {
        return teamIds.contains(team.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return teams.size();
    }

    @Override
    public @NotNull Stream<Team> stream(@NotNull DatabaseReadTransaction txn) {
        return teams.stream();
    }
}
