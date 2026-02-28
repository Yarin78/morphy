package se.yarin.morphy.queries.operations;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

public class GameEntityLookup<E extends Entity & Comparable<E>> extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull EntityIndexReadTransaction<E> entityTransaction;
  private final @NotNull ToIntFunction<Game> entityIdExtractor;
  private final @NotNull E emptyEntity;
  private final @NotNull String name;

  public GameEntityLookup(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<Game> source,
      @NotNull EntityType entityType,
      @NotNull ToIntFunction<Game> entityIdExtractor,
      @NotNull E emptyEntity,
      @NotNull String name) {
    super(queryContext, source.hasFullData());
    this.source = source;
    @SuppressWarnings("unchecked")
    EntityIndexReadTransaction<E> txn =
        (EntityIndexReadTransaction<E>) transaction().entityTransaction(entityType);
    this.entityTransaction = txn;
    this.entityIdExtractor = entityIdExtractor;
    this.emptyEntity = emptyEntity;
    this.name = name;
  }

  public static GameEntityLookup<Player> whitePlayer(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.PLAYER, Game::whitePlayerId, Player.ofFullName(""),
        "GamePlayerLookup(white)");
  }

  public static GameEntityLookup<Player> blackPlayer(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.PLAYER, Game::blackPlayerId, Player.ofFullName(""),
        "GamePlayerLookup(black)");
  }

  public static GameEntityLookup<Tournament> tournament(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.TOURNAMENT, Game::tournamentId, Tournament.of("", Date.unset()),
        "GameTournamentLookup");
  }

  public static GameEntityLookup<Annotator> annotator(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.ANNOTATOR, Game::annotatorId, Annotator.of(""),
        "GameAnnotatorLookup");
  }

  public static GameEntityLookup<Source> source(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.SOURCE, Game::sourceId, Source.of(""),
        "GameSourceLookup");
  }

  public static GameEntityLookup<GameTag> gameTag(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.GAME_TAG, Game::gameTagId, GameTag.of(""),
        "GameTagLookup");
  }

  public static GameEntityLookup<Team> whiteTeam(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.TEAM, Game::whiteTeamId, Team.of(""),
        "GameTeamLookup(white)");
  }

  public static GameEntityLookup<Team> blackTeam(
      @NotNull QueryContext ctx, @NotNull QueryOperator<Game> source) {
    return new GameEntityLookup<>(
        ctx, source, EntityType.TEAM, Game::blackTeamId, Team.of(""),
        "GameTeamLookup(black)");
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull QuerySortOrder<Game> sortOrder() {
    return source.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  protected Stream<QueryData<Game>> operatorStream() {
    return this.source.stream()
        .map(
            data -> {
              Game game = data.data();
              int entityId = entityIdExtractor.applyAsInt(game);
              if (game.guidingText() || entityId < 0) {
                return data.withExtra(emptyEntity);
              }
              E entity = entityTransaction.get(entityId);
              return data.withExtra(entity);
            });
  }

  @Override
  protected void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();
    operatorCost
        .estimateRows(sourceCost.estimateRows())
        .estimateDeserializations(sourceCost.estimateRows())
        .estimatePageReads(
            context().queryPlanner().estimateGamePageReads(sourceCost.estimateRows()));
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return this.entityTransaction.metricsProviders();
  }
}
