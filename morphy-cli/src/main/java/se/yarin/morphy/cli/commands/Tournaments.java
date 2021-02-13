package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentBase;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.util.CBUtil;

import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "tournaments", mixinStandardHelpOptions = true)
public class Tournaments extends BaseCommand implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger();

    @CommandLine.Option(names = "--count", description = "Max number of tournaments to list")
    int maxTournaments = 20;

    @CommandLine.Option(names = "--sorted", description = "Sort by default sorting order (instead of id)")
    boolean sorted = false;

    @CommandLine.Option(names = "--hex", description = "Show tournament key in hexadecimal")
    boolean hex = false;

    @Override
    public Integer call() throws IOException {
        setupGlobalOptions();

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file)) {
                TournamentBase tournaments = db.getTournamentBase();
                int count = 0;
                Iterable<TournamentEntity> iterable = sorted ? tournaments.iterableOrderedAscending() : tournaments.iterable();
                for (TournamentEntity tournament : iterable) {
                    if (count >= maxTournaments) break;
                    String line;
                    String year = tournament.getDate().year() == 0 ? "????" : tournament.getDate().toPrettyString().substring(0, 4);
                    if (hex) {
                        line = String.format("%7d:  %4s %-30s %-30s %6d", tournament.getId(), year, CBUtil.toHexString(tournament.getRaw()).substring(0, 30), tournament.getTitle(), tournament.getCount());
                    } else {
                        line = String.format("%7d:  %4s %-30s %6d", tournament.getId(), year, tournament.getTitle(), tournament.getCount());
                    }
                    System.out.println(line);
                    count += 1;
                }
                System.out.println();
                System.out.println("Total: " + tournaments.getCount());
            }  catch (IOException e) {
                System.err.println("IO error when processing " + file);
            }
        });
        return 0;
    }
}