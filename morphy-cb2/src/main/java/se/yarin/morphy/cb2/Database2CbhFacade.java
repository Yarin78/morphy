package se.yarin.morphy.cb2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
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
import se.yarin.morphy.cb2.convert.DtoConverter;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;
import se.yarin.morphy.cb2.query.EntitySearch;
import se.yarin.morphy.cb2.query.GameSearch;
import se.yarin.morphy.chessbase.convert.GameDtoImporter;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * The vendor-neutral {@link Database} over a ChessBase v2 ({@code .2cbh}) database.
 *
 * <p>It wraps the v2 engine, {@link Database2Cbh}, converting games and entities to DTOs and
 * running {@link Query queries} with {@link GameSearch} and {@link EntitySearch}, which accept the
 * same fields as a v1 database. Closing it closes the engine.
 *
 * <p>Obtain one through {@link se.yarin.morphy.api.Databases#open}; code that needs the v2
 * internals uses {@link Database2Cbh} directly instead.
 */
public class Database2CbhFacade implements Database {

  private final @NotNull Database2Cbh database;
  private final @NotNull DtoConverter converter = new DtoConverter();
  private final @NotNull GameDtoImporter importer = new GameDtoImporter();

  /** Wraps an open v2 database; the facade takes over closing it. */
  public Database2CbhFacade(@NotNull Database2Cbh database) {
    this.database = database;
  }

  /** The engine behind the facade. */
  public @NotNull Database2Cbh engine() {
    return database;
  }

  @Override
  public @NotNull String name() {
    return database.name();
  }

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.CB2;
  }

  @Override
  public @NotNull Capabilities capabilities() {
    boolean writable = database.isWritable();
    return new Capabilities(writable, true, writable);
  }

  private void requireWritable() {
    if (!database.isWritable()) {
      throw new UnsupportedOperationException("Database " + name() + " is read-only");
    }
  }

  @Override
  public void close() {
    database.close();
  }

  // ── Games ────────────────────────────────────────────────────────────────

  @Override
  public long gameCount() {
    return database.count();
  }

  @Override
  public @Nullable GameDto getGame(long id, @NotNull GameFetchOptions fetch) {
    try (ReadTransaction txn = new ReadTransaction(database)) {
      if (id < 1 || id > txn.count()) {
        return null;
      }
      return converter.toDto(txn.getGame((int) id), fetch);
    }
  }

  @Override
  public @NotNull ResultPage<GameDto> findGames(
      @NotNull Query query, @NotNull GameFetchOptions fetch) {
    List<FilterCondition> conditions = conditions(query, GameSearch.DEFAULT_FIELD);
    try (ReadTransaction txn = new ReadTransaction(database)) {
      if (conditions.isEmpty() && isIdOrder(query.sort())) {
        return listGamesById(txn, query, fetch);
      }
      List<Integer> ids = GameSearch.find(txn, conditions, query.sort());
      List<GameDto> page =
          slice(ids, query).stream().map(id -> converter.toDto(txn.getGame(id), fetch)).toList();
      return new ResultPage<>(
          page, query.offset(), query.limit(), (long) ids.size(), describe(conditions));
    }
  }

  /** Lists games straight from the header file when the query needs no filtering or sorting. */
  private ResultPage<GameDto> listGamesById(
      ReadTransaction txn, Query query, GameFetchOptions fetch) {
    int total = txn.count();
    boolean descending =
        !query.sort().isNatural()
            && query.sort().keys().getFirst().direction() == Sort.Direction.DESCENDING;
    List<GameDto> page = new ArrayList<>();
    for (int i = query.offset(); i < query.offset() + query.limit() && i < total; i++) {
      int id = descending ? total - i : i + 1;
      page.add(converter.toDto(txn.getGame(id), fetch));
    }
    return new ResultPage<>(page, query.offset(), query.limit(), (long) total, "");
  }

  private static boolean isIdOrder(Sort sort) {
    return sort.isNatural()
        || (sort.keys().size() == 1 && sort.keys().getFirst().field().equalsIgnoreCase("id"));
  }

  @Override
  public @NotNull SearchSchema gameSearchSchema() {
    return GameSearch.schema();
  }

  @Override
  public long addGame(@NotNull GameDto game) {
    requireWritable();
    if ("text".equals(game.type())) {
      return database.addText(importer.toTextModel(game));
    }
    return database.addGame(importer.toGameModel(game));
  }

  @Override
  public void replaceGame(long id, @NotNull GameDto game) {
    requireWritable();
    if (id < 1 || id > database.count()) {
      throw new IllegalArgumentException("No game with id " + id);
    }
    if ("text".equals(game.type())) {
      database.replaceText((int) id, importer.toTextModel(game));
    } else {
      database.replaceGame((int) id, importer.toGameModel(game));
    }
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  @Override
  public long entityCount(@NotNull EntityKind<?> kind) {
    EntitySearch search = EntitySearch.of(kind);
    try (ReadTransaction txn = new ReadTransaction(database)) {
      return txn.sortedCount(search.index());
    }
  }

  @Override
  public <T> @Nullable T getEntity(@NotNull EntityKind<T> kind, long id) {
    EntitySearch search = EntitySearch.of(kind);
    try (ReadTransaction txn = new ReadTransaction(database)) {
      Entity entity = txn.entity(search.type(), id);
      // Entities exist only through the games that refer to them
      if (entity == null || txn.gameCount(id, search.role()) == 0) {
        return null;
      }
      return kind.dtoType().cast(toDto(txn, kind, id, entity));
    }
  }

  @Override
  public <T> @NotNull ResultPage<T> findEntities(
      @NotNull EntityKind<T> kind, @NotNull Query query) {
    EntitySearch search = EntitySearch.of(kind);
    List<FilterCondition> conditions = conditions(query, search.defaultField());
    Predicate<Entity> filter = search.filter(conditions);
    Comparator<EntitySearch.Row> comparator = search.comparator(query.sort());
    try (ReadTransaction txn = new ReadTransaction(database)) {
      List<EntitySearch.Row> rows = new ArrayList<>();
      for (long id : txn.sortedIds(search.index())) {
        Entity entity = txn.entity(search.type(), id);
        if (entity != null && filter.test(entity)) {
          rows.add(new EntitySearch.Row(id, entity, txn.gameCount(id, search.role())));
        }
      }
      if (comparator != null) {
        rows.sort(comparator);
      }
      List<T> page =
          slice(rows, query).stream()
              .map(row -> kind.dtoType().cast(toDto(txn, kind, row.id(), row.entity())))
              .toList();
      return new ResultPage<>(
          page, query.offset(), query.limit(), (long) rows.size(), describe(conditions));
    }
  }

  @Override
  public @NotNull SearchSchema entitySearchSchema(@NotNull EntityKind<?> kind) {
    return EntitySearch.of(kind).schema();
  }

  @Override
  public <T> @NotNull T updateEntity(@NotNull EntityKind<T> kind, long id, @NotNull T dto) {
    requireWritable();
    if (getEntity(kind, id) == null) {
      throw new IllegalArgumentException("No " + kind + " with id " + id);
    }
    EntitySearch search = EntitySearch.of(kind);
    try (WriteTransaction txn = new WriteTransaction(database)) {
      Entity existing = txn.entity(search.type(), id);
      Entity updated =
          switch (existing) {
            case Player p when kind == EntityKind.ANNOTATOR ->
                converter.toAnnotator((AnnotatorDto) dto, p);
            case Player p -> converter.toPlayer((PlayerDto) dto, p);
            case Tournament t -> converter.toTournament((TournamentDto) dto, t);
            case Source s -> converter.toSource((SourceDto) dto);
            case Team t -> converter.toTeam((TeamDto) dto);
            case GameTag g -> converter.toGameTag((GameTagDto) dto, g);
            case null -> throw new IllegalArgumentException("No " + kind + " with id " + id);
          };
      txn.updateEntity(id, updated);
      txn.commit();
    }
    T result = getEntity(kind, id);
    if (result == null) {
      throw new IllegalStateException("The updated " + kind + " " + id + " can't be read");
    }
    return result;
  }

  private Object toDto(DatabaseTransaction txn, EntityKind<?> kind, long id, Entity entity) {
    if (kind == EntityKind.PLAYER) {
      return converter.toDto(txn, id, (Player) entity);
    } else if (kind == EntityKind.ANNOTATOR) {
      return converter.toAnnotatorDto(txn, id, (Player) entity);
    } else if (kind == EntityKind.TOURNAMENT) {
      return converter.toDto(txn, id, (Tournament) entity, true);
    } else if (kind == EntityKind.SOURCE) {
      return converter.toDto(txn, id, (Source) entity, true);
    } else if (kind == EntityKind.TEAM) {
      return converter.toDto(txn, id, (Team) entity, true);
    } else if (kind == EntityKind.GAME_TAG) {
      return converter.toDto(txn, id, (GameTag) entity);
    }
    throw new IllegalArgumentException("Unsupported entity kind: " + kind);
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
}
