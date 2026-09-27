package se.yarin.morphy.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.cli.columns.TournamentColumn;
import se.yarin.morphy.cli.queries.FacadeQuerySupport;
import se.yarin.morphy.cli.tournaments.StdoutTournamentsSummary;
import se.yarin.morphy.cli.tournaments.TournamentConsumer;

import java.io.IOException;
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
      names = "--columns",
      description =
          "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
  private String columns;

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    TournamentConsumer tournamentConsumer = createTournamentConsumer();
    tournamentConsumer.init();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
                tournamentConsumer.setCurrentDatabase(db);

                Sort sort = Sort.natural();
                if (sorted) {
                  SearchSchema schema = db.entitySearchSchema(EntityKind.TOURNAMENT);
                  if (!schema.sortFields().isEmpty()) {
                    sort = Sort.by(schema.sortFields().get(0).name(), null);
                  }
                }
                Query query = Query.of(filterExpression, sort, 0, 1);

                long startTime = System.currentTimeMillis();
                FacadeQuerySupport.Result result;
                try {
                  result =
                      FacadeQuerySupport.stream(
                          query,
                          limit,
                          pageQuery -> db.findEntities(EntityKind.TOURNAMENT, pageQuery),
                          tournamentConsumer);
                } catch (IllegalArgumentException e) {
                  System.err.println(e.getMessage());
                  System.exit(1);
                  return;
                }

                tournamentConsumer.searchDone(
                    result.total(), result.consumed(), System.currentTimeMillis() - startTime);
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

  public TournamentConsumer createTournamentConsumer() {
    if (columns == null) {
      columns = StdoutTournamentsSummary.DEFAULT_COLUMNS;
    }
    List<TournamentColumn> parsedColumns = StdoutTournamentsSummary.parseColumns(this.columns);
    return new StdoutTournamentsSummary(parsedColumns);
  }
}
