package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QTournamentsWithId extends ItemQuery<Tournament> {
    private final @NotNull Set<Tournament> tournaments;
    private final @NotNull Set<Integer> tournamentIds;

    public QTournamentsWithId(@NotNull Collection<Tournament> tournaments) {
        this.tournaments = new HashSet<>(tournaments);
        this.tournamentIds = tournaments.stream().map(Tournament::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
        return tournamentIds.contains(tournament.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return tournaments.size();
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        return tournaments.stream();
    }
}
