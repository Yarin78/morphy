package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.cli.games.*;
import se.yarin.morphy.cli.columns.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.qqueries.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
public class Games extends BaseCommand implements Callable<Integer> {
    private static final Logger log = LogManager.getLogger();

    @CommandLine.Option(names = "--limit", description = "Max number of games to output")
    private int limit = 0;

    @CommandLine.Option(names = "--id", description = "The id of a game to get")
    private int[] ids;

    @CommandLine.Option(names = "--count-all", description = "Count all hits, even beyond the limit (if specified)")
    private boolean countAll = false;

    @CommandLine.Option(names = "--player", description = "Show only games with this player (any color)")
    private String[] players;

    @CommandLine.Option(names = "--result", description = "Show only games with this result (1-0, 0-1, draw, win, loss etc)")
    private String result;

    @CommandLine.Option(names = "--date", description = "Date range, e.g. '2015-10-' or '1960-1970'")
    private String dateRange;

    @CommandLine.Option(names = "--team", description = "Show only games where one of the players played for this team")
    private String team;

    @CommandLine.Option(names = "--game-tag", description = "Show only games with a game tag matching this prefix")
    private String gameTag;

    @CommandLine.Option(names = "--tournament", description = "Show only games in this tournament")
    private String tournament;

    @CommandLine.Option(names = "--tournament-time", description = "Show only games with this type of time control (normal, rapid, blitz, corr)")
    private String tournamentTimeControl;

    @CommandLine.Option(names = "--tournament-type", description = "Show only games in this type of tournament (tourn, swiss, match etc)")
    private String tournamentType;

    @CommandLine.Option(names = "--tournament-place", description = "Show only games from this tournament place")
    private String tournamentPlace;

    @CommandLine.Option(names = "--rating", description = "Rating range required for both players, e.g. 2700- or 2000-2200")
    private String ratingRangeBoth;

    @CommandLine.Option(names = "--rating.any", description = "Rating range required for at least one player, e.g. 2700- or 2000-2200")
    private String ratingRangeAny;

    @CommandLine.Option(names = "--setup-position", description = "Show only games that has a setup position (does not start at move 1)")
    private boolean setupPosition;

    @CommandLine.Option(names = "--start-position", description = "Show only games that starts at the start position (move 1)")
    private boolean startPosition;

    @CommandLine.Option(names = "--game", description = "Show only chess games (no guiding texts)")
    private boolean game;

    @CommandLine.Option(names = "--text", description = "Show only guiding texts")
    private boolean guidingText;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output database (.cbh or .pgn)")
    private String output;

    @CommandLine.Option(names = "--stats", description = "Show statistics about all matching games")
    private boolean stats;

    @CommandLine.Option(names = "--overwrite", description = "If true, overwrite the output database if it already exists.")
    private boolean overwrite;

