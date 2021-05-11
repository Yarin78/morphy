package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Player;

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
            try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                try (var txn = new DatabaseReadTransaction(db)) {
                    int count = 0;
                    for (Player player : txn.playerTransaction().iterable()) {
                        if (count >= maxPlayers) break;
                        String line;
                        if (hex) {
                            byte[] raw = db.playerIndex().getRaw(player.id());
                            line = String.format("%7d:  %-30s %-30s %6d", player.id(), CBUtil.toHexString(raw).substring(0, 30), player.getFullName(), player.count());
                        } else {
                            line = String.format("%7d:  %-30s %6d", player.id(), player.getFullName(), player.count());
                        }
                        System.out.println(line);
                    }
                    System.out.println();
                    System.out.println("Total: " + db.playerIndex().count());
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
