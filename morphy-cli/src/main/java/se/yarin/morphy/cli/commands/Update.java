package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@CommandLine.Command(name = "update", mixinStandardHelpOptions = true)
public class Update extends BaseCommand implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(Update.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description =
          "Filter expression (e.g., \"result:1-0 AND player.name:Carlsen AND date:2020..\")")
  private String filterExpression;

  @CommandLine.Option(
      names = "--tag",
      description = "Sets the GameTag of all matching games to this value")
  private String tag;

  private final GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    if (tag == null) {
      System.err.println("No update specified; use --tag");
      return 1;
    }

    var numDatabaseErrors = new AtomicInteger(0);
    var totalUpdated = new AtomicInteger(0);

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Database.open(file, DatabaseMode.READ_WRITE)) {
                // Speeds up performance quite a lot, and we should be fairly certain that the
                // moves in the CBH databases are valid
                db.moveRepository().setValidateDecodedMoves(false);

                List<Integer> matchingGameIds = new ArrayList<>();
                try (var readTxn = new DatabaseReadTransaction(db)) {
                  GameQuery gameQuery;
                  try {
                    gameQuery = gameQueryBuilder.buildQuery(db, filterExpression);
                  } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                    return;
                  }

                  QueryContext qc = new QueryContext(readTxn, false);
                  List<QueryOperator<Game>> plans =
                      db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
                  QueryOperator<Game> bestPlan = db.queryPlanner().selectBestQueryPlan(plans);

                  try (ProgressBar pb = new ProgressBar("Searching", db.count())) {
                    QueryAdapter.execute(
                        bestPlan,
                        0,
                        false,
                        game -> matchingGameIds.add(game.id()),
                        game -> pb.stepTo(game.id()));
                  }
                }

                if (!matchingGameIds.isEmpty()) {
                  try (var writeTxn = new DatabaseWriteTransaction(db)) {
                    int gameTagId = writeTxn.gameTagTransaction().getOrCreate(GameTag.of(tag));

                    try (ProgressBar pb = new ProgressBar("Updating", matchingGameIds.size())) {
                      for (int gameId : matchingGameIds) {
                        Game game = writeTxn.getGame(gameId);
                        ExtendedGameHeader newExtendedHeader =
                            ImmutableExtendedGameHeader.builder()
                                .from(game.extendedHeader())
                                .gameTagId(gameTagId)
                                .build();
                        writeTxn.replaceGame(gameId, new Game(writeTxn, game.header(), newExtendedHeader));
                        pb.step();
                      }
                    }

                    writeTxn.commit();
                    totalUpdated.addAndGet(matchingGameIds.size());
                  }
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

    System.out.println("Updated " + totalUpdated.get() + " game(s)");

    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }
}
