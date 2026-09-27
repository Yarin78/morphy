package se.yarin.morphy.cli.commands;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.cli.columns.GameColumn;
import se.yarin.morphy.cli.games.GameConsumer;
import se.yarin.morphy.cli.games.OutputDatabaseWriter;
import se.yarin.morphy.cli.games.StatsGameConsumer;
import se.yarin.morphy.cli.games.StdoutGamesSummary;
import se.yarin.morphy.cli.queries.FacadeQuerySupport;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
public class Games extends BaseCommand implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(Games.class);

  @CommandLine.Parameters(
      index = "1",
      arity = "0..1",
      description =
          "Filter expression (e.g., \"result:1-0 AND player.name:Carlsen AND date:2020..\")")
  private String filterExpression;

  @CommandLine.Option(names = "--limit", description = "Max number of games to output")
  private int limit = 0;

  @CommandLine.Option(names = "--id", description = "The id of a game to get")
  private int[] ids;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output database (.cbh, .2cbh or .pgn)")
  private String output;

  @CommandLine.Option(names = "--stats", description = "Show statistics about all matching games")
  private boolean stats;

  @CommandLine.Option(
      names = "--overwrite",
      description = "If true, overwrite the output database if it already exists.")
  private boolean overwrite;

  @CommandLine.Option(
      names = "--columns",
      description =
          "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
  private String columns;

  @Override
  public Integer call() throws IOException {
    var numDatabaseErrors = new AtomicInteger(0);

    setupGlobalOptions();

    GameConsumer gameConsumer = createGameConsumer();
    gameConsumer.init();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
                gameConsumer.setCurrentDatabase(db);

                Query query = Query.of(filterExpression, Sort.natural(), 0, 1);
                GameFetchOptions fetchOptions =
                    new GameFetchOptions(gameConsumer.needsMoves(), false, true);

                long startTime = System.currentTimeMillis();
                FacadeQuerySupport.Result result;
                try {
                  if (!(gameConsumer instanceof StdoutGamesSummary)) {
                    try (ProgressBar pb = new ProgressBar("Games", db.gameCount())) {
                      result =
                          FacadeQuerySupport.stream(
                              query,
                              limit,
                              pageQuery -> db.findGames(pageQuery, fetchOptions),
                              dto -> {
                                gameConsumer.accept(dto);
                                pb.stepTo(dto.id());
                              });
                    }
                  } else {
                    result =
                        FacadeQuerySupport.stream(
                            query,
                            limit,
                            pageQuery -> db.findGames(pageQuery, fetchOptions),
                            gameConsumer);
                  }
                } catch (IllegalArgumentException e) {
                  System.err.println(e.getMessage());
                  System.exit(1);
                  return;
                }

                gameConsumer.searchDone(
                    result.total(), result.consumed(), System.currentTimeMillis() - startTime);
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

    gameConsumer.finish();
    if (numDatabaseErrors.get() > 0) {
      return 1;
    }
    return 0;
  }

  public GameConsumer createGameConsumer() throws IOException {
    GameConsumer gameConsumer;
    if (output == null) {
      if (!stats) {
        if (columns == null) {
          columns = StdoutGamesSummary.DEFAULT_COLUMNS;
        }
        List<GameColumn> parsedColumns = StdoutGamesSummary.parseColumns(this.columns);
        gameConsumer = new StdoutGamesSummary(parsedColumns);
        if (limit == 0) {
          limit = 50;
        }
      } else {
        gameConsumer = new StatsGameConsumer();
      }
    } else {
      File file = new File(output);
      if (!overwrite && file.exists()) {
        throw new FileAlreadyExistsException(output);
      }
      gameConsumer = new OutputDatabaseWriter(file, overwrite);
    }
    return gameConsumer;
  }
}
