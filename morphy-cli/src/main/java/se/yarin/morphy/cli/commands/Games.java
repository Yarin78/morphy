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
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.MultiPlayerNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;
import se.yarin.morphy.cli.queries.QueryResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
public class Games extends BaseCommand implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(Games.class);

  @CommandLine.Option(names = "--limit", description = "Max number of games to output")
  private int limit = 0;

  @CommandLine.Option(names = "--id", description = "The id of a game to get")
  private int[] ids;

  @CommandLine.Option(
      names = "--count-all",
      description = "Count all hits, even beyond the limit (if specified)")
  private boolean countAll = false;

  @CommandLine.Option(
      names = "--player",
      description = "Show only games with this player (any color)")
  private String[] players;

  @CommandLine.Option(
      names = "--result",
      description = "Show only games with this result (1-0, 0-1, draw, win, loss etc)")
  private String result;

  @CommandLine.Option(names = "--date", description = "Date range, e.g. '2015-10-' or '1960-1970'")
  private String dateRange;

  @CommandLine.Option(
      names = "--team",
      description = "Show only games where one of the players played for this team")
  private String team;

  @CommandLine.Option(
      names = "--game-tag",
      description = "Show only games with a game tag matching this prefix")
  private String gameTag;

  @CommandLine.Option(names = "--tournament", description = "Show only games in this tournament")
  private String tournament;

  @CommandLine.Option(
      names = "--tournament-time",
      description = "Show only games with this type of time control (normal, rapid, blitz, corr)")
  private String tournamentTimeControl;

  @CommandLine.Option(
      names = "--tournament-type",
      description = "Show only games in this type of tournament (tourn, swiss, match etc)")
  private String tournamentType;

  @CommandLine.Option(
      names = "--tournament-place",
      description = "Show only games from this tournament place")
  private String tournamentPlace;

  @CommandLine.Option(
      names = "--rating",
      description = "Rating range required for both players, e.g. 2700- or 2000-2200")
  private String ratingRangeBoth;

  @CommandLine.Option(
      names = "--rating.any",
      description = "Rating range required for at least one player, e.g. 2700- or 2000-2200")
  private String ratingRangeAny;

  @CommandLine.Option(
      names = "--setup-position",
      description = "Show only games that has a setup position (does not start at move 1)")
  private boolean setupPosition;

  @CommandLine.Option(
      names = "--start-position",
      description = "Show only games that starts at the start position (move 1)")
  private boolean startPosition;

  @CommandLine.Option(names = "--game", description = "Show only chess games (no guiding texts)")
  private boolean game;

  @CommandLine.Option(names = "--text", description = "Show only guiding texts")
  private boolean guidingText;

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
                    QueryContext qc = new QueryContext(txn, false);
                    gameQuery = createGameQuery(db, qc);
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

  public GameQuery createGameQuery(Database db, QueryContext qc) {
    ArrayList<GameFilter> gameFilters = new ArrayList<>();
    ArrayList<GameEntityJoin<?>> entityJoins = new ArrayList<>();

    // ID filter - Note: ManualFilter will be needed but we need to get GameHeaders first
    // For now, we'll handle this differently if IDs are specified
    if (ids != null && ids.length > 0) {
      // Create a filter that will only match these IDs
      // We'll need to implement this as a custom filter or handle differently
      throw new IllegalArgumentException("ID filtering not yet supported in new query system");
    }

    // Position type filters
    // Note: Setup/start position filters require loading game models which is expensive
    // The new query system doesn't support these yet
    if (setupPosition || startPosition) {
      throw new IllegalArgumentException("Setup/start position filtering not yet supported in new query system");
    }

    // Game vs text filter
    if (game) {
      gameFilters.add(new IsGameFilter());
    }

    if (guidingText) {
      gameFilters.add(new TextStorageFilter());
    }

    // Player searches
    if (players != null) {
      for (String player : players) {
        EntityFilter<Player> playerFilter;
        if (!player.contains("|")) {
          playerFilter = new PlayerNameFilter(player, true, false);
        } else {
          List<String> playerNames = Arrays.stream(player.split("\\|"))
              .map(String::trim)
              .collect(Collectors.toList());
          playerFilter = new MultiPlayerNameFilter(playerNames, true, false);
        }

        // Execute player query to get matching player IDs
        EntityQuery<Player> playerQuery = new EntityQuery<>(db, se.yarin.morphy.entities.EntityType.PLAYER, List.<EntityFilter<Player>>of(playerFilter));
        List<QueryOperator<Player>> playerPlans = db.queryPlanner().getEntityQueryPlans(qc, playerQuery, false);
        QueryOperator<Player> bestPlayerPlan = db.queryPlanner().selectBestQueryPlan(playerPlans);
        List<Integer> playerIds = bestPlayerPlan.stream()
            .map(qd -> qd.id())
            .collect(Collectors.toList());

        if (playerIds.isEmpty()) {
          // No matching players found - this will match no games
          // We can't easily create an "impossible" filter, so we'll just skip this
          continue;
        } else {
          // Add player filter with join condition
          GameEntityJoinCondition matchCondition = GameEntityJoinCondition.ANY;
          if ("win".equals(result)) {
            matchCondition = GameEntityJoinCondition.WINNER;
          } else if ("loss".equals(result)) {
            matchCondition = GameEntityJoinCondition.LOSER;
          }
          int[] playerIdArray = playerIds.stream().mapToInt(Integer::intValue).toArray();
          gameFilters.add(new PlayerFilter(playerIdArray, matchCondition));
        }
      }
    }

    // Result filter
    if (result != null) {
      if (result.equals("win") || result.equals("loss")) {
        if (players == null) {
          throw new IllegalArgumentException(
              "A player search is needed when filtering on 'wins' or 'loss' results");
        }
        // Already taken care of above
      } else {
        gameFilters.add(new ResultsFilter(result));
      }
    }

    // Date range filter
    if (dateRange != null) {
      gameFilters.add(new DateRangeFilter(dateRange));
    }

    // Rating filters
    if (ratingRangeBoth != null) {
      gameFilters.add(new RatingRangeFilter(ratingRangeBoth, RatingRangeFilter.RatingColor.BOTH));
    }

    if (ratingRangeAny != null) {
      gameFilters.add(new RatingRangeFilter(ratingRangeAny, RatingRangeFilter.RatingColor.ANY));
    }

    // Team filter
    if (team != null) {
      se.yarin.morphy.entities.filters.TeamTitleFilter teamFilter =
          new se.yarin.morphy.entities.filters.TeamTitleFilter(team, true, false);
      EntityQuery<se.yarin.morphy.entities.Team> teamQuery =
          new EntityQuery<>(db, se.yarin.morphy.entities.EntityType.TEAM, List.<EntityFilter<se.yarin.morphy.entities.Team>>of(teamFilter));
      List<QueryOperator<se.yarin.morphy.entities.Team>> teamPlans = db.queryPlanner().getEntityQueryPlans(qc, teamQuery, false);
      QueryOperator<se.yarin.morphy.entities.Team> bestTeamPlan = db.queryPlanner().selectBestQueryPlan(teamPlans);
      List<Integer> teamIds = bestTeamPlan.stream()
          .map(qd -> qd.id())
          .collect(Collectors.toList());

      if (!teamIds.isEmpty()) {
        int[] teamIdArray = teamIds.stream().mapToInt(Integer::intValue).toArray();
        gameFilters.add(new TeamFilter(teamIdArray, null));
      }
    }

    // Game tag filter
    if (gameTag != null) {
      se.yarin.morphy.entities.filters.GameTagTitleFilter gameTagFilter =
          new se.yarin.morphy.entities.filters.GameTagTitleFilter(gameTag, true, false);
      EntityQuery<se.yarin.morphy.entities.GameTag> gameTagQuery =
          new EntityQuery<>(db, se.yarin.morphy.entities.EntityType.GAME_TAG, List.<EntityFilter<se.yarin.morphy.entities.GameTag>>of(gameTagFilter));
      List<QueryOperator<se.yarin.morphy.entities.GameTag>> gameTagPlans = db.queryPlanner().getEntityQueryPlans(qc, gameTagQuery, false);
      QueryOperator<se.yarin.morphy.entities.GameTag> bestGameTagPlan = db.queryPlanner().selectBestQueryPlan(gameTagPlans);
      List<Integer> gameTagIds = bestGameTagPlan.stream()
          .map(qd -> qd.id())
          .collect(Collectors.toList());

      if (!gameTagIds.isEmpty()) {
        int[] gameTagIdArray = gameTagIds.stream().mapToInt(Integer::intValue).toArray();
        gameFilters.add(new GameTagFilter(gameTagIdArray));
      }
    }

    // Tournament filters
    List<se.yarin.morphy.entities.filters.EntityFilter<Tournament>> tournamentFilters = new ArrayList<>();
    if (tournament != null) {
      tournamentFilters.add(new se.yarin.morphy.entities.filters.TournamentTitleFilter(tournament, true, false));
    }

    if (tournamentTimeControl != null) {
      tournamentFilters.add(new se.yarin.morphy.entities.filters.TournamentTimeControlFilter(tournamentTimeControl));
    }

    if (tournamentType != null) {
      tournamentFilters.add(new se.yarin.morphy.entities.filters.TournamentTypeFilter(tournamentType));
    }

    if (tournamentPlace != null) {
      tournamentFilters.add(new se.yarin.morphy.entities.filters.TournamentPlaceFilter(tournamentPlace, true, false));
    }

    if (!tournamentFilters.isEmpty()) {
      EntityQuery<Tournament> tournamentQuery =
          new EntityQuery<>(db, se.yarin.morphy.entities.EntityType.TOURNAMENT, List.<EntityFilter<Tournament>>copyOf(tournamentFilters));
      List<QueryOperator<Tournament>> tournamentPlans = db.queryPlanner().getEntityQueryPlans(qc, tournamentQuery, false);
      QueryOperator<Tournament> bestTournamentPlan = db.queryPlanner().selectBestQueryPlan(tournamentPlans);
      List<Integer> tournamentIds = bestTournamentPlan.stream()
          .map(qd -> qd.id())
          .collect(Collectors.toList());

      if (!tournamentIds.isEmpty()) {
        int[] tournamentIdArray = tournamentIds.stream().mapToInt(Integer::intValue).toArray();
        gameFilters.add(new TournamentFilter(tournamentIdArray));
      }
    }

    // Raw filters
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

    return new GameQuery(db, gameFilters, entityJoins);
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
