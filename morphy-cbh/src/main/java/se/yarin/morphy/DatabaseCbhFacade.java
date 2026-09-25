package se.yarin.morphy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.Capabilities;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.FilterQueryParser;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.convert.AnnotatorDtoConverter;
import se.yarin.morphy.convert.GameDtoConverter;
import se.yarin.morphy.convert.GameDtoImporter;
import se.yarin.morphy.convert.GameTagDtoConverter;
import se.yarin.morphy.convert.PlayerDtoConverter;
import se.yarin.morphy.convert.SourceDtoConverter;
import se.yarin.morphy.convert.TeamDtoConverter;
import se.yarin.morphy.convert.TournamentDtoConverter;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QueryPlanner;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;
import se.yarin.morphy.queries.filter.AbstractEntityQueryBuilder;
import se.yarin.morphy.queries.filter.AnnotatorQueryBuilder;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.filter.GameTagQueryBuilder;
import se.yarin.morphy.queries.filter.PlayerQueryBuilder;
import se.yarin.morphy.queries.filter.SourceQueryBuilder;
import se.yarin.morphy.queries.filter.TeamQueryBuilder;
import se.yarin.morphy.queries.filter.TournamentQueryBuilder;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.queries.visualisation.QueryDescriptionFormatter;

/**
 * The vendor-neutral {@link Database} over a ChessBase v1 ({@code .cbh}) database.
 *
 * <p>It wraps the v1 engine, {@link DatabaseCbh}, and translates between DTOs and the v1 records:
 * games and entities are converted to and from DTOs, and {@link Query queries} run through the v1
 * query planner. Closing it closes the engine. It also offers {@link CbhDiagnostics} through
 * {@link #extension(Class)}.
 *
 * <p>Obtain one through {@link se.yarin.morphy.api.Databases#open}; code that needs the v1
 * internals uses {@link DatabaseCbh} directly instead.
 */
public class DatabaseCbhFacade implements Database, CbhDiagnostics {

  private final @NotNull DatabaseCbh database;
  private final @NotNull PlayerDtoConverter players = new PlayerDtoConverter();
  private final @NotNull TournamentDtoConverter tournaments = new TournamentDtoConverter();
  private final @NotNull AnnotatorDtoConverter annotators = new AnnotatorDtoConverter();
  private final @NotNull SourceDtoConverter sources = new SourceDtoConverter();
  private final @NotNull TeamDtoConverter teams = new TeamDtoConverter();
  private final @NotNull GameTagDtoConverter gameTags = new GameTagDtoConverter();
  private final @NotNull GameDtoConverter gameDtoConverter =
      new GameDtoConverter(players, tournaments, annotators, sources, teams, gameTags);
  private final @NotNull GameDtoImporter gameDtoImporter = new GameDtoImporter();
  private final @NotNull GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();
  private final @NotNull Map<EntityKind<?>, KindSupport<?, ?>> kinds;

  /**
   * How one entity kind maps onto v1: its entity type, query builder, index transaction and DTO
   * conversion.
   */
  private record KindSupport<E extends Entity & Comparable<E>, D>(
      @NotNull EntityType type,
      @NotNull AbstractEntityQueryBuilder<E> queryBuilder,
      @NotNull Function<DatabaseTransaction, EntityIndexTransaction<E>> index,
      @NotNull BiFunction<DatabaseTransaction, E, D> toDto) {}

  /** Wraps an open v1 database; the facade takes over closing it. */
  public DatabaseCbhFacade(@NotNull DatabaseCbh database) {
    this.database = database;
    this.kinds =
        Map.of(
            EntityKind.PLAYER,
            new KindSupport<>(
                EntityType.PLAYER,
                new PlayerQueryBuilder(),
                DatabaseTransaction::playerTransaction,
                (txn, p) -> players.toDto(p)),
            EntityKind.TOURNAMENT,
            new KindSupport<Tournament, TournamentDto>(
                EntityType.TOURNAMENT,
                new TournamentQueryBuilder(),
                DatabaseCbhFacade::tournamentIndex,
                (txn, t) -> tournaments.toDto(t, txn.getTournamentExtra(t.id()))),
            EntityKind.ANNOTATOR,
            new KindSupport<>(
                EntityType.ANNOTATOR,
                new AnnotatorQueryBuilder(),
                DatabaseTransaction::annotatorTransaction,
                (txn, a) -> annotators.toDto(a)),
            EntityKind.SOURCE,
            new KindSupport<>(
                EntityType.SOURCE,
                new SourceQueryBuilder(),
                DatabaseTransaction::sourceTransaction,
                (txn, s) -> sources.toDto(s)),
            EntityKind.TEAM,
            new KindSupport<>(
                EntityType.TEAM,
                new TeamQueryBuilder(),
                DatabaseTransaction::teamTransaction,
                (txn, t) -> teams.toDto(t)),
            EntityKind.GAME_TAG,
            new KindSupport<>(
                EntityType.GAME_TAG,
                new GameTagQueryBuilder(),
                DatabaseTransaction::gameTagTransaction,
                (txn, g) -> gameTags.toDto(g)));
  }

