package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.chess.GameModel;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.cli.opening.OpeningRepertoireCache;
import se.yarin.morphy.cli.update.GameUpdater;
import se.yarin.morphy.cli.update.OpeningClassifyUpdater;
import se.yarin.morphy.cli.update.StaticTagUpdater;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;

import java.io.File;
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

  @CommandLine.Option(
      names = "--annotate-opening",
      description =
          "Classifies each matching game against the games (opening repertoire branches) in the "
              + "given opening database, sets the GameTag to the best matching branch, and "
              + "annotates moves that deviate from it. The database name must contain \"white\" "
              + "or \"black\" to indicate which color the repertoire is prepared for.")
  private File annotateOpening;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description =
          "Instead of updating games in place, write matching (and updated) games to this new "
              + "database, leaving the source database(s) untouched. If the updates involve "
              + "adding moves or annotations to the games, this might be much faster "
              + "than an in-place update.")
  private File output;

  @CommandLine.Option(
      names = "--overwrite",
      description = "If true, overwrite the output database if it already exists.")
  private boolean overwrite;

  @CommandLine.Option(
      names = "--batch-size",
      description =
          "Number of games to commit per transaction. Defaults to committing all matching games "
              + "in a single transaction.")
  private Integer batchSize;

  private final GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    List<GameUpdater> updaters = new ArrayList<>();
    if (annotateOpening != null) {
      log.info("Loading opening repertoire {}", annotateOpening);
      OpeningRepertoireCache openingRepertoire;
      try {
        openingRepertoire = OpeningRepertoireCache.load(annotateOpening);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 1;
      }
      log.info("Annotating as {}", openingRepertoire.myColor());
      updaters.add(new OpeningClassifyUpdater(openingRepertoire));
    }
    if (tag != null) {
      updaters.add(new StaticTagUpdater(tag));
    }
    if (updaters.isEmpty()) {
      System.err.println("No update specified; use --tag or --annotate-opening");
      return 1;
    }

    var numDatabaseErrors = new AtomicInteger(0);
    var totalUpdated = new AtomicInteger(0);

    DatabaseCbh outputDb = output != null ? DatabaseCbh.create(output, overwrite) : null;
    try {
      DatabaseMode sourceMode =
          outputDb != null ? DatabaseMode.READ_ONLY : DatabaseMode.READ_WRITE;

      getDatabaseStream()
          .forEach(
              file -> {
                log.info("Opening {}", file);
                try (DatabaseCbh db = DatabaseCbh.open(file, sourceMode)) {
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
                    int updatedInDb =
                        applyUpdates(db, outputDb, matchingGameIds, updaters, batchSize);
                    totalUpdated.addAndGet(updatedInDb);
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
    } finally {
      if (outputDb != null) {
        outputDb.close();
      }
    }

    System.out.println("Updated " + totalUpdated.get() + " game(s)");
    if (outputDb != null) {
      System.out.println("Wrote to " + output);
    }

    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }

  /**
   * Applies all {@code updaters} to each matching game in turn, committing every {@code
   * batchSize} games (or all of them in one transaction, if null/non-positive). A game is skipped
   * entirely if any updater declines it (e.g. it didn't match an opening classification).
   *
   * <p>If {@code outputDb} is null, updated games are replaced in place in {@code sourceDb}.
   * Otherwise they're appended to {@code outputDb}, leaving {@code sourceDb} untouched; since
   * appending is always O(1) per game, batch size only affects transaction/commit overhead there,
   * never the offset-shifting cost that in-place replacement can incur when move/annotation data
   * grows.
   *
   * @return the number of games updated/written
   */
  private int applyUpdates(
      DatabaseCbh sourceDb,
      @Nullable DatabaseCbh outputDb,
      List<Integer> matchingGameIds,
      List<GameUpdater> updaters,
      @Nullable Integer batchSize) {
    boolean toOutput = outputDb != null;
    DatabaseCbh targetDb = toOutput ? outputDb : sourceDb;
    int size = batchSize == null || batchSize <= 0 ? matchingGameIds.size() : batchSize;
    int updated = 0;

    try (ProgressBar pb =
        new ProgressBar(toOutput ? "Writing" : "Updating", matchingGameIds.size())) {
      for (int start = 0; start < matchingGameIds.size(); start += size) {
        List<Integer> batch =
            matchingGameIds.subList(start, Math.min(start + size, matchingGameIds.size()));

        try (var writeTxn = new DatabaseWriteTransaction(targetDb)) {
          for (int gameId : batch) {
            Game sourceGame = toOutput ? sourceDb.getGame(gameId) : writeTxn.getGame(gameId);
            GameModel model = sourceGame.getModel();

            boolean matched = true;
            for (GameUpdater updater : updaters) {
              if (!updater.apply(model)) {
                matched = false;
                break;
              }
            }

            if (matched) {
              if (toOutput) {
                // The header carries internal entity-id references resolved against sourceDb;
                // those are meaningless (or worse, refer to unrelated entities) in outputDb, so
                // clear them and let addGame() resolve every entity by value instead.
                model.header().clearEntityIds();
                writeTxn.addGame(model);
              } else {
                writeTxn.replaceGame(gameId, model);
              }
              updated++;
            }
            pb.step();
          }
          writeTxn.commit();
        }
      }
    }
    return updated;
  }
}
