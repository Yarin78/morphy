package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.chessbase.convert.GameDtoImporter;
import se.yarin.morphy.cli.games.OutputDatabases;
import se.yarin.morphy.cli.opening.OpeningRepertoireCache;
import se.yarin.morphy.cli.queries.FacadeQuerySupport;
import se.yarin.morphy.pgn.PgnGameMapper;
import se.yarin.morphy.pgn.GameMovesDtoCodec;

import java.io.File;
import java.io.IOException;
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

  // The synthesized summary carries only plain PGN-representable annotations (comments and NAGs),
  // so a plain GameMovesDtoCodec is enough to turn it into a GameDto; each target format's own
  // Database.addGame(dto) then re-encodes those annotations however it needs to.
  private static final PgnGameMapper MAPPER = new PgnGameMapper(GameMovesDtoCodec.PLAIN);

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
      description = "The database to write the summary games to (.cbh, .2cbh or .pgn)")
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
              try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
                Query query = Query.of(filterExpression, Sort.natural(), 0, 1);
                GameFetchOptions fetchOptions = new GameFetchOptions(true, false, false);
                GameDtoImporter importer = new GameDtoImporter();

                FacadeQuerySupport.Result result;
                try {
                  try (ProgressBar pb = new ProgressBar("Classifying", db.gameCount())) {
                    result =
                        FacadeQuerySupport.stream(
                            query,
                            0,
                            pageQuery -> db.findGames(pageQuery, fetchOptions),
                            dto -> {
                              if (!"text".equals(dto.type())) {
                                GameMovesModel moves = importer.toGameModel(dto).moves();
                                openingRepertoire
                                    .classify(moves)
                                    .ifPresent(
                                        entry -> {
                                          openingRepertoire.recordGame(moves, entry);
                                          totalClassified.incrementAndGet();
                                        });
                              }
                              pb.stepTo(dto.id());
                            });
                  }
                } catch (IllegalArgumentException e) {
                  System.err.println(e.getMessage());
                  System.exit(1);
                  return;
                }

                totalGames.addAndGet((int) result.consumed());
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

    int entriesWritten;
    try {
      entriesWritten = writeOutput(openingRepertoire);
    } catch (RuntimeException e) {
      System.err.println("Unknown output format: " + output + " (" + e.getMessage() + ")");
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

  private int writeOutput(OpeningRepertoireCache repertoire) throws IOException {
    OutputDatabases.prepareForOverwrite(output, overwrite);
    int entriesWritten = 0;
    try (Database outputDb = Databases.create(output)) {
      for (OpeningRepertoireCache.Entry entry : repertoire.entries()) {
        Optional<GameModel> summary = repertoire.summarize(entry, annotateAllMoves);
        if (summary.isPresent()) {
          outputDb.addGame(MAPPER.toDto(summary.get(), null, true));
          entriesWritten++;
        }
      }
    }
    return entriesWritten;
  }
}
