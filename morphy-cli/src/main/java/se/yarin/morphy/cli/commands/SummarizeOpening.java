package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.chess.GameModel;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.cli.games.PgnDatabaseBuilder;
import se.yarin.morphy.cli.opening.OpeningRepertoireCache;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classifies games against an opening repertoire, same as {@code update --annotate-opening}, but
 * instead of annotating each game individually, aggregates how they were actually played into one
 * summary game per repertoire entry: a pruned clone of the entry's own line, annotated with how
 * often each mistake or deviation occurred and how often the prepared line was actually reached.
 */
@CommandLine.Command(name = "summarize-opening", mixinStandardHelpOptions = true)
public class SummarizeOpening extends BaseCommand implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(SummarizeOpening.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description =
          "Filter expression (e.g., \"result:1-0 AND player.name:Carlsen AND date:2020..\")")
  private String filterExpression;

  @CommandLine.Parameters(index = "2", description = "The opening repertoire database")
  private File repertoire;

  @CommandLine.Parameters(
      index = "3",
      description = "The database to write the summary games to (.cbh or .pgn)")
  private File output;

  @CommandLine.Option(
      names = "--overwrite",
      description = "If true, overwrite the output database if it already exists.")
  private boolean overwrite;

  @CommandLine.Option(
      names = "--annotate-all-moves",
      description =
          "Also annotate moves that match the repertoire with how many times they were played, "
              + "not just deviations and end-of-line positions.")
  private boolean annotateAllMoves;

  private final GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    log.info("Loading opening repertoire {}", repertoire);
    OpeningRepertoireCache openingRepertoire;
    try {
      openingRepertoire = OpeningRepertoireCache.load(repertoire);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 1;
    }
    log.info("Classifying as {}", openingRepertoire.myColor());

    var numDatabaseErrors = new AtomicInteger(0);
    var totalGames = new AtomicInteger(0);
    var totalClassified = new AtomicInteger(0);

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (DatabaseCbh db = DatabaseCbh.open(file, DatabaseMode.READ_ONLY)) {
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

                try (ProgressBar pb = new ProgressBar("Classifying", matchingGameIds.size())) {
                  for (int gameId : matchingGameIds) {
                    GameModel model = db.getGame(gameId).getModel();
                    openingRepertoire
                        .classify(model.moves())
                        .ifPresent(
                            entry -> {
                              openingRepertoire.recordGame(model.moves(), entry);
                              totalClassified.incrementAndGet();
                            });
                    pb.step();
                  }
                }
                totalGames.addAndGet(matchingGameIds.size());

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

    String outputName = output.getName().toLowerCase(Locale.ROOT);
    int entriesWritten;
    if (outputName.endsWith(".cbh")) {
      entriesWritten = writeToDatabase(openingRepertoire);
    } else if (outputName.endsWith(".pgn")) {
      entriesWritten = writeToPgn(openingRepertoire);
    } else {
      System.err.println("Unknown output format: " + output + " (must end with .cbh or .pgn)");
      return 1;
    }

    System.out.println(
        "Classified " + totalClassified.get() + " of " + totalGames.get() + " game(s)");
    System.out.println("Wrote " + entriesWritten + " summary game(s) to " + output);

    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }

  private int writeToDatabase(OpeningRepertoireCache repertoire) throws IOException {
    int entriesWritten = 0;
    try (DatabaseCbh outputDb = DatabaseCbh.create(output, overwrite)) {
      try (var writeTxn = new DatabaseWriteTransaction(outputDb)) {
        for (OpeningRepertoireCache.Entry entry : repertoire.entries()) {
          Optional<GameModel> summary = repertoire.summarize(entry, annotateAllMoves);
          if (summary.isPresent()) {
            GameModel model = summary.get();
            model.header().clearEntityIds();
            writeTxn.addGame(model);
            entriesWritten++;
          }
        }
        writeTxn.commit();
      }
    }
    return entriesWritten;
  }

  private int writeToPgn(OpeningRepertoireCache repertoire) throws IOException {
    if (!overwrite && output.exists()) {
      throw new FileAlreadyExistsException(output.toString());
    }
    int entriesWritten = 0;
    PgnDatabaseBuilder pgnBuilder = new PgnDatabaseBuilder(output);
    try {
      for (OpeningRepertoireCache.Entry entry : repertoire.entries()) {
        Optional<GameModel> summary = repertoire.summarize(entry, annotateAllMoves);
        if (summary.isPresent()) {
          pgnBuilder.writeModel(summary.get());
          entriesWritten++;
        }
      }
    } finally {
      pgnBuilder.finish();
    }
    return entriesWritten;
  }
}
