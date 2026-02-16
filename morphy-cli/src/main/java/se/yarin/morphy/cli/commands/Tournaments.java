package se.yarin.morphy.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.queries.filter.TournamentQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;
import se.yarin.morphy.cli.queries.QueryResult;
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

  private static final Logger log = LoggerFactory.getLogger(Tournaments.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description =
          "Filter expression (e.g., \"name:Candidates AND type:swiss AND date:2020..\")")
  private String filterExpression;

  @CommandLine.Option(names = "--limit", description = "Max number of tournaments to list")
  int limit = 20;

  @CommandLine.Option(
      names = "--sorted",
      description = "Sort by default sorting order (instead of id)")
  boolean sorted = false;

  @CommandLine.Option(
      names = "--count-all",
      description = "Count all hits, even beyond the limit (if specified)")
  private boolean countAll = false;

  @CommandLine.Option(names = "--raw", description = "Raw filter expression in CBT data (debug)")
  private String[] rawFilter;

  @CommandLine.Option(
      names = "--columns",
      description =
          "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
  private String columns;

  @CommandLine.Option(names = "--raw-col", description = "Show binary CBT data (debug)")
  private String[] rawColumns;

  private final TournamentQueryBuilder tournamentQueryBuilder = new TournamentQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    TournamentConsumer tournamentConsumer = createTournamentConsumer();
    tournamentConsumer.init();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                tournamentConsumer.setCurrentDatabase(db);

                try (var txn = new DatabaseReadTransaction(db)) {
                  EntityQuery<Tournament> tournamentQuery = null;
                  try {
                    tournamentQuery = createTournamentQuery(db);
                  } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                  }
                  assert tournamentQuery != null;

                  QueryContext qc = new QueryContext(txn, false);
                  List<QueryOperator<Tournament>> plans = db.queryPlanner().getEntityQueryPlans(qc, tournamentQuery, true);
                  QueryOperator<Tournament> bestPlan = db.queryPlanner().selectBestQueryPlan(plans);

                  // TODO: support sorted
                  QueryResult<Tournament> result =
                      QueryAdapter.execute(bestPlan, limit, countAll, tournamentConsumer, null);

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
                System.err.println(
                    "Unexpected error when processing " + file + ": " + e.getMessage());
                if (verboseLevel() > 0) {
                  e.printStackTrace();
                }
              }
            });

    tournamentConsumer.finish();
    return 0;
  }

  public EntityQuery<Tournament> createTournamentQuery(Database db) {
    // Build query from filter expression
    EntityQuery<Tournament> baseQuery = tournamentQueryBuilder.buildQuery(db, filterExpression);

    // Add raw debug filters on top of the parsed query
    if (rawFilter == null) {
      return baseQuery;
    }

    ArrayList<EntityFilter<Tournament>> filters = new ArrayList<>(baseQuery.filters());
    for (String expression : rawFilter) {
      filters.add(new RawEntityFilter<>(expression, EntityType.TOURNAMENT));
    }

    return new EntityQuery<>(db, EntityType.TOURNAMENT, List.<EntityFilter<Tournament>>copyOf(filters));
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
        parsedColumns.add(
            new RawTournamentColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
      }
    }

    return new StdoutTournamentsSummary(countAll, parsedColumns);
  }
}
