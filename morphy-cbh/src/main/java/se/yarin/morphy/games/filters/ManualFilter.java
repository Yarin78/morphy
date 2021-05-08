package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ManualFilter implements ItemStorageFilter<GameHeader> {
    private final Set<Integer> gameHeaderIds;

    public ManualFilter(@NotNull GameHeader gameHeader) {
        gameHeaderIds = Set.of(gameHeader.id());
    }

    public ManualFilter(@NotNull Collection<GameHeader> gameHeaders) {
        gameHeaderIds = gameHeaders.stream().map(GameHeader::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return gameHeaderIds.contains(gameHeader.id());
    }
}
