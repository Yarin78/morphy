package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class ManualPlayerSearcher implements PlayerSearcher {
    private final Set<Player> players;

    public ManualPlayerSearcher(@NotNull Player player) {
        this(Arrays.asList(player));
    }

    public ManualPlayerSearcher(@NotNull Collection<Player> players) {
        this.players = new HashSet<>(players);
    }

    @Override
    public Stream<Hit> search() {
        return players.stream().map(Hit::new);
    }

    @Override
    public List<Player> quickSearch() {
        return new ArrayList<>(players);
    }

    @Override
    public boolean matches(@NotNull Player player) {
        return players.contains(player);
    }
}
