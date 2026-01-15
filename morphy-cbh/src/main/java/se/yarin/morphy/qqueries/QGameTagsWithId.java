package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.GameTag;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGameTagsWithId extends ItemQuery<GameTag> {
  private final @NotNull Set<GameTag> gameTags;
  private final @NotNull Set<Integer> gameTagIds;

  public QGameTagsWithId(@NotNull Collection<GameTag> gameTags) {
    this.gameTags = new HashSet<>(gameTags);
    this.gameTagIds =
        gameTags.stream().map(GameTag::id).collect(Collectors.toCollection(HashSet::new));
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull GameTag gameTag) {
    return gameTagIds.contains(gameTag.id());
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return gameTags.size();
  }

  @Override
  public @NotNull Stream<GameTag> stream(@NotNull DatabaseReadTransaction txn) {
    return gameTags.stream();
  }
}
