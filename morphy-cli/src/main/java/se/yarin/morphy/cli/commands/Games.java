package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.cli.games.*;
import se.yarin.morphy.cli.columns.*;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;
import se.yarin.morphy.cli.queries.QueryResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
public class Games extends BaseCommand implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(Games.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description =
          "Filter expression (e.g., \"result:1-0 AND player.name:Carlsen AND date:2020..\")")
  private String filterExpression;

  @CommandLine.Option(names = "--limit", description = "Max number of games to output")
  private int limit = 0;

  @CommandLine.Option(names = "--id", description = "The id of a game to get")
  private int[] ids;

  @CommandLine.Option(
      names = "--count-all",
      description = "Count all hits, even beyond the limit (if specified)")
  private boolean countAll = false;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output database (.cbh or .pgn)")
  private String output;

  @CommandLine.Option(
      names = "--pgn-headers",
      description =
          "PGN header mode: 'all' (default) includes optional headers, 'seven-tag-roster' exports only mandatory headers")
  private String pgnHeaders = "all";

  @CommandLine.Option(
      names = "--pgn-annotations",
      description =
          "PGN annotation mode: 'all' (default) exports all ChessBase annotations, 'standard' exports only standard PGN annotations")
  private String pgnAnnotations = "all";

  @CommandLine.Option(
      names = "--pgn-comment-language",
      description =
          "Filter text comments by language. Comma-separated IOC codes (e.g., ENG,GER,FRA). Use 'ALL' for language-neutral comments. Omit to include all.")
  private String pgnCommentLanguage;

  @CommandLine.Option(names = "--stats", description = "Show statistics about all matching games")
  private boolean stats;

  @CommandLine.Option(
      names = "--overwrite",
      description = "If true, overwrite the output database if it already exists.")
  private boolean overwrite;

  @CommandLine.Option(
      names = "--columns",
      description =
          "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
  private String columns;

  @CommandLine.Option(names = "--raw-col-cbh", description = "Show binary CBH data (debug)")
  private String[] rawCbhColumns;

  @CommandLine.Option(names = "--raw-col-cbj", description = "Show binary CBJ data (debug)")
  private String[] rawCbjColumns;

  @CommandLine.Option(names = "--raw-col-cbg", description = "Show binary CBG data (debug)")
  private boolean rawCbgColumns;

  @CommandLine.Option(names = "--raw-col-cba", description = "Show binary CBA data (debug)")
  private boolean rawCbaColumns;

  @CommandLine.Option(names = "--raw-col-cbb", description = "Show binary CBB data (debug)")
  private String[] rawCbbColumns;

  @CommandLine.Option(
      names = "--raw-cbh",
      description = "Raw filter expression in CBH data (debug)")
  private String[] rawCbhFilter;

  @CommandLine.Option(
      names = "--raw-cbj",
      description = "Raw filter expression in CBJ data (debug)")
  private String[] rawCbjFilter;

  private final GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();

  @Override
  public Integer call() throws IOException {
    var numDatabaseErrors = new AtomicInteger(0);

    setupGlobalOptions();

    GameConsumer gameConsumer = createGameConsumer();
    gameConsumer.init();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                // Speeds up performance quite a lot, and we should be fairly certain that the moves
                // in the CBH databases are valid
                db.moveRepository().setValidateDecodedMoves(false);

                try (var txn = new DatabaseReadTransaction(db)) {
                  GameQuery gameQuery = null;
                  try {
                    gameQuery = createGameQuery(db);
                  } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                  }
                  assert gameQuery != null;

                  QueryContext qc = new QueryContext(txn, false);
                  List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
                  QueryOperator<Game> bestPlan = db.queryPlanner().selectBestQueryPlan(plans);

                  QueryResult<Game> result;
                  if (!(gameConsumer instanceof StdoutGamesSummary)) {
                    try (ProgressBar pb = new ProgressBar("Games", db.count())) {
                      result = QueryAdapter.execute(bestPlan, limit, countAll, gameConsumer, game -> pb.stepTo(game.id()));
                    }
                  } else {
                    result = QueryAdapter.execute(bestPlan, limit, countAll, gameConsumer, null);
                  }

                  gameConsumer.searchDone(result);
                }

                if (showInstrumentation()) {
                  db.context().instrumentation().show();
                }
              } catch (IOException e) {
                System.err.println("IO error when processing " + file);
                numDatabaseErrors.incrementAndGet();
                if (verboseLevel() > 0) {
                  e.printStackTrace();
                }
              } catch (RuntimeException e) {
                System.err.println(
                    "Unexpected error when processing " + file + ": " + e.getMessage());
                numDatabaseErrors.incrementAndGet();
                if (verboseLevel() > 0) {
                  e.printStackTrace();
                }
              }
            });

    gameConsumer.finish();
    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }

  public GameQuery createGameQuery(Database db) {
    // Build query from filter expression
    GameQuery baseQuery = gameQueryBuilder.buildQuery(db, filterExpression);

    // Add raw debug filters on top of the parsed query
    if (rawCbhFilter == null && rawCbjFilter == null) {
      return baseQuery;
    }

    ArrayList<GameFilter> gameFilters = new ArrayList<>(baseQuery.gameFilters());
    if (rawCbhFilter != null) {
      for (String filter : rawCbhFilter) {
        gameFilters.add(new RawGameHeaderFilter(filter));
      }
    }
    if (rawCbjFilter != null) {
      for (String filter : rawCbjFilter) {
        gameFilters.add(new RawExtendedHeaderFilter(filter));
      }
    }

    return new GameQuery(db, gameFilters, new ArrayList<>(baseQuery.entityJoins()));
  }

  public GameConsumer createGameConsumer() throws IOException {
    GameConsumer gameConsumer;
    if (output == null) {
      if (!stats) {
        if (columns == null) {
          columns = StdoutGamesSummary.DEFAULT_COLUMNS;
        }
        List<GameColumn> parsedColumns = StdoutGamesSummary.parseColumns(this.columns);
        if (rawCbhColumns != null) {
          for (String rawCbhColumn : rawCbhColumns) {
            String[] parts = rawCbhColumn.split(",");
            if (parts.length != 2) {
              throw new IllegalArgumentException("Invalid format of raw CBH column");
            }
            parsedColumns.add(
                new RawHeaderColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
          }
        }
        if (rawCbjColumns != null) {
          for (String rawCbjColumn : rawCbjColumns) {
            String[] parts = rawCbjColumn.split(",");
            if (parts.length != 2) {
              throw new IllegalArgumentException("Invalid format of raw CBJ column");
            }
            parsedColumns.add(
                new RawExtendedHeaderColumn(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
          }
        }
        if (rawCbgColumns) {
          parsedColumns.add(new RawMovesColumn());
        }
        if (rawCbaColumns) {
          parsedColumns.add(new RawAnnotationsColumn());
        }
        if (rawCbbColumns != null) {
          for (String rawCbjColumn : rawCbbColumns) {
            String[] parts = rawCbjColumn.split(",");
            if (parts.length != 2) {
              throw new IllegalArgumentException("Invalid format of raw CBB column");
            }
            parsedColumns.add(
                new RawCBBColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
          }
        }

        gameConsumer = new StdoutGamesSummary(countAll, parsedColumns);
        if (limit == 0) {
          limit = 50;
        }
      } else {
        gameConsumer = new StatsGameConsumer();
      }
    } else if (output.endsWith(".cbh")) {
      File file = new File(output);
      if (!overwrite && file.exists()) {
        throw new FileAlreadyExistsException(output);
      }
      if (file.exists()) {
        Database.delete(file);
      }
      gameConsumer = new DatabaseBuilder(file);
    } else if (output.endsWith(".pgn")) {
      File file = new File(output);
      if (!overwrite && file.exists()) {
        throw new FileAlreadyExistsException(output);
      }
      if (file.exists()) {
        // TODO: A pgn database may have additional index files that should be deleted as well
        file.delete();
      }

      // Validate and parse --pgn-headers
      boolean includeOptionalHeaders = pgnHeaders.equalsIgnoreCase("all");
      if (!pgnHeaders.equalsIgnoreCase("all")
          && !pgnHeaders.equalsIgnoreCase("seven-tag-roster")) {
        throw new IllegalArgumentException("--pgn-headers must be 'all' or 'seven-tag-roster'");
      }

      // Validate and parse --pgn-annotations
      boolean standardAnnotationsOnly = pgnAnnotations.equalsIgnoreCase("standard");
      if (!pgnAnnotations.equalsIgnoreCase("all")
          && !pgnAnnotations.equalsIgnoreCase("standard")) {
        throw new IllegalArgumentException("--pgn-annotations must be 'all' or 'standard'");
      }

      // Validate and parse --pgn-comment-language
      Set<Nation> commentLanguageFilter = null;
      if (pgnCommentLanguage != null && !pgnCommentLanguage.isEmpty()) {
        commentLanguageFilter = new HashSet<>();
        String[] languageCodes = pgnCommentLanguage.split(",");
        for (String code : languageCodes) {
          String trimmedCode = code.trim().toUpperCase();
          Nation nation;
          if (trimmedCode.equals("ALL")) {
            nation = Nation.NONE;
          } else {
            nation = Nation.fromIOC(trimmedCode);
            if (nation == Nation.NONE) {
              throw new IllegalArgumentException(
                  "Invalid IOC language code: "
                      + code.trim()
                      + ". Examples: ENG, GER, FRA, or ALL for language-neutral");
            }
          }
          commentLanguageFilter.add(nation);
        }
      }

      gameConsumer =
          new PgnDatabaseBuilder(
              file, includeOptionalHeaders, standardAnnotationsOnly, commentLanguageFilter);
    } else {
      throw new IllegalArgumentException("Unknown output format: " + output);
    }
    return gameConsumer;
  }
}
