package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.entities.TournamentSearcher;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.cbhlib.games.search.DateRangeFilter;
import se.yarin.cbhlib.util.parser.Parser;
import se.yarin.cbhlib.util.parser.Scanner;
import se.yarin.morphy.cli.columns.RawTournamentColumn;
import se.yarin.morphy.cli.columns.TournamentColumn;
import se.yarin.morphy.cli.tournaments.StdoutTournamentsSummary;
import se.yarin.morphy.cli.tournaments.TournamentConsumer;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            try (Database db = Database.open(file)) {
                tournamentConsumer.setCurrentDatabase(db);
                TournamentSearcher tournamentSearcher = null;
                try {
                    tournamentSearcher = createTournamentSearcher(db);
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                assert tournamentSearcher != null;

                TournamentSearcher.SearchResult result = tournamentSearcher.search(limit, countAll, sorted, tournamentConsumer);

                tournamentConsumer.searchDone(result);
            } catch (IOException e) {
                System.err.println("IO error when processing " + file);
            } catch (RuntimeException e) {
                System.err.println("Unexpected error when processing " + file + ": " + e.getMessage());
            }
        });

        tournamentConsumer.finish();
        return 0;
    }

    public TournamentSearcher createTournamentSearcher(Database db) {
        TournamentSearcher searcher = new TournamentSearcher(db.getTournamentBase());

        if (name != null) {
            searcher.setSearchString(name, true, false);
        }
        if (dateRange != null) {
            searcher.setFromDate(DateRangeFilter.parseFromDate(dateRange));
            searcher.setToDate(DateRangeFilter.parseToDate(dateRange));
        }
        if (type != null) {
            TournamentType tournamentType = TournamentType.fromName(type);
            searcher.setTypes(Set.of(tournamentType));
        }
        if (timeControl != null) {
            TournamentTimeControl tournamentTimeControl = TournamentTimeControl.fromName(timeControl);
            searcher.setTimeControls(Set.of(tournamentTimeControl));
        }
        if (place != null) {
            searcher.setPlaces(Set.of(place));
        }
        if (nation != null) {
            searcher.setNations(Set.of(Nation.fromName(nation)));
        }
        if (teams) {
            searcher.setTeamsOnly(true);
        }
        if (rounds != null) {
            Pattern pattern = Pattern.compile("^([0-9]+)?-([0-9]+)?$");
            Matcher matcher = pattern.matcher(rounds);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid round range specified: " + rounds);
            }
            searcher.setMinRounds(matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1)));
            searcher.setMaxRounds(matcher.group(2) == null ? 9999 : Integer.parseInt(matcher.group(2)));
        }
        searcher.setMinCategory(minCategory);
        if (rawFilter != null) {
            for (String expression : rawFilter) {
                Scanner scanner = new Scanner(expression);
                searcher.addExpression(new Parser(scanner.scanTokens()).parse());
            }
        }

        return searcher;
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