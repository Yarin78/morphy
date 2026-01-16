package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class TournamentFilter
    implements ItemStorageFilter<GameHeader>, GameFilter, GameEntityFilter<Tournament> {
  private final @NotNull Set<Integer> tournamentIds;

  public TournamentFilter(int tournamentId) {
    this(new int[] {tournamentId});
  }

  public TournamentFilter(int[] tournamentIds) {
    this.tournamentIds =
        Arrays.stream(tournamentIds).boxed().collect(Collectors.toUnmodifiableSet());
  }

  public TournamentFilter(@NotNull Tournament tournament) {
    this(Set.of(tournament));
  }

  public TournamentFilter(@NotNull Collection<Tournament> tournaments) {
    this.tournamentIds =
        tournaments.stream().map(Tournament::id).collect(Collectors.toCollection(HashSet::new));
  }

  @Override
  public EntityType entityType() {
    return EntityType.TOURNAMENT;
  }

  public List<Integer> entityIds() {
    return new ArrayList<>(tournamentIds);
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    return tournamentIds.contains(gameHeader.tournamentId());
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    int tournamentId;

    if (IsGameFilter.isGame(buf)) {
      // Regular game
      tournamentId = ByteBufferUtil.getUnsigned24BitB(buf, 15);
    } else {
      // Guiding text
      tournamentId = ByteBufferUtil.getUnsigned24BitB(buf, 7);
    }
    return tournamentIds.contains(tournamentId);
  }

  @Override
  public String toString() {
    if (tournamentIds.size() == 1) {
      return "tournamentId=" + tournamentIds.stream().findFirst().get();
    } else {
      return "tournamentId in ( "
          + tournamentIds.stream().map(Object::toString).collect(Collectors.joining(", "))
          + ")";
    }
  }
}
