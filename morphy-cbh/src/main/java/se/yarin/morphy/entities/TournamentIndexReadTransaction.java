package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.metrics.MetricsProvider;

import java.util.ArrayList;
import java.util.List;

public class TournamentIndexReadTransaction extends EntityIndexReadTransaction<Tournament>
    implements TournamentIndexTransaction {
  private final @Nullable TournamentExtraStorage tournamentExtraStorage;

  public TournamentIndexReadTransaction(@NotNull TournamentIndex index) {
    this(index, null);
  }

  public TournamentIndexReadTransaction(
      @NotNull TournamentIndex index, @Nullable TournamentExtraStorage extraStorage) {
    super(index);
    this.tournamentExtraStorage = extraStorage;
  }

  public @NotNull TournamentExtra getExtra(int id) {
    if (tournamentExtraStorage == null) {
      throw new IllegalStateException("Transaction does not contain extra storage");
    }
    return tournamentExtraStorage.get(id);
  }

  @Override
  public @NotNull List<MetricsProvider> metricsProviders() {
    List<MetricsProvider> metricsProviders = super.metricsProviders();
    if (this.tournamentExtraStorage != null) {
      metricsProviders = new ArrayList<>(metricsProviders);
      metricsProviders.add(this.tournamentExtraStorage);
      metricsProviders = List.copyOf(metricsProviders);
    }
    return metricsProviders;
  }
}
