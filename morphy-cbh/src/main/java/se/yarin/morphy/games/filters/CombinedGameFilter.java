package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CombinedGameFilter implements GameFilter {
    private final @NotNull List<GameFilter> gameFilters;

    private final @Nullable ItemStorageFilter<GameHeader> combinedGameHeaderFilter;
    private final @Nullable ItemStorageFilter<ExtendedGameHeader> combinedExtendedGameHeaderFilter;

    public static @Nullable GameFilter combine(@NotNull List<GameFilter> filters) {
        if (filters.size() == 0) {
            return null;
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new CombinedGameFilter(filters);
    }

    public CombinedGameFilter(@NotNull List<GameFilter> filters) {
        this.gameFilters = List.copyOf(filters); // For toString only

        ArrayList<ItemStorageFilter<GameHeader>> gameHeaderFilters = new ArrayList<>();
        ArrayList<ItemStorageFilter<ExtendedGameHeader>> extendedGameHeaderFilters = new ArrayList<>();

        for (GameFilter filter : filters) {
            if (filter.gameHeaderFilter() != null) {
                gameHeaderFilters.add(filter.gameHeaderFilter());
            }
            if (filter.extendedGameHeaderFilter() != null) {
                extendedGameHeaderFilters.add(filter.extendedGameHeaderFilter());
            }
        }

        if (gameHeaderFilters.size() > 0) {
            combinedGameHeaderFilter = new ItemStorageFilter<>() {
                @Override
                public boolean matches(@NotNull GameHeader gameHeader) {
                    return gameHeaderFilters.stream().allMatch(itemFilter -> itemFilter.matches(gameHeader));
                }

                @Override
                public boolean matchesSerialized(@NotNull ByteBuffer buf) {
                    return gameHeaderFilters.stream().allMatch(itemFilter -> itemFilter.matchesSerialized(buf));
                }
            };
        } else {
            combinedGameHeaderFilter = null;
        }

        if (extendedGameHeaderFilters.size() > 0) {
            combinedExtendedGameHeaderFilter = new ItemStorageFilter<>() {
                @Override
                public boolean matches(@NotNull ExtendedGameHeader extendedGameHeader) {
                    return extendedGameHeaderFilters.stream().allMatch(itemFilter -> itemFilter.matches(extendedGameHeader));
                }

                @Override
                public boolean matchesSerialized(@NotNull ByteBuffer buf) {
                    return extendedGameHeaderFilters.stream().allMatch(itemFilter -> itemFilter.matchesSerialized(buf));
                }
            };
        } else {
            combinedExtendedGameHeaderFilter = null;
        }
    }

    @Override
    public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
        return combinedGameHeaderFilter;
    }

    @Override
    public @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() {
        return combinedExtendedGameHeaderFilter;
    }

    @Override
    public String toString() {
        return gameFilters.stream().map(Object::toString).collect(Collectors.joining(" and "));
    }
}
