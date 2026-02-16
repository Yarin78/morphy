package se.yarin.morphy.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.queries.filter.PlayerQueryBuilder;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "players", mixinStandardHelpOptions = true)
public class Players extends BaseCommand implements Callable<Integer> {

  private static final Logger log = LoggerFactory.getLogger(Players.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description = "Filter expression (e.g., \"name:Carlsen\")")
  private String filterExpression;

  @CommandLine.Option(names = "--limit", description = "Max number of players to list")
  int limit = 20;

  @CommandLine.Option(
      names = "--count-all",
      description = "Count all hits, even beyond the limit (if specified)")
  private boolean countAll = false;

  @CommandLine.Option(names = "--hex", description = "Show player key in hexadecimal")
  boolean hex = false;

  private final PlayerQueryBuilder playerQueryBuilder = new PlayerQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                try (var txn = new DatabaseReadTransaction(db)) {
                  EntityQuery<Player> playerQuery = null;
                  try {
                    playerQuery = playerQueryBuilder.buildQuery(db, filterExpression);
                  } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                  }
                  assert playerQuery != null;

                  QueryContext qc = new QueryContext(txn, false);
                  List<QueryOperator<Player>> plans =
                      db.queryPlanner().getEntityQueryPlans(qc, playerQuery, true);
                  QueryOperator<Player> bestPlan = db.queryPlanner().selectBestQueryPlan(plans);

                  int consumed = 0;
                  int total = 0;
                  for (var qd : (Iterable<QueryData<Player>>) bestPlan.stream()::iterator) {
                    Player player = qd.data();
                    if (player == null) continue;
                    total++;

                    if (limit > 0 && consumed >= limit) {
                      if (!countAll) break;
                      continue;
                    }

                    consumed++;
                    String line;
                    if (hex) {
                      byte[] raw = db.playerIndex().getRaw(player.id());
                      line =
                          String.format(
                              "%7d:  %-30s %-30s %6d",
                              player.id(),
                              CBUtil.toHexString(raw).substring(0, 30),
                              player.getFullName(),
                              player.count());
                    } else {
                      line =
                          String.format(
                              "%7d:  %-30s %6d",
                              player.id(), player.getFullName(), player.count());
                    }
                    System.out.println(line);
                  }

                  System.out.println();
                  if (countAll) {
                    if (consumed < total) {
                      System.out.printf("%d out of %d hits%n", consumed, total);
                    } else {
                      System.out.printf("%d hits%n", total);
                    }
                  } else {
                    System.out.println("Total: " + db.playerIndex().count());
                  }
                }

                if (showInstrumentation()) {
                  db.context().instrumentation().show();
                }
              } catch (IOException e) {
                System.err.println("IO error when processing " + file);
                if (verboseLevel() > 0) {
                  e.printStackTrace();
                }
              }
            });

    return 0;
  }
}
