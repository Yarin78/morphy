package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.RawEntityFilter;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.cli.columns.RawTournamentColumn;
import se.yarin.morphy.cli.columns.TournamentColumn;
import se.yarin.morphy.cli.tournaments.StdoutTournamentsSummary;
import se.yarin.morphy.cli.tournaments.TournamentConsumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "tournaments", mixinStandardHelpOptions = true)
public class Tournaments extends BaseCommand implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger();

    @CommandLine.Option(names = "--limit", description = "Max number of tournaments to list")
    int limit = 20;

    @CommandLine.Option(names = "--sorted", description = "Sort by default sorting order (instead of id)")
    boolean sorted = false;

    @CommandLine.Option(names = "--count-all", description = "Count all hits, even beyond the limit (if specified)")
    private boolean countAll = false;

    @CommandLine.Option(names = "--name", description = "Show only tournaments matching this search string")
    private String name;

    @CommandLine.Option(names = "--date", description = "Date range, e.g. '2015-10-' or '1960-1970'")
    private String dateRange;

    @CommandLine.Option(names = "--time", description = "Show only tournaments with this type of time control (normal, rapid, blitz, corr)")
    private String timeControl;

    @CommandLine.Option(names = "--type", description = "Show only this type of tournaments (tourn, swiss, match etc)")
    private String type;

    @CommandLine.Option(names = "--place", description = "Show only tournaments from this place")
    private String place;

    @CommandLine.Option(names = "--category", description = "Minimum category")
    private int minCategory;

    @CommandLine.Option(names = "--rounds", description = "Round range, e.g. '5-' or '11-13'")
    private String rounds;

    @CommandLine.Option(names = "--nation", description = "Show only tournaments from this nation")
    private String nation;

    @CommandLine.Option(names = "--teams", description = "Show only team tournaments")
    private boolean teams;

    @CommandLine.Option(names = "--raw", description = "Raw filter expression in CBT data (debug)")
    private String[] rawFilter;

    @CommandLine.Option(names = "--columns", description = "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
    private String columns;

    @CommandLine.Option(names = "--raw-col", description = "Show binary CBT data (debug)")
    private String[] rawColumns;


    @Override
    public Integer call() throws IOException {
        setupGlobalOptions();

        TournamentConsumer tournamentConsumer = createTournamentConsumer();
        tournamentConsumer.init();

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                tournamentConsumer.setCurrentDatabase(db);
                ItemQuery<Tournament> tournamentQuery = null;
                try {
                    tournamentQuery = createTournamentQuery();
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                assert tournamentQuery != null;

                try (var txn = new DatabaseReadTransaction(db)) {
                    QueryExecutor<Tournament> executor = new QueryExecutor<>(txn);
                    // TODO: support sorted
                    QueryResult<Tournament> result = executor.execute(tournamentQuery, limit, countAll, tournamentConsumer);

                    tournamentConsumer.searchDone(result);
                }

                if (showInstrumentation()) {
                    db.context().instrumentation().show();
                }
            } catch (IOException e) {
                System.err.println("IO error when processing " + file);
                if (verboseLevel() > 0) {
                    e.printStackTrace();
                }
            } catch (RuntimeException e) {
                System.err.println("Unexpected error when processing " + file + ": " + e.getMessage());
                if (verboseLevel() > 0) {
                    e.printStackTrace();
                }
            }
        });

        tournamentConsumer.finish();
        return 0;
    }

    public ItemQuery<Tournament> createTournamentQuery() {
        ArrayList<ItemQuery<Tournament>> tournamentQueries = new ArrayList<>();
        tournamentQueries.add(new QTournamentsAll()); // Ensure we have at least one query

        if (name != null) {
            tournamentQueries.add(new QTournamentsSearch(name, true, false));
        }
        if (dateRange != null) {
            tournamentQueries.add(new QTournamentsWithStartDate(dateRange));
        }
        if (type != null) {
            tournamentQueries.add(new QTournamentsWithType(type));
        }
        if (timeControl != null) {
            tournamentQueries.add(new QTournamentsWithTimeControl(timeControl));
        }
        if (place != null) {
            tournamentQueries.add(new QTournamentsWithPlace(place));
        }
        if (nation != null) {
            tournamentQueries.add(new QTournamentsWithNation(nation));
        }
        if (teams) {
            tournamentQueries.add(new QTournamentsIsTeam());
        }
        if (rounds != null) {
            tournamentQueries.add(new QTournamentsWithRounds(rounds));
        }
        if (minCategory > 0) {
            tournamentQueries.add(new QTournamentsWithCategory(minCategory, 100));
        }
        if (rawFilter != null) {
            for (String expression : rawFilter) {
                tournamentQueries.add(new QTournamentsWithRaw(new RawEntityFilter<>(expression)));
            }
        }

        return new QAnd<>(tournamentQueries);
    }

    public TournamentConsumer createTournamentConsumer() {
        if (columns == null) {
            columns = StdoutTournamentsSummary.DEFAULT_COLUMNS;
        }
        List<TournamentColumn> parsedColumns = StdoutTournamentsSummary.parseColumns(this.columns);
        if (rawColumns != null) {
            for (String rawCbhColumn : rawColumns) {
                String[] parts = rawCbhColumn.split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid format of raw CBT column");
                }
                parsedColumns.add(new RawTournamentColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
        }

        return new StdoutTournamentsSummary(countAll, parsedColumns);
    }
}