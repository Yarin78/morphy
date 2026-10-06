package se.yarin.morphy.service.positions;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import se.yarin.morphy.positions.MoveStats;
import se.yarin.morphy.positions.PositionGames;
import se.yarin.morphy.positions.PositionIndex;
import se.yarin.morphy.positions.RatedPlayer;
import se.yarin.morphy.service.config.DatabaseConfig;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.GameSearchResponse;
import se.yarin.morphy.service.search.SearchMetadata;

/**
 * Searches the games of reference databases by position, through their position indexes. An
 * index is opened when first needed and kept open; it's opened again when it has been rebuilt.
 */
@Service
public class PositionsService {
  private static final Logger log = LoggerFactory.getLogger(PositionsService.class);

  /** The fields the games of a position can be sorted by. */
  public static final List<String> SORT_FIELDS =
      List.of("id", "playedDate", "playedYear", "whiteElo", "blackElo", "eloAvg", "eloMax");

  // The players named for a move
  private static final int TOP_PLAYERS = 3;

  private static final int MAX_LIMIT = 1000;

  private final DatabaseService databaseService;

  /** The open indexes, by database id, with the time their files were built. */
  private final Map<String, OpenIndex> indexes = new ConcurrentHashMap<>();

  private record OpenIndex(PositionIndex index, FileTime built) {}

  public PositionsService(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  /**
   * A page of the games of a reference database that reached a position.
   *
   * @throws IllegalArgumentException if the database isn't a reference database, or the position
   *     or the sort order isn't valid
   * @throws PositionIndexUnavailableException if the database's index is missing or out of date
   */
  public PositionSearchResponse search(
      @NotNull String databaseId,
      @NotNull String fen,
      @NotNull String sortBy,
      int offset,
      int limit,
      boolean includeMoves) {
    long startTime = System.currentTimeMillis();
    DatabaseConfig config = databaseService.getDatabaseConfig(databaseId);
    if (config == null) {
      throw new IllegalArgumentException("Unknown database ID: " + databaseId);
    }
    if (config.getReferenceName() == null) {
      throw new IllegalArgumentException(
          "Database '" + databaseId + "' is not a reference database; it can't be searched by position");
    }
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
        databaseId,
        db -> {
          PositionIndex index = indexOf(config, db);
          PositionGames games;
          try (GameScan scan = scanning(db).openScan()) {
            games = PositionGames.find(index, scan, position);
          }
          int[] sorted = order.sort(games.gameIds(), index.facts());
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

  /** The database's index, opened if it isn't, or opened again if it has been rebuilt. */
  private PositionIndex indexOf(DatabaseConfig config, Database db) {
    Path databaseFile = Path.of(config.getPath());
    Path dir =
        config.getPositionIndex() != null
            ? Path.of(config.getPositionIndex())
            : IndexFiles.indexDirectoryOf(databaseFile);
    String build = "build it with: morphy positions build \"" + databaseFile + "\"";
    FileTime built;
    try {
      built = Files.getLastModifiedTime(dir.resolve("meta.properties"));
    } catch (IOException e) {
      throw new PositionIndexUnavailableException(
          "The reference database " + config.getReferenceName() + " has no position index; " + build);
    }
    OpenIndex open =
        indexes.compute(
            config.getId(),
            (id, current) -> {
              if (current != null && current.built().equals(built)) {
                return current;
              }
              if (current != null) {
                close(current.index());
              }
              try {
                log.info("Opening the position index of '{}' in {}", id, dir);
                return new OpenIndex(PositionIndex.open(dir), built);
              } catch (IOException e) {
                throw new PositionIndexUnavailableException(
                    "The position index of " + config.getReferenceName() + " can't be read; " + build,
                    e);
              }
            });
    try {
      if (open.index().isStale(DatabaseIdentity.of(databaseFile, db.gameCount()))) {
        throw new PositionIndexUnavailableException(
            "The position index of " + config.getReferenceName() + " is out of date; " + build);
      }
    } catch (IOException e) {
      throw new PositionIndexUnavailableException("Can't read " + databaseFile, e);
    }
    return open.index();
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
      String spec = sortBy.isBlank() ? "+id" : sortBy.trim();
      boolean descending = spec.startsWith("-");
      String field = spec.startsWith("-") || spec.startsWith("+") ? spec.substring(1) : spec;
      if (!SORT_FIELDS.contains(field)) {
        throw new IllegalArgumentException(
            "The games of a position can't be sorted by '" + field + "', only by " + SORT_FIELDS);
      }
      return new Order(field, descending);
    }

    /** The games in this order. */
    int[] sort(int[] gameIds, GameFactsTable facts) {
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
        long value = value(facts, gameIds[i]);
        keys[i] = ((descending ? (1L << 30) - value : value) << 32) | gameIds[i];
      }
      Arrays.sort(keys);
      int[] sorted = new int[keys.length];
      for (int i = 0; i < keys.length; i++) {
        sorted[i] = (int) keys[i];
      }
      return sorted;
    }

    private long value(GameFactsTable facts, int id) {
      int white = facts.whiteElo(id), black = facts.blackElo(id);
      return switch (field) {
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
}
