package se.yarin.morphy.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.cli.queries.FacadeQuerySupport;
import se.yarin.morphy.model.PlayerDto;

import java.io.IOException;
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

  @Override
  public Integer call() throws IOException {
    setupGlobalOptions();

    getDatabaseStream()
        .forEach(
            file -> {
              log.info("Opening {}", file);
              try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
                Query query = Query.of(filterExpression, Sort.natural(), 0, 1);

                FacadeQuerySupport.Result result;
                try {
                  result =
                      FacadeQuerySupport.stream(
                          query,
                          limit,
                          pageQuery -> db.findEntities(EntityKind.PLAYER, pageQuery),
                          Players::printPlayer);
                } catch (IllegalArgumentException e) {
                  System.err.println(e.getMessage());
                  System.exit(1);
                  return;
                }

                System.out.println();
                if (result.consumed() < result.total()) {
                  System.out.printf("%d out of %d hits%n", result.consumed(), result.total());
                } else {
                  System.out.printf("%d hits%n", result.total());
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

  private static void printPlayer(PlayerDto player) {
    System.out.printf(
        "%7d:  %-30s %6d%n",
        player.id(), fullName(player), player.gameCount() == null ? 0 : player.gameCount());
  }

  private static String fullName(PlayerDto player) {
    String last = player.lastName() == null ? "" : player.lastName();
    String first = player.firstName() == null ? "" : player.firstName();
    if (last.isEmpty()) {
      return first;
    }
    if (first.isEmpty()) {
      return last;
    }
    return last + ", " + first;
  }
}
