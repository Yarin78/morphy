package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.filters.TeamTitleFilter;

import java.util.stream.Stream;

public class QTeamsWithTitle extends ItemQuery<Team> {
    private final @NotNull TeamTitleFilter filter;

    public QTeamsWithTitle(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this(new TeamTitleFilter(title, caseSensitive, exactMatch));
    }

    public QTeamsWithTitle(@NotNull TeamTitleFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Team team) {
        return filter.matches(team);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Team> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Serialization stream
        return txn.teamTransaction().stream().filter(team -> matches(txn, team));
    }
}
