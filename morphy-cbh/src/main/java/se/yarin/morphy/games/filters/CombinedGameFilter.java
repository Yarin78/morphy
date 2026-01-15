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
    if (filters.isEmpty()) {
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

    if (!gameHeaderFilters.isEmpty()) {
      combinedGameHeaderFilter =
          new ItemStorageFilter<>() {
            @Override
            public boolean matches(int id, @NotNull GameHeader gameHeader) {
              return gameHeaderFilters.stream()
                  .allMatch(itemFilter -> itemFilter.matches(id, gameHeader));
            }

            @Override
            public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
              return gameHeaderFilters.stream()
                  .allMatch(itemFilter -> itemFilter.matchesSerialized(id, buf));
            }
          };
    } else {
      combinedGameHeaderFilter = null;
    }

    if (!extendedGameHeaderFilters.isEmpty()) {
      combinedExtendedGameHeaderFilter =
          new ItemStorageFilter<>() {
            @Override
            public boolean matches(int id, @NotNull ExtendedGameHeader extendedGameHeader) {
              return extendedGameHeaderFilters.stream()
                  .allMatch(itemFilter -> itemFilter.matches(id, extendedGameHeader));
            }

            @Override
            public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
              return extendedGameHeaderFilters.stream()
                  .allMatch(itemFilter -> itemFilter.matchesSerialized(id, buf));
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
