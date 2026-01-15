package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ManualFilter implements ItemStorageFilter<GameHeader>, GameFilter {
  private final Set<Integer> gameHeaderIds;

  public ManualFilter(@NotNull GameHeader gameHeader) {
    gameHeaderIds = Set.of(gameHeader.id());
  }

  public ManualFilter(@NotNull Collection<GameHeader> gameHeaders) {
    gameHeaderIds =
        gameHeaders.stream().map(GameHeader::id).collect(Collectors.toCollection(HashSet::new));
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    return gameHeaderIds.contains(gameHeader.id());
  }

  public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return this;
  }

  @Override
  public String toString() {
    if (gameHeaderIds.size() == 1) {
      return "id=" + gameHeaderIds.stream().findFirst().get();
    } else {
      return "id in ( "
          + gameHeaderIds.stream().map(Object::toString).collect(Collectors.joining(", "))
          + ")";
    }
  }
}
