package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.util.*;
import java.util.stream.Stream;

public class ManualTournamentSearcher implements TournamentSearcher {
    private final Set<TournamentEntity> tournaments;

    public ManualTournamentSearcher(@NonNull TournamentEntity tournament) {
        this(Arrays.asList(tournament));
    }

    public ManualTournamentSearcher(@NonNull Collection<TournamentEntity> tournaments) {
        this.tournaments = new HashSet<>(tournaments);
    }

    @Override
    public Stream<TournamentSearcher.Hit> search() {
        return tournaments.stream().map(TournamentSearcher.Hit::new);
    }

    @Override
    public List<TournamentEntity> quickSearch() {
        return new ArrayList<>(tournaments);
    }

    @Override
    public boolean matches(@NonNull TournamentEntity tournament) {
        return tournaments.contains(tournament);
    }
}
