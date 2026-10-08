package se.yarin.morphy.service.positions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PositionState;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.GameScanning;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.positions.DatabaseIdentity;
import se.yarin.morphy.positions.GameFactsTable;
import se.yarin.morphy.positions.IndexFiles;
import se.yarin.morphy.positions.IndexMeta;
import se.yarin.morphy.positions.MoveStats;
import se.yarin.morphy.positions.PositionGames;
import se.yarin.morphy.positions.PositionIndex;
import se.yarin.morphy.positions.PositionIndexBuilder;
import se.yarin.morphy.positions.RatedPlayer;
import se.yarin.morphy.service.config.DatabaseConfig;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.GameSearchResponse;
import se.yarin.morphy.service.search.SearchMetadata;

/**
 * The position indexes, as {@code position-indexes.json} defines them, next to {@code
 * databases.json} (and {@code position-indexes.local.json}, out of version control, for those of
 * databases that aren't shared): listing them, searching their games by position, and building
 * them. An index is opened when first searched and kept open; it's opened again when it has been
 * rebuilt. Builds run one at a time, in the background.
 */
@Service
public class PositionsService {
  private static final Logger log = LoggerFactory.getLogger(PositionsService.class);

  /** The fields the games of a position can be sorted by. */
  public static final List<String> SORT_FIELDS =
      List.of(
          "relevance", "id", "playedDate", "playedYear", "whiteElo", "blackElo", "eloAvg", "eloMax");

  /**
   * How much a year of age weighs against rating in a game's relevance: its average rating less
   * this much for every year it was played before the newest game of the index. A game of 2700 a
   * year old comes before one of 2600 played this year. To be tuned.
   */
  static final int RELEVANCE_ELO_PER_YEAR = 50;

  // The age of a game of no known year, in its relevance
  private static final int UNKNOWN_AGE = 100;

  // The players named for a move
  private static final int TOP_PLAYERS = 3;

  private static final int MAX_LIMIT = 1000;

  private final DatabaseService databaseService;

  /** The definitions, by id, in the order of the files. */
  private final Map<String, PositionIndexConfig> definitions;

  /** The open indexes, by index id, with the time their files were built. */
  private final Map<String, OpenIndex> indexes = new ConcurrentHashMap<>();

  private record OpenIndex(PositionIndex index, FileTime built) {}

  /** A build queued or running, or the last one, if it failed. */
  private static final class Build {
    volatile boolean running = true;
    volatile String progress = "Waiting for another build";
    volatile @Nullable String failure;
    CompletableFuture<Void> done = new CompletableFuture<>();
  }

