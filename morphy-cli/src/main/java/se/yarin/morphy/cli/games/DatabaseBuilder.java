package se.yarin.morphy.cli.games;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.text.TextModel;

import java.io.File;
import java.io.IOException;

public class DatabaseBuilder extends GameConsumerBase {
    private static final Logger log = LogManager.getLogger();

    // If true, moves and annotations are added without parsing
    private static final boolean QUICK_ADD = true;

    private static final int BATCH_SIZE = 10000;
    private static final int LOG_INFO_SIZE = 1000;

    private final Database database;
    private final DatabaseWriteTransaction transaction;
    private final File file;
    private int gamesAdded = 0;

    public DatabaseBuilder(File file) throws IOException {
        this.file = file;
        this.database = Database.create(file, true);
        transaction = new DatabaseWriteTransaction(this.database);
    }


    @Override
    public void finish() {
        System.out.println("Committing...");
        transaction.commit();
        System.out.println(gamesAdded + " games added");

        transaction.close();
        try {
            this.database.close();
        } catch (IOException e) {
            log.warn("Failed to close output database");
        }

        System.out.printf("%d games added to %s in %.2f s%n", totalFoundGames, file, totalSearchTime / 1000.0);
    }

    @Override
    public void accept(Game game) {
        Game addedGame;

        if (QUICK_ADD) {
            // Direct copy of game between databases
            // Header metadata is refreshed (entities needs to receive new ID's etc)
            // but moves and annotations are copied as opaque blobs
            try {
                addedGame = transaction.addGame(game);
            } catch (MorphyException e) {
                log.warn("Failed to add game " + game.id() + " in the searched database");
                return;
            }
        } else {
            // Moves and annotations are deserialized into models and then
            // serialized back again. This is slower, but will detect errors in the games.
            if (game.guidingText()) {
                TextModel model;
                try {
                     model = game.getTextModel();
                } catch (MorphyException e) {
                    log.warn("Failed to get text " + game.id() + " in the searched database");
                    return;
                }
                try {
                    addedGame = transaction.addText(model);
                } catch (MorphyException e) {
                    log.warn("Failed to add text " + game.id() + " in the searched database");
                    return;
                }
            } else {
                GameModel model;
                try {
                    model = game.getModel();
                } catch (MorphyException e) {
                    log.warn("Failed to get game " + game.id() + " in the searched database");
                    return;
                }

                try {
                    addedGame = transaction.addGame(model);
                } catch (MorphyInvalidDataException e) {
                    log.warn("Failed to add game " + game.id() + " in the searched database in the output database", e);
                    return;
                }
            }
        }

        if (log.isDebugEnabled()) {
            if (game.guidingText()) {
                log.debug(String.format("%d: Text added to new database with id %d",
                        game.id(), addedGame.id()));
            } else {
                log.debug(String.format("%d: %s-%s added to new database with id %d",
                        game.id(), game.white().getFullNameShort(), game.black().getFullNameShort(), addedGame.id()));
            }
        }
        gamesAdded++;

        if (gamesAdded % LOG_INFO_SIZE == 0) {
            System.out.println(gamesAdded + " games added");
        }
        if (gamesAdded % BATCH_SIZE == 0) {
            System.out.println("Committing...");
            transaction.commit();
        }
    }
}
