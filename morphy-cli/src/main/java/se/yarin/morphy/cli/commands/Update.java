package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.GameAdapter;
import se.yarin.morphy.cli.opening.OpeningRepertoireCache;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.cli.queries.QueryAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private final GameQueryBuilder gameQueryBuilder = new GameQueryBuilder();

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    if (tag == null && annotateOpening == null) {
      System.err.println("No update specified; use --tag or --annotate-opening");
      return 1;
    }
    if (tag != null && annotateOpening != null) {
      System.err.println("--tag and --annotate-opening cannot be combined");
      return 1;
    }

    final OpeningRepertoireCache openingRepertoire;
    if (annotateOpening != null) {
      log.info("Loading opening repertoire {}", annotateOpening);
      try {
        openingRepertoire = OpeningRepertoireCache.load(annotateOpening);
      } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 1;
      }
      log.info("Annotating as {}", openingRepertoire.myColor());
    } else {
      openingRepertoire = null;
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
                  int updatedInDb;
                  if (tag != null) {
                    try (var writeTxn = new DatabaseWriteTransaction(db)) {
                      updatedInDb = applyStaticTag(writeTxn, matchingGameIds, tag);
                      writeTxn.commit();
                    }
                  } else {
                    // One transaction per game: committing a single big transaction with many
                    // growing move/annotation blobs is very slow, since each replaced game with a
                    // size delta triggers an O(remaining games) offset-shifting pass.
                    updatedInDb = applyOpeningAnnotation(db, matchingGameIds, openingRepertoire);
                  }
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

    System.out.println("Updated " + totalUpdated.get() + " game(s)");

    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }

  /** Sets a fixed GameTag on all matching games. Returns the number of games updated. */
  private int applyStaticTag(
      DatabaseWriteTransaction writeTxn, List<Integer> matchingGameIds, String tag) {
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
    return matchingGameIds.size();
  }

  /**
   * Classifies each matching game against the opening repertoire, sets the GameTag to the best
   * matching branch, and annotates moves that deviate from it. Games with no match are left
   * untouched. Each updated game is committed in its own transaction. Returns the number of games
   * updated.
   */
  private int applyOpeningAnnotation(
      Database db, List<Integer> matchingGameIds, OpeningRepertoireCache openingRepertoire) {
    Map<String, Integer> classifiedTagIds = new HashMap<>();
    int updated = 0;

    try (ProgressBar pb = new ProgressBar("Updating", matchingGameIds.size())) {
      for (int gameId : matchingGameIds) {
        try (var writeTxn = new DatabaseWriteTransaction(db)) {
          Game game = writeTxn.getGame(gameId);
          GameModel model = game.getModel();

          Optional<OpeningRepertoireCache.Entry> matchedEntry =
              openingRepertoire.classify(model.moves());
          if (matchedEntry.isEmpty()) {
            pb.step();
            continue;
          }

          OpeningRepertoireCache.Entry entry = matchedEntry.get();
          String tagText = openingRepertoire.formatTag(entry);
          int gameTagId =
              classifiedTagIds.computeIfAbsent(
                  tagText, t -> writeTxn.gameTagTransaction().getOrCreate(GameTag.of(t)));

          openingRepertoire.annotate(model.moves(), entry);
          model.header().setField(GameAdapter.GAME_TAG_ID, gameTagId);

          writeTxn.replaceGame(gameId, model);
          writeTxn.commit();
          updated++;
          pb.step();
        }
      }
    }
    return updated;
  }
}