  /**
   * The tournament index of a transaction. {@link DatabaseTransaction#tournamentTransaction()} is
   * typed as {@link se.yarin.morphy.entities.TournamentIndexTransaction}, but both implementations
   * (read and write) are entity index transactions.
   */
  private static EntityIndexTransaction<Tournament> tournamentIndex(DatabaseTransaction txn) {
    return (EntityIndexTransaction<Tournament>) txn.tournamentTransaction();
  }

  // ── Identity, capabilities, lifecycle ─────────────────────────────────────

  @Override
  public @NotNull String name() {
    return database.name();
  }

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.CBH;
  }

  @Override
  public @NotNull Capabilities capabilities() {
    boolean writable = database.isWritable();
    return new Capabilities(writable, true, writable, true);
  }

  private void requireWritable() {
    if (!database.isWritable()) {
      throw new UnsupportedOperationException("Database " + name() + " is read-only");
    }
  }

  @Override
  public void close() throws IOException {
    database.close();
  }

  // ── Games ────────────────────────────────────────────────────────────────

  @Override
  public long gameCount() {
    return database.count();
  }

  @Override
  public long addGame(@NotNull GameDto game) {
    requireWritable();
    if ("text".equals(game.type())) {
      return database.addText(gameDtoImporter.toTextModel(game));
    }
    return database.addGame(gameDtoImporter.toGameModel(game));
  }

  @Override
  public void replaceGame(long id, @NotNull GameDto game) {
    requireWritable();
    if (id < 1 || id > database.count()) {
      throw new IllegalArgumentException("No game with id " + id);
    }
    if ("text".equals(game.type())) {
      database.replaceText((int) id, gameDtoImporter.toTextModel(game));
    } else {
      database.replaceGame((int) id, gameDtoImporter.toGameModel(game));
    }
  }

  @Override
  public @Nullable GameDto getGame(long id, @NotNull GameFetchOptions fetch) {
    if (id < 1 || id > Integer.MAX_VALUE) {
      return null;
    }
    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
      Game game;
      try {
        game = txn.getGame((int) id);
      } catch (IllegalArgumentException e) {
        return null;
      }
      return toDto(game, fetch);
    }
  }

  @Override
  public @NotNull ResultPage<GameDto> findGames(
      @NotNull Query query, @NotNull GameFetchOptions fetch) {
    List<FilterCondition> conditions = conditions(query, gameQueryBuilder.defaultField());
    QuerySortOrder<Game> sortOrder = gameQueryBuilder.buildSortOrder(query.sort());

    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
      if (conditions.isEmpty() && isIdOrder(query.sort())) {
        return listGamesById(txn, query, fetch);
      }

      GameQuery gameQuery = gameQuery(conditions, sortOrder);
      QueryOperator<Game> plan = gamePlans(new QueryContext(txn, false), gameQuery).getFirst();
      List<Game> games = plan.stream().map(QueryData::data).toList();

      List<GameDto> page =
          slice(games, query).stream().map(game -> toDto(game, fetch)).toList();
      return new ResultPage<>(
          page, query.offset(), query.limit(), (long) games.size(), describe(conditions));
    }
  }

  private GameQuery gameQuery(List<FilterCondition> conditions, QuerySortOrder<Game> sortOrder) {
    GameQuery base = gameQueryBuilder.buildQuery(database, conditions);
    return new GameQuery(
        database, base.gameFilters(), new ArrayList<>(base.entityJoins()), sortOrder, 0);
  }

  /** The candidate plans for a game query, cheapest first. */
  private List<QueryOperator<Game>> gamePlans(QueryContext context, GameQuery gameQuery) {
    QueryPlanner planner = database.queryPlanner();
    return planner.sortQueryPlansByCost(planner.getGameQueryPlans(context, gameQuery, true));
  }

  /** Lists games straight from the header index when the query needs no filtering or sorting. */
  private ResultPage<GameDto> listGamesById(
      DatabaseReadTransaction txn, Query query, GameFetchOptions fetch) {
    int total = database.count();
    boolean descending =
        !query.sort().isNatural()
            && query.sort().keys().getFirst().direction() == Sort.Direction.DESCENDING;
    List<GameDto> page = new ArrayList<>();
    for (int i = query.offset(); i < query.offset() + query.limit() && i < total; i++) {
      int id = descending ? total - i : i + 1;
      page.add(toDto(txn.getGame(id), fetch));
    }
    return new ResultPage<>(page, query.offset(), query.limit(), (long) total, "");
  }

  /** Whether a sort is the natural order, or by id alone in either direction. */
  private static boolean isIdOrder(Sort sort) {
    return sort.isNatural()
        || (sort.keys().size() == 1 && sort.keys().getFirst().field().equalsIgnoreCase("id"));
  }

  @Override
  public @NotNull SearchSchema gameSearchSchema() {
    return new SearchSchema(
        gameQueryBuilder.defaultField(),
        gameQueryBuilder.availableFields(),
        gameQueryBuilder.hiddenFields(),
        sortFields(gameQueryBuilder.availableSortFields()));
  }

  private GameDto toDto(@NotNull Game game, @NotNull GameFetchOptions fetch) {
    return gameDtoConverter.toDto(
        game,
        fetch.includeMoves(),
        fetch.includeText(),
        fetch.includeEntityDetails(),
        fetch.includeEntityDetails(),
        fetch.includeEntityDetails(),
        fetch.includeRawData());
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  @Override
  public long entityCount(@NotNull EntityKind<?> kind) {
    return database.entityIndex(support(kind).type()).count();
  }

  @Override
  public @Nullable <T> T getEntity(@NotNull EntityKind<T> kind, long id) {
    return getEntity(support(kind), id);
  }

  private <E extends Entity & Comparable<E>, D> @Nullable D getEntity(
      KindSupport<E, D> support, long id) {
    if (id < 0 || id > Integer.MAX_VALUE) {
      return null;
    }
    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
      E entity;
      try {
        entity = support.index().apply(txn).get((int) id);
      } catch (IllegalArgumentException e) {
        return null;
      }
      // Entities exist only through the games that refer to them
      return entity.count() > 0 ? support.toDto().apply(txn, entity) : null;
    }
  }

  @Override
  public @NotNull <T> ResultPage<T> findEntities(
      @NotNull EntityKind<T> kind, @NotNull Query query) {
    return findEntities(support(kind), query);
  }

  private <E extends Entity & Comparable<E>, D> ResultPage<D> findEntities(
      KindSupport<E, D> support, Query query) {
    List<FilterCondition> conditions = conditions(query, support.queryBuilder().defaultField());
    EntityQuery<E> entityQuery = entityQuery(support, conditions, query.sort());

    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
      QueryOperator<E> plan = entityPlans(new QueryContext(txn, false), entityQuery).getFirst();
      // Entities exist only through the games that refer to them
      List<E> entities =
          plan.stream().map(QueryData::data).filter(e -> e.count() > 0).toList();

      List<D> page =
          slice(entities, query).stream().map(e -> support.toDto().apply(txn, e)).toList();
      return new ResultPage<>(
          page, query.offset(), query.limit(), (long) entities.size(), describe(conditions));
    }
  }

  private <E extends Entity & Comparable<E>> EntityQuery<E> entityQuery(
      KindSupport<E, ?> support, List<FilterCondition> conditions, Sort sort) {
    AbstractEntityQueryBuilder<E> builder = support.queryBuilder();
    QuerySortOrder<E> sortOrder = builder.buildSortOrder(sort.toString());
    EntityQuery<E> base = builder.buildQuery(database, conditions);
    return new EntityQuery<>(database, support.type(), base.filters(), sortOrder, 0);
  }

  /** The candidate plans for an entity query, cheapest first. */
  private <E extends Entity & Comparable<E>> List<QueryOperator<E>> entityPlans(
      QueryContext context, EntityQuery<E> entityQuery) {
    QueryPlanner planner = database.queryPlanner();
    return planner.sortQueryPlansByCost(planner.getEntityQueryPlans(context, entityQuery, true));
  }

  @Override
  public @NotNull SearchSchema entitySearchSchema(@NotNull EntityKind<?> kind) {
    AbstractEntityQueryBuilder<?> builder = support(kind).queryBuilder();
    return new SearchSchema(
        builder.defaultField(),
        builder.availableFields(),
        Set.of(),
        sortFields(builder.availableSortFields()));
  }

  @Override
  public @NotNull <T> T updateEntity(@NotNull EntityKind<T> kind, long id, @NotNull T dto) {
    requireWritable();
    if (getEntity(kind, id) == null) {
      throw new IllegalArgumentException("No " + kind + " with id " + id);
    }
    int entityId = (int) id;
    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(database)) {
      if (kind == EntityKind.PLAYER) {
        Player player = players.toPlayer((PlayerDto) dto);
        ensureUnique(txn.playerTransaction(), player, entityId, kind);
        txn.updatePlayerById(entityId, player);
      } else if (kind == EntityKind.TOURNAMENT) {
        TournamentDto tournamentDto = (TournamentDto) dto;
        Tournament tournament = tournaments.toTournament(tournamentDto);
        ensureUnique(txn.tournamentTransaction(), tournament, entityId, kind);
        txn.updateTournamentById(
            entityId, tournament, tournaments.toTournamentExtra(tournamentDto));
      } else if (kind == EntityKind.ANNOTATOR) {
        Annotator annotator = annotators.toAnnotator((AnnotatorDto) dto);
        ensureUnique(txn.annotatorTransaction(), annotator, entityId, kind);
        txn.updateAnnotatorById(entityId, annotator);
      } else if (kind == EntityKind.SOURCE) {
        Source source = sources.toSource((SourceDto) dto);
        ensureUnique(txn.sourceTransaction(), source, entityId, kind);
        txn.updateSourceById(entityId, source);
      } else if (kind == EntityKind.TEAM) {
        Team team = teams.toTeam((TeamDto) dto);
        ensureUnique(txn.teamTransaction(), team, entityId, kind);
        txn.updateTeamById(entityId, team);
      } else if (kind == EntityKind.GAME_TAG) {
        GameTag gameTag = gameTags.toGameTag((GameTagDto) dto);
        ensureUnique(txn.gameTagTransaction(), gameTag, entityId, kind);
        txn.updateGameTagById(entityId, gameTag);
      } else {
        throw new IllegalArgumentException("Unsupported entity kind: " + kind);
      }
      txn.commit();
    }
    return getEntity(kind, id);
  }

  /** Rejects an update that would give an entity the same key as another existing entity. */
  private static <E extends Entity & Comparable<E>> void ensureUnique(
      EntityIndexTransaction<E> index, E entity, int id, EntityKind<?> kind) {
    E existing = index.get(entity);
    if (existing != null && existing.id() != id) {
      throw new IllegalArgumentException(
          "Cannot update " + kind + " " + id + ": another one with the same key already exists");
    }
  }

  @SuppressWarnings("unchecked")
  private <T> KindSupport<?, T> support(@NotNull EntityKind<T> kind) {
    KindSupport<?, ?> support = kinds.get(kind);
    if (support == null) {
      throw new IllegalArgumentException("Unsupported entity kind: " + kind);
    }
    return (KindSupport<?, T>) support;
  }

  // ── Diagnostics ──────────────────────────────────────────────────────────

  /** Plans estimated to cost this much or more are not executed when comparing plans. */
  private static final long MAX_PLAN_COST_TO_EXECUTE = 100_000;

  @Override
  public @NotNull QueryExplanation explainGames(
      @NotNull Query query, boolean executeAllPlans) {
    List<FilterCondition> conditions = conditions(query, gameQueryBuilder.defaultField());
    GameQuery gameQuery = gameQuery(conditions, gameQueryBuilder.buildSortOrder(query.sort()));
    return explain(
        QueryDescriptionFormatter.format(gameQuery),
        context -> gamePlans(context, gameQuery),
        executeAllPlans);
  }

  @Override
  public @NotNull QueryExplanation explainEntities(
      @NotNull EntityKind<?> kind, @NotNull Query query, boolean executeAllPlans) {
    return explainEntities(support(kind), query, executeAllPlans);
  }

  private <E extends Entity & Comparable<E>> CbhDiagnostics.QueryExplanation explainEntities(
      KindSupport<E, ?> support, Query query, boolean executeAllPlans) {
    List<FilterCondition> conditions = conditions(query, support.queryBuilder().defaultField());
    EntityQuery<E> entityQuery = entityQuery(support, conditions, query.sort());
    return explain(
        QueryDescriptionFormatter.format(entityQuery),
        context -> entityPlans(context, entityQuery),
        executeAllPlans);
  }

  /**
   * Executes the cheapest plan with profiling and, if asked, the other plans too, comparing the
   * items they find. Each execution gets plans from a fresh context, since operators hold state.
   */
  private <T extends IdObject> CbhDiagnostics.QueryExplanation explain(
      String description,
      Function<QueryContext, List<QueryOperator<T>>> plansFor,
      boolean executeAllPlans) {
    try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
      List<QueryOperator<T>> plans = plansFor.apply(new QueryContext(txn, true));
      List<CbhDiagnostics.ExplainedPlan> explained = new ArrayList<>();

      List<QueryData<T>> bestResults = plans.getFirst().executeProfiled();
      List<Integer> bestIds = sortedIds(bestResults);
      explained.add(
          new CbhDiagnostics.ExplainedPlan(plans.getFirst(), true, bestResults.size(), null));

      Boolean allPlansAgree = executeAllPlans ? true : null;
      for (int i = 1; i < plans.size(); i++) {
        QueryOperator<T> plan = plans.get(i);
        long estimatedCost = (long) plan.getQueryCost().estimatedTotalCost();
        if (!executeAllPlans || estimatedCost >= MAX_PLAN_COST_TO_EXECUTE) {
          explained.add(new CbhDiagnostics.ExplainedPlan(plan, false, null, null));
          continue;
        }
        QueryOperator<T> freshPlan = plansFor.apply(new QueryContext(txn, true)).get(i);
        List<QueryData<T>> results = freshPlan.executeProfiled();
        boolean differs = !sortedIds(results).equals(bestIds);
        if (differs) {
          allPlansAgree = false;
        }
        explained.add(new CbhDiagnostics.ExplainedPlan(freshPlan, true, results.size(), differs));
      }
      return new CbhDiagnostics.QueryExplanation(description, allPlansAgree, explained);
    }
  }

  private static <T extends IdObject> List<Integer> sortedIds(List<QueryData<T>> results) {
    return results.stream().map(QueryData::id).sorted().toList();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /** The query's free-text filter, parsed with the target's default field, plus its conditions. */
  private static List<FilterCondition> conditions(Query query, String defaultField) {
    List<FilterCondition> conditions = new ArrayList<>();
    if (query.filter() != null && !query.filter().isBlank()) {
      conditions.addAll(new FilterQueryParser(defaultField).parse(query.filter()));
    }
    conditions.addAll(query.conditions());
    return conditions;
  }

  private static <T> List<T> slice(List<T> items, Query query) {
    int from = Math.min(query.offset(), items.size());
    int to = Math.min(query.offset() + query.limit(), items.size());
    return items.subList(from, to);
  }

  /** A readable form of the applied conditions, e.g. {@code result:1-0 AND rating..2600..}. */
  private static String describe(List<FilterCondition> conditions) {
    return conditions.stream()
        .map(
            c -> {
              String s = c.field() + c.operator() + c.value();
              if (!c.modifiers().isEmpty()) {
                s +=
                    c.modifiers().entrySet().stream()
                        .map(e -> "," + e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining());
              }
              return s;
            })
        .collect(Collectors.joining(" AND "));
  }

  private static <T extends IdObject> List<SearchSchema.SortField> sortFields(
      List<QuerySortField<T>> fields) {
    return fields.stream()
        .map(
            f ->
                new SearchSchema.SortField(
                    f.name(),
                    f.defaultDirection() == QuerySortOrder.Direction.DESCENDING
                        ? Sort.Direction.DESCENDING
                        : Sort.Direction.ASCENDING))
        .toList();
  }
}