    @CommandLine.Option(names = "--columns", description = "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
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

    @CommandLine.Option(names = "--raw-cbh", description = "Raw filter expression in CBH data (debug)")
    private String[] rawCbhFilter;

    @CommandLine.Option(names = "--raw-cbj", description = "Raw filter expression in CBJ data (debug)")
    private String[] rawCbjFilter;

    @Override
    public Integer call() throws IOException {
        var numDatabaseErrors = new AtomicInteger(0);

        setupGlobalOptions();

        GameConsumer gameConsumer = createGameConsumer();
        gameConsumer.init();

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                // Speeds up performance quite a lot, and we should be fairly certain that the moves in the CBH databases are valid
                db.moveRepository().setValidateDecodedMoves(false);

                ItemQuery<Game> gameQuery = null;
                try {
                    gameQuery = createGameQuery();
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                assert gameQuery != null;

                try (var txn = new DatabaseReadTransaction(db)) {
                    QueryResult<Game> result;

                    if (!(gameConsumer instanceof StdoutGamesSummary)) {
                        try (ProgressBar pb = new ProgressBar("Games", db.count())) {
                            QueryExecutor<Game> executor = new QueryExecutor<>(txn, game -> pb.stepTo(game.id()));
                            result = executor.execute(gameQuery, limit, countAll, gameConsumer);
                        }
                    } else {
                        QueryExecutor<Game> executor = new QueryExecutor<>(txn);
                        result = executor.execute(gameQuery, limit, countAll, gameConsumer);
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
                System.err.println("Unexpected error when processing " + file + ": " + e.getMessage());
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

    public ItemQuery<Game> createGameQuery() {
        ArrayList<ItemQuery<Game>> gameQueries = new ArrayList<>();
        gameQueries.add(new QGamesAll()); // Ensure we have at least one query

        if (ids != null) {
            gameQueries.add(new QGamesWithId(Arrays.stream(ids).boxed().collect(Collectors.toList())));
        }

        if (setupPosition) {
            gameQueries.add(new QGamesIsSetupPosition());
        }

        if (startPosition) {
            gameQueries.add(new QGamesIsStartPosition());
        }

        if (game) {
            gameQueries.add(new QGamesIsGame());
        }

        if (guidingText) {
            gameQueries.add(new QGamesIsText());
        }

        ItemQuery<Player> primaryPlayerSearcher = null;
        if (players != null) {
            for (String player : players) {
                ItemQuery<Player> playerSearcher;
                if (!player.contains("|")) {
                    playerSearcher = new QPlayersWithName(player, true, false);
                } else {
                    playerSearcher = new QOr<>(Arrays
                            .stream(player.split("\\|"))
                            .map(name -> new QPlayersWithName(name, true, false))
                            .collect(Collectors.toList()));

                }
                PlayerFilter.PlayerResult playerResult = PlayerFilter.PlayerResult.ANY;
                if ("win".equals(result)) {
                    playerResult = PlayerFilter.PlayerResult.WIN;
                } else if ("loss".equals(result)) {
                    playerResult = PlayerFilter.PlayerResult.LOSS;
                }
                gameQueries.add(new QGamesByPlayers(playerSearcher, PlayerFilter.PlayerColor.ANY, playerResult));
                if (primaryPlayerSearcher == null) {
                    primaryPlayerSearcher = playerSearcher;
                }
            }
        }

        if (result != null) {
            if (result.equals("win") || result.equals("loss")) {
                if (players == null) {
                    throw new IllegalArgumentException("A player search is needed when filtering on 'wins' or 'loss' results");
                }
                // Already taken care of above
            } else {
                gameQueries.add(new QGamesWithResult(result));
            }
        }

        if (dateRange != null) {
            gameQueries.add(new QGamesWithPlayedDate(dateRange));
        }

        if (ratingRangeBoth != null) {
            gameQueries.add(new QGamesWithRating(ratingRangeBoth, RatingRangeFilter.RatingColor.BOTH));
        }

        if (ratingRangeAny != null) {
            gameQueries.add(new QGamesWithRating(ratingRangeAny, RatingRangeFilter.RatingColor.ANY));
        }

        if (team != null) {
            gameQueries.add(new QGamesByTeams(new QTeamsWithTitle(team, true, false)));
        }

        if (gameTag != null) {
            gameQueries.add(new QGamesByGameTag(new QGameTagsWithTitle(gameTag, true, false)));
        }

        ArrayList<ItemQuery<Tournament>> tournamentQueries = new ArrayList<>();
        if (tournament != null) {
            // TODO: Extract year and use QTournamentsWithYearTitle
            tournamentQueries.add(new QTournamentsWithTitle(tournament, true, false));
        }

        if (tournamentTimeControl != null) {
            tournamentQueries.add(new QTournamentsWithTimeControl(tournamentTimeControl));
        }

        if (tournamentType != null) {
            tournamentQueries.add(new QTournamentsWithType(tournamentType));
        }

        if (tournamentPlace != null) {
            tournamentQueries.add(new QTournamentsWithPlace(tournamentPlace));
        }

        if (!tournamentQueries.isEmpty()) {
            gameQueries.add(new QGamesByTournaments(new QAnd<>(tournamentQueries)));
        }

        if (rawCbhFilter != null) {
            for (String filter : rawCbhFilter) {
                gameQueries.add(new QGamesWithRaw(new RawGameHeaderFilter(filter), null));
            }
        }

        if (rawCbjFilter != null) {
            for (String filter : rawCbjFilter) {
                gameQueries.add(new QGamesWithRaw(null, new RawExtendedHeaderFilter(filter)));
            }
        }
        return new QAnd<>(gameQueries);
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
                        parsedColumns.add(new RawHeaderColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                    }
                }
                if (rawCbjColumns != null) {
                    for (String rawCbjColumn : rawCbjColumns) {
                        String[] parts = rawCbjColumn.split(",");
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("Invalid format of raw CBJ column");
                        }
                        parsedColumns.add(new RawExtendedHeaderColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
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
                        parsedColumns.add(new RawCBBColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
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
            gameConsumer = new PgnDatabaseBuilder(file);
        } else {
            throw new IllegalArgumentException("Unknown output format: " + output);
        }
        return gameConsumer;
    }
}
