package se.yarin.morphy.cli.old.commands;

import me.tongfei.progressbar.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.MultiPlayerSearcher;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.entities.SinglePlayerSearcher;
import se.yarin.cbhlib.entities.TournamentSearcher;
import se.yarin.cbhlib.games.search.*;
import se.yarin.morphy.cli.old.games.DatabaseBuilder;
import se.yarin.morphy.cli.old.games.GameConsumer;
import se.yarin.morphy.cli.old.games.StatsGameConsumer;
import se.yarin.morphy.cli.old.games.StdoutGamesSummary;
import se.yarin.morphy.cli.old.columns.*;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
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
        setupGlobalOptions();

        GameConsumer gameConsumer = createGameConsumer();
        gameConsumer.init();

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file)) {
                // Speeds up performance quite a lot, and we should be fairly certain that the moves in the CBH databases are valid
                db.getMovesBase().setValidateDecodedMoves(false);

                GameSearcher gameSearcher = null;
                try {
                    gameSearcher = createGameSearcher(db);
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                assert gameSearcher != null;

                GameSearcher.SearchResult result;

                if (!(gameConsumer instanceof StdoutGamesSummary)) {
                    try (ProgressBar pb = new ProgressBar("Games", gameSearcher.getTotal())) {
                        result = gameSearcher.search(limit, countAll, gameConsumer, pb::stepTo);
                    }
                } else {
                    result = gameSearcher.search(limit, countAll, gameConsumer, null);
                }

                gameConsumer.searchDone(result);
            } catch (IOException e) {
                System.err.println("IO error when processing " + file);
            } catch (RuntimeException e) {
                System.err.println("Unexpected error when processing " + file + ": " + e.getMessage());
            }
        });

        gameConsumer.finish();
        return 0;
    }

    public GameSearcher createGameSearcher(Database db) {
        GameSearcher gameSearcher = new GameSearcher(db);

        if (ids != null) {
            gameSearcher.addFilter(new GameIdFilter(db, Arrays.stream(ids).boxed().collect(Collectors.toList())));
        }

        if (setupPosition) {
            gameSearcher.addFilter(new SetupPositionFilter(db, true));
        }

        if (startPosition) {
            gameSearcher.addFilter(new SetupPositionFilter(db, false));
        }

        if (game) {
            gameSearcher.addFilter(new GameTypeFilter(db, false));
        }

        if (guidingText) {
            gameSearcher.addFilter(new GameTypeFilter(db, true));
        }

        PlayerSearcher primaryPlayerSearcher = null;
        if (players != null) {
            for (String player : players) {
                PlayerSearcher playerSearcher;
                if (!player.contains("|")) {
                    playerSearcher = new SinglePlayerSearcher(db.getPlayerBase(), player, true, false);
                } else {
                    playerSearcher = new MultiPlayerSearcher(db.getPlayerBase(), player);
                }
                gameSearcher.addFilter(new PlayerFilter(db, playerSearcher, PlayerFilter.PlayerColor.ANY));
                if (primaryPlayerSearcher == null) {
                    primaryPlayerSearcher = playerSearcher;
                }
            }
        }

        if (result != null) {
            if (result.equals("win") || result.equals("loss")) {
                if (primaryPlayerSearcher == null) {
                    throw new IllegalArgumentException("A player search is needed when filtering on 'wins' or 'loss' results");
                }
                gameSearcher.addFilter(new PlayerResultsFilter(db, result, primaryPlayerSearcher));
            } else {
                gameSearcher.addFilter(new ResultsFilter(db, result));
            }
        }

        if (dateRange != null) {
            gameSearcher.addFilter(new DateRangeFilter(db, dateRange));
        }

        if (ratingRangeBoth != null) {
            gameSearcher.addFilter(new RatingRangeFilter(db, ratingRangeBoth, RatingRangeFilter.RatingColor.BOTH));
        }

        if (ratingRangeAny != null) {
            gameSearcher.addFilter(new RatingRangeFilter(db, ratingRangeAny, RatingRangeFilter.RatingColor.ANY));
        }

        if (team != null) {
            gameSearcher.addFilter(new TeamFilter(db, team));
        }

        if (gameTag != null) {
            gameSearcher.addFilter(new GameTagFilter(db, gameTag));
        }

        TournamentSearcher tournamentSearcher = null;
        if (tournament != null) {
            tournamentSearcher = new TournamentSearcher(db.getTournamentBase(), tournament, true, false);
            gameSearcher.addFilter(new TournamentFilter(db, tournamentSearcher));
        }

        if (tournamentTimeControl != null) {
            TournamentTimeControlFilter ttFilter = new TournamentTimeControlFilter(db, tournamentTimeControl);
            gameSearcher.addFilter(ttFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setTimeControls(ttFilter.getTimeControls());
            }
        }

        if (tournamentType != null) {
            TournamentTypeFilter ttFilter = new TournamentTypeFilter(db, tournamentType);
            gameSearcher.addFilter(ttFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setTypes(ttFilter.getTypes());
            }
        }

        if (tournamentPlace != null) {
            TournamentPlaceFilter tpFilter = new TournamentPlaceFilter(db, tournamentPlace);
            gameSearcher.addFilter(tpFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setPlaces(tpFilter.getPlaces());
            }
        }

        if (rawCbhFilter != null) {
            for (String filter : rawCbhFilter) {
                gameSearcher.addFilter(new RawHeaderFilter(db, filter));
            }
        }

        if (rawCbjFilter != null) {
            for (String filter : rawCbjFilter) {
                gameSearcher.addFilter(new RawExtendedHeaderFilter(db, filter));
            }
        }
        return gameSearcher;
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
        } else {
            throw new IllegalArgumentException("Unknown output format: " + output);
        }
        return gameConsumer;
    }
}
