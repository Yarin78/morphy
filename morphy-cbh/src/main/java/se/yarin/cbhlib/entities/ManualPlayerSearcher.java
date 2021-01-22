package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.util.*;
import java.util.stream.Stream;

public class ManualPlayerSearcher implements PlayerSearcher {
    private final Set<PlayerEntity> players;

    public ManualPlayerSearcher(@NonNull PlayerEntity player) {
        this(Arrays.asList(player));
    }
    
    public ManualPlayerSearcher(@NonNull Collection<PlayerEntity> players) {
        this.players = new HashSet<>(players);
    }

    @Override
    public Stream<Hit> search() {
        return players.stream().map(Hit::new);
    }

    @Override
    public List<PlayerEntity> quickSearch() {
        return new ArrayList<>(players);
    }

    @Override
    public boolean matches(@NonNull PlayerEntity player) {
        return players.contains(player);
    }
}
