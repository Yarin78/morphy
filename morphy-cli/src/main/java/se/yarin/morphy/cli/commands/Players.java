package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerBase;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.util.CBUtil;

import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "players", mixinStandardHelpOptions = true)
public class Players extends BaseCommand implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger();

    @CommandLine.Option(names = "--count", description = "Max number of players to list")
    int maxPlayers = 20;

    @CommandLine.Option(names = "--hex", description = "Show player key in hexadecimal")
    boolean hex = false;

    @Override
    public Integer call() throws IOException {
        setupGlobalOptions();

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file)) {

                PlayerBase players = db.getPlayerBase();
                int count = 0;
                for (PlayerEntity player : players.iterable()) {
                    if (count >= maxPlayers) break;
                    String line;
                    if (hex) {
                        line = String.format("%7d:  %-30s %-30s %6d", player.getId(), CBUtil.toHexString(player.getRaw()).substring(0, 30), player.getFullName(), player.getCount());
                    } else {
                        line = String.format("%7d:  %-30s %6d", player.getId(), player.getFullName(), player.getCount());
                    }
                    System.out.println(line);
                }
                System.out.println();
                System.out.println("Total: " + players.getCount());
            } catch (IOException e) {
                System.err.println("IO error when processing " + file);
            }
        });

        return 0;
    }
}
