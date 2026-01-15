package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentTimeControlFilter;

import java.util.stream.Stream;

public class QTournamentsWithTimeControl extends ItemQuery<Tournament> {
  private final @NotNull TournamentTimeControlFilter filter;

  public QTournamentsWithTimeControl(@NotNull String timeControls) {
    this(new TournamentTimeControlFilter(timeControls));
  }

  public QTournamentsWithTimeControl(@NotNull TournamentTimeControlFilter filter) {
    this.filter = filter;
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
    return filter.matches(tournament);
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return INFINITE;
  }

  @Override
  public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
    return txn.tournamentTransaction().stream().filter(filter::matches);
  }
}
