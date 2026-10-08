package se.yarin.morphy.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.pgn.PositionState;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.GameScanning;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.positions.DatabaseIdentity;
import se.yarin.morphy.positions.IndexFiles;
import se.yarin.morphy.positions.IndexMeta;
import se.yarin.morphy.positions.MoveStats;
import se.yarin.morphy.positions.PositionGames;
import se.yarin.morphy.positions.PositionIndex;
import se.yarin.morphy.positions.PositionScanner;
import se.yarin.morphy.positions.PositionIndexBuilder;
import se.yarin.morphy.positions.RatedPlayer;

@CommandLine.Command(
    name = "positions",
    description = "Builds and searches a database's position index",
    mixinStandardHelpOptions = true,
    subcommands = {
      Positions.Build.class,
      Positions.Update.class,
      Positions.Compact.class,
      Positions.Lookup.class
    })
class Positions implements Runnable {

  @Override
  public void run() {
    System.out.println("A subcommand must be specified; use --help");
  }

  private static GameScanning scanning(Database db) {
    return db.extension(GameScanning.class)
        .orElseThrow(
            () -> new IllegalArgumentException(db.name() + " can't be indexed by position"));
  }

  @CommandLine.Command(
      name = "build",
      description =
          "Builds a position index of a database, of all its games or those matching a filter",
      mixinStandardHelpOptions = true)
  static class Build implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The database")
    private File file;

    @CommandLine.Option(
        names = "--filter",
        description =
            "Index only the games matching this filter, in the game search's language, e.g."
                + " \"tournament.time:normal rating:2300..,mode=both\"")
    private String filter = "";

    @CommandLine.Option(
        names = "--index",
        description = "The index directory (default: next to the database, <name>.positions)")
    private Path indexDir;