  private final Map<String, Build> builds = new ConcurrentHashMap<>();
  private final ExecutorService builder =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "position-index-build");
            t.setDaemon(true);
            return t;
          });

  /**
   * @param configPath the definitions file; the {@code .local} file next to it is read too. A
   *     missing file defines no indexes
   */
  public PositionsService(
      DatabaseService databaseService,
      @Value("${app.position-indexes.config:test-databases/position-indexes.json}")
          String configPath) {
    this.databaseService = databaseService;
    this.definitions = readDefinitions(configPath);
  }

  private static Map<String, PositionIndexConfig> readDefinitions(String configPath) {
    Map<String, PositionIndexConfig> definitions = new LinkedHashMap<>();
    if (configPath == null || configPath.isBlank()) {
      return definitions;
    }
    File file = new File(configPath).getAbsoluteFile();
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    File local =
        new File(
            file.getParentFile(),
            dot > 0 ? name.substring(0, dot) + ".local" + name.substring(dot) : name + ".local");
    ObjectMapper mapper = new ObjectMapper();
    for (File f : List.of(file, local)) {
      if (!f.isFile()) {
        continue;
      }
      try {
        Map<String, PositionIndexConfig> read =
            mapper.readValue(f, new TypeReference<LinkedHashMap<String, PositionIndexConfig>>() {});
        read.forEach((id, definition) -> definitions.put(id, definition.withId(id)));
        log.info("Loaded {} position index definition(s) from {}", read.size(), f);
      } catch (IOException e) {
        throw new IllegalStateException("Can't read the position indexes in " + f + ": " + e.getMessage(), e);
      }
    }
    return definitions;
  }

  // ── Listing ──────────────────────────────────────────────────────────────

  /** Every index, in the order of the definitions. */
  public List<PositionIndexInfo> list() {
    return definitions.values().stream().map(this::info).toList();
  }

  /**
   * An index.
   *
   * @throws IllegalArgumentException if there is no such index
   */
  public PositionIndexInfo info(@NotNull String indexId) {
    return info(definition(indexId));
  }

  private PositionIndexInfo info(PositionIndexConfig definition) {
    String id = definition.id();
    Build build = builds.get(id);
    String status, message = null;
    Long games = null;
    String builtAt = null;
    IndexMeta meta = null;
    try {
      meta = PositionIndex.readMeta(directory(definition));
      games = meta.games();
      builtAt = meta.builtAt().toString();
    } catch (IOException | RuntimeException e) {
      // No index yet
    }
    if (build != null && build.running) {
      status = "building";
      message = build.progress;
    } else if (build != null && build.failure != null) {
      status = "failed";
      message = build.failure;
    } else if (meta == null) {
      status = "missing";
      message = "The position index " + definition.name() + " hasn't been built";
    } else if (isStale(definition, meta)) {
      status = "stale";
      message = "The position index " + definition.name() + " is out of date";
    } else {
      status = "ready";
    }
    return new PositionIndexInfo(
        id, definition.name(), definition.database(), definition.filterOrAll(), status, message, games, builtAt);
  }

  private PositionIndexConfig definition(String indexId) {
    PositionIndexConfig definition = definitions.get(indexId);
    if (definition == null) {
      throw new IllegalArgumentException("Unknown position index: " + indexId);
    }
    return definition;
  }

  private DatabaseConfig databaseOf(PositionIndexConfig definition) {
    DatabaseConfig config = databaseService.getDatabaseConfig(definition.database());
    if (config == null) {
      throw new IllegalArgumentException(
          "The position index " + definition.id() + " is of an unknown database: " + definition.database());
    }
    return config;
  }

  /** The directory of an index: as defined, or next to its database. */
  private Path directory(PositionIndexConfig definition) {
    return definition.path() != null
        ? Path.of(definition.path())
        : IndexFiles.indexDirectoryOf(Path.of(databaseOf(definition).getPath()), definition.id());
  }

  /** Whether an index was built from another filter, or its database has changed since. */
  private boolean isStale(PositionIndexConfig definition, IndexMeta meta) {
    if (!meta.filter().strip().equals(definition.filterOrAll())) {
      return true;
    }
    DatabaseConfig config = databaseOf(definition);
    return databaseService.read(
        config.getId(),
        db -> {
          try {
            return !meta.database().equals(DatabaseIdentity.of(Path.of(config.getPath()), db.gameCount()));
          } catch (IOException e) {
            return true;
          }
        });
  }

  // ── Searching ────────────────────────────────────────────────────────────

  /**
   * A page of the games of an index that reached a position.
   *
   * @throws IllegalArgumentException if there is no such index, or the position or the sort order
   *     isn't valid
   * @throws PositionIndexUnavailableException if the index is missing or out of date
   */
  public PositionSearchResponse search(
      @NotNull String indexId,
      @NotNull String fen,
      @NotNull String sortBy,
      int offset,
      int limit,
      boolean includeMoves) {
    long startTime = System.currentTimeMillis();
    PositionIndexConfig definition = definition(indexId);
    DatabaseConfig config = databaseOf(definition);
    Position position;
    try {
      position = PositionState.fromFen(fen).position();
    } catch (PgnFormatException e) {
      throw new IllegalArgumentException("Not a valid position: " + fen + " (" + e.getMessage() + ")");
    }
    Order order = Order.parse(sortBy);
    int first = Math.max(0, offset);
    int count = Math.min(Math.max(0, limit), MAX_LIMIT);

    return databaseService.read(
        config.getId(),
        db -> {
          PositionIndex index = indexOf(definition, config, db);
          PositionGames games;
          try (GameScan scan = scanning(db).openScan()) {
            games = index.find(position, scan);
          }
          int[] sorted = order.sort(games.gameIds(), index.facts(), index.meta().recentSince() + 2);
          GameFetchOptions fetch = new GameFetchOptions(includeMoves, false, false);
          List<GameDto> page = new ArrayList<>();
          for (int i = first; i < Math.min(sorted.length, first + count); i++) {
            GameDto game = db.getGame(sorted[i], fetch);
            if (game != null) {
              page.add(game);
            }
          }
          PositionSummary summary =
              first == 0 ? summary(db, fen, position, games, index.meta().recentSince()) : null;
          SearchMetadata metadata =
              new SearchMetadata(null, order.toString(), System.currentTimeMillis() - startTime);
          return new PositionSearchResponse(
              indexId,
              config.getId(),
              summary,
              new GameSearchResponse(page, page.size(), sorted.length, first, count, metadata));
        });
  }

  private static GameScanning scanning(Database db) {
    return db.extension(GameScanning.class)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Database " + db.name() + " can't be searched by position"));
  }

  /** An index, opened if it isn't, or opened again if it has been rebuilt. */
  private PositionIndex indexOf(PositionIndexConfig definition, DatabaseConfig config, Database db) {
    Path dir = directory(definition);
    String build = "build it from the Games pane, or with: morphy positions build \"" + config.getPath() + "\""
        + (definition.filterOrAll().isEmpty() ? "" : " --filter \"" + definition.filterOrAll() + "\"")
        + " --index \"" + dir + "\"";
    FileTime built;
    try {
      built = Files.getLastModifiedTime(dir.resolve("meta.properties"));
    } catch (IOException e) {
      throw new PositionIndexUnavailableException(
          "The position index " + definition.name() + " hasn't been built; " + build);
    }
    OpenIndex open =
        indexes.compute(
            definition.id(),
            (id, current) -> {
              if (current != null && current.built().equals(built)) {
                return current;
              }
              if (current != null) {
                close(current.index());
              }
              try {
                log.info("Opening the position index '{}' in {}", id, dir);
                return new OpenIndex(PositionIndex.open(dir), built);
              } catch (IOException e) {
                throw new PositionIndexUnavailableException(
                    "The position index " + definition.name() + " can't be read; " + build, e);
              }
            });
    try {
      if (open.index().isStale(DatabaseIdentity.of(Path.of(config.getPath()), db.gameCount()), definition.filterOrAll())) {
        throw new PositionIndexUnavailableException(
            "The position index " + definition.name() + " is out of date; " + build);
      }
    } catch (IOException e) {
      throw new PositionIndexUnavailableException("Can't read " + config.getPath(), e);
    }
    return open.index();
  }

  // ── Building ─────────────────────────────────────────────────────────────

  /**
   * Builds an index in the background, after any build before it; does nothing if it's already
   * being built.
   *
   * @return when the build is done; it completes exceptionally if the build fails
   * @throws IllegalArgumentException if there is no such index
   */
  public CompletableFuture<Void> build(@NotNull String indexId) {
    PositionIndexConfig definition = definition(indexId);
    DatabaseConfig config = databaseOf(definition);
    Build build = new Build();
    Build current = builds.putIfAbsent(indexId, build);
    if (current != null) {
      if (current.running) {
        return current.done;
      }
      builds.put(indexId, build);
    }
    builder.submit(
        () -> {
          long start = System.nanoTime();
          try {
            Path dir = directory(definition);
            build.progress = "Starting";
            databaseService.read(
                config.getId(),
                db -> {
                  try (GameScan scan = scanning(db).openScan(definition.filterOrAll())) {
                    new PositionIndexBuilder(message -> build.progress = message)
                        .build(
                            scan,
                            DatabaseIdentity.of(Path.of(config.getPath()), db.gameCount()),
                            definition.filterOrAll(),
                            dir,
                            dir.toAbsolutePath().getParent());
                    return null;
                  } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                  }
                });
            log.info(
                "Built the position index '{}' in {} s", indexId, (System.nanoTime() - start) / 1_000_000_000);
            builds.remove(indexId, build);
            build.running = false;
            build.done.complete(null);
          } catch (RuntimeException e) {
            log.error("Failed to build the position index '{}'", indexId, e);
            build.failure = "The last build failed: " + e.getMessage();
            build.running = false;
            build.done.completeExceptionally(e);
          }
        });
    return build.done;
  }

  private static void close(PositionIndex index) {
    try {
      index.close();
    } catch (IOException e) {
      log.warn("Failed to close the position index in {}", index.directory(), e);
    }
  }

  @PreDestroy
  public void closeAll() {
    builder.shutdownNow();
    indexes.values().forEach(open -> close(open.index()));
    indexes.clear();
  }

  private static PositionSummary summary(
      Database db, String fen, Position position, PositionGames games, int recentSince) {
    int whiteWins = 0, draws = 0, blackWins = 0;
    List<PositionGames.PlayedMove> all = new ArrayList<>(games.moves());
    all.add(games.ended());
    for (PositionGames.PlayedMove m : all) {
      whiteWins += m.stats().whiteWins();
      draws += m.stats().draws();
      blackWins += m.stats().blackWins();
    }
    List<PositionMove> moves =
        games.moves().stream()
            .map(
                m -> {
                  MoveStats s = m.stats();
                  return new PositionMove(
                      m.move().toSAN(),
                      s.games(),
                      s.whiteWins(),
                      s.draws(),
                      s.blackWins(),
                      s.recentGames(),
                      s.lastYear() > 0 ? s.lastYear() : null,
                      s.eloCount() > 0 ? s.averageElo() : null,
                      players(db, s.topPlayers()));
                })
            .toList();
    return new PositionSummary(
        fen, games.games(), whiteWins, draws, blackWins, recentSince, moves);
  }

  private static List<PositionPlayer> players(Database db, List<RatedPlayer> players) {
    List<PositionPlayer> named = new ArrayList<>();
    for (RatedPlayer p : players) {
      if (named.size() == TOP_PLAYERS) {
        break;
      }
      PlayerDto player = db.getEntity(EntityKind.PLAYER, p.playerId());
      if (player == null || player.lastName() == null || player.lastName().isEmpty()) {
        continue;
      }
      String name =
          player.firstName() == null || player.firstName().isEmpty()
              ? player.lastName()
              : player.lastName() + ", " + player.firstName();
      named.add(new PositionPlayer(name, p.elo() > 0 ? p.elo() : null));
    }
    return named;
  }

  /** How the games are sorted: by one of the {@link #SORT_FIELDS}, then by id. */
  private record Order(String field, boolean descending) {
    static Order parse(String sortBy) {
      String spec = sortBy.isBlank() ? "-relevance" : sortBy.trim();
      boolean descending = spec.startsWith("-");
      String field = spec.startsWith("-") || spec.startsWith("+") ? spec.substring(1) : spec;
      if (!SORT_FIELDS.contains(field)) {
        throw new IllegalArgumentException(
            "The games of a position can't be sorted by '" + field + "', only by " + SORT_FIELDS);
      }
      return new Order(field, descending);
    }

    /**
     * The games in this order.
     *
     * @param newestYear the year of the index's newest game, which the age in a game's relevance
     *     is counted from
     */
    int[] sort(int[] gameIds, GameFactsTable facts, int newestYear) {
      if (field.equals("id")) {
        if (!descending) {
          return gameIds;
        }
        int[] reversed = new int[gameIds.length];
        for (int i = 0; i < gameIds.length; i++) {
          reversed[i] = gameIds[gameIds.length - 1 - i];
        }
        return reversed;
      }
      // The value above the id, so a primitive sort orders by value, then id
      long[] keys = new long[gameIds.length];
      for (int i = 0; i < gameIds.length; i++) {
        long value = value(facts, gameIds[i], newestYear);
        keys[i] = ((descending ? (1L << 30) - value : value) << 32) | gameIds[i];
      }
      Arrays.sort(keys);
      int[] sorted = new int[keys.length];
      for (int i = 0; i < keys.length; i++) {
        sorted[i] = (int) keys[i];
      }
      return sorted;
    }

    /** The value sorted by, at least 0 and below 2^30. */
    private long value(GameFactsTable facts, int id, int newestYear) {
      int white = facts.whiteElo(id), black = facts.blackElo(id);
      return switch (field) {
        case "relevance" -> relevance(facts, id, newestYear);
        case "playedDate" -> facts.sortableDate(id);
        case "playedYear" -> facts.year(id);
        case "whiteElo" -> white;
        case "blackElo" -> black;
        case "eloAvg" -> white > 0 && black > 0 ? (white + black) / 2 : Math.max(white, black);
        case "eloMax" -> Math.max(white, black);
        default -> id;
      };
    }

    @Override
    public String toString() {
      return (descending ? "-" : "+") + field;
    }
  }

  /**
   * How relevant a game is, the higher the more: the average rating of its players (or the one
   * rating, if only one is rated) less {@link #RELEVANCE_ELO_PER_YEAR} for each year it was played
   * before the newest year; offset to be above 0.
   */
  static long relevance(GameFactsTable facts, int id, int newestYear) {
    int white = facts.whiteElo(id), black = facts.blackElo(id);
    int rating = white > 0 && black > 0 ? (white + black) / 2 : Math.max(white, black);
    int year = facts.year(id);
    int age = year > 0 ? Math.max(0, newestYear - year) : UNKNOWN_AGE;
    return (1 << 20) + rating - (long) RELEVANCE_ELO_PER_YEAR * Math.min(age, 1000);
  }
}