    @Override
    public Integer call() throws Exception {
      Locale.setDefault(Locale.US);
      Path databaseFile = file.toPath();
      Path indexDir = this.indexDir != null ? this.indexDir : IndexFiles.indexDirectoryOf(databaseFile);
      long start = System.nanoTime();
      try (Database db = Databases.open(file, AccessMode.READ_ONLY);
          GameScan scan = scanning(db).openScan(filter)) {
        IndexMeta meta =
            new PositionIndexBuilder(System.out::println)
                .build(
                    scan,
                    DatabaseIdentity.of(databaseFile, db.gameCount()),
                    filter,
                    indexDir);
        System.out.printf(
            "Indexed %,d games: %,d positions several games reached and %,d one game reached, in"
                + " %.0f s; %,d MB in %s%n",
            meta.games(),
            meta.sharedPositions(),
            meta.singlePositions(),
            (System.nanoTime() - start) / 1e9,
            sizeOf(indexDir) / 1_000_000,
            indexDir);
      }
      return 0;
    }
  }

  /** The bytes of the files in a directory and those below it. */
  private static long sizeOf(Path dir) throws IOException {
    try (var files = Files.walk(dir)) {
      return files.filter(Files::isRegularFile).mapToLong(f -> f.toFile().length()).sum();
    }
  }

  @CommandLine.Command(
      name = "update",
      description =
          "Adds the games added to a database since its position index was built or updated, as a"
              + " segment of their own; compacts the index when it has grown too many",
      mixinStandardHelpOptions = true)
  static class Update implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The database")
    private File file;

    @CommandLine.Option(
        names = "--index",
        description = "The index directory (default: next to the database, <name>.positions)")
    private Path indexDir;

    @CommandLine.Option(
        names = "--changed",
        split = ",",
        description = "Games changed or deleted since, by id, to index again")
    private int[] changed = new int[0];

    @Override
    public Integer call() throws Exception {
      Locale.setDefault(Locale.US);
      Path databaseFile = file.toPath();
      Path indexDir = this.indexDir != null ? this.indexDir : IndexFiles.indexDirectoryOf(databaseFile);
      long start = System.nanoTime();
      String filter = PositionIndex.readMeta(indexDir).filter();
      try (Database db = Databases.open(file, AccessMode.READ_ONLY);
          GameScan scan = scanning(db).openScan(filter)) {
        IndexMeta meta =
            new PositionIndexBuilder(System.out::println)
                .update(scan, DatabaseIdentity.of(databaseFile, db.gameCount()), indexDir, changed);
        System.out.printf(
            "%,d games in %d segment(s), updated in %.1f s; %,d MB%n",
            meta.games(),
            meta.segments().size(),
            (System.nanoTime() - start) / 1e9,
            sizeOf(indexDir) / 1_000_000);
      }
      return 0;
    }
  }

  @CommandLine.Command(
      name = "compact",
      description = "Merges the segments of a database's position index into one",
      mixinStandardHelpOptions = true)
  static class Compact implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The database")
    private File file;

    @CommandLine.Option(
        names = "--index",
        description = "The index directory (default: next to the database, <name>.positions)")
    private Path indexDir;

    @Override
    public Integer call() throws Exception {
      Locale.setDefault(Locale.US);
      Path indexDir =
          this.indexDir != null ? this.indexDir : IndexFiles.indexDirectoryOf(file.toPath());
      long start = System.nanoTime();
      IndexMeta meta = new PositionIndexBuilder(System.out::println).compact(indexDir);
      System.out.printf(
          "%,d games in one segment, compacted in %.1f s; %,d MB%n",
          meta.games(), (System.nanoTime() - start) / 1e9, sizeOf(indexDir) / 1_000_000);
      return 0;
    }
  }

  @CommandLine.Command(
      name = "lookup",
      description = "Shows the moves played from a position in a database's games",
      mixinStandardHelpOptions = true)
  static class Lookup implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The database")
    private File file;

    @CommandLine.Parameters(index = "1", description = "The position, as FEN")
    private String fen;

    @CommandLine.Option(
        names = "--index",
        description = "The index directory (default: next to the database, <name>.positions)")
    private Path indexDir;

    @CommandLine.Option(
        names = "--scan",
        description = "Play through every game instead of using an index")
    private boolean scan;

    @CommandLine.Option(
        names = "--filter",
        description = "With --scan, only the games matching this game search filter")
    private String filter = "";

    @Override
    public Integer call() throws Exception {
      Locale.setDefault(Locale.US);
      Position position = PositionState.fromFen(fen).position();
      boolean white = position.playerToMove() == Player.WHITE;
      try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
        PositionGames games = scan ? scanned(db, position) : lookedUp(db, position);
        if (games == null) {
          return 1;
        }
        System.out.printf("%-8s %9s %6s %5s %5s  %s%n", "Move", "Games", "Score", "Avg", "Last", "Top players");
        for (PositionGames.PlayedMove move : games.moves()) {
          MoveStats s = move.stats();
          int decided = s.whiteWins() + s.draws() + s.blackWins();
          double score =
              decided == 0 ? 0 : ((white ? s.whiteWins() : s.blackWins()) + s.draws() / 2.0) / decided;
          System.out.printf(
              "%-8s %,9d %5.0f%% %5s %5s  %s%n",
              move.move().toSAN(),
              s.games(),
              100 * score,
              s.eloCount() > 0 ? String.valueOf(s.averageElo()) : "",
              s.lastYear() > 0 ? String.valueOf(s.lastYear()) : "",
              names(db, s.topPlayers()));
        }
        if (games.ended().gameIds().length > 0) {
          System.out.printf("%-8s %,9d%n", "(ended)", games.ended().gameIds().length);
        }
      }
      return 0;
    }

    private PositionGames scanned(Database db, Position position) {
      long start = System.nanoTime();
      PositionGames games;
      try (GameScan scan = scanning(db).openScan(filter)) {
        games = PositionScanner.find(position, scan);
      }
      System.out.printf(
          "%,d games (every game played through in %.1f s)%n",
          games.games(), (System.nanoTime() - start) / 1e9);
      return games;
    }

    private PositionGames lookedUp(Database db, Position position) throws IOException {
      long opened = System.nanoTime();
      try (PositionIndex index =
          PositionIndex.open(
              indexDir != null ? indexDir : IndexFiles.indexDirectoryOf(file.toPath()))) {
        if (index.isStale(DatabaseIdentity.of(file.toPath(), db.gameCount()), index.meta().filter())) {
          System.out.println("The index is out of date; build it again, or use --scan");
          return null;
        }
        long start = System.nanoTime();
        PositionGames games = index.find(position);
        long looked = System.nanoTime();
        System.out.printf(
            "%,d games (index opened in %.0f ms, looked up in %.1f ms)%n",
            games.games(), (start - opened) / 1e6, (looked - start) / 1e6);
        return games;
      }
    }

    private static String names(Database db, List<RatedPlayer> players) {
      return players.stream()
          .limit(3)
          .map(
              p -> {
                PlayerDto dto = db.getEntity(EntityKind.PLAYER, p.playerId());
                return dto == null ? "?" : dto.lastName();
              })
          .collect(Collectors.joining(", "));
    }
  }
}
