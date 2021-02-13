package se.yarin.morphy.cli.games;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.TextModel;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;

public class DatabaseBuilder extends GameConsumerBase {
    private static final Logger log = LogManager.getLogger();

    // If true, moves and annotations are added without parsing
    private static final boolean QUICK_ADD = false;

    private final Database database;
    private final File file;
    private int gamesAdded = 0;

    public DatabaseBuilder(File file) throws IOException {
        this.file = file;
        this.database = Database.create(file, true);
        // this.database = new Database();
    }


    @Override
    public void finish() {
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
                addedGame = this.database.addGame(game);
            } catch (ChessBaseException e) {
                log.warn("Failed to add game " + game.getId() + " in the searched database");
                return;
            }
        } else {
            // Moves and annotations are deserialized into models and then
            // serialized back again. This is slower, but will detect errors in the games.
            if (game.isGuidingText()) {
                TextModel model;
                try {
                     model = game.getTextModel();
                } catch (ChessBaseException e) {
                    log.warn("Failed to get text " + game.getId() + " in the searched database");
                    return;
                }
                try {
                    addedGame = this.database.addText(model);
                } catch (ChessBaseException e) {
                    log.warn("Failed to add text " + game.getId() + " in the searched database");
                    return;
                }
            } else {
                GameModel model;
                try {
                    model = game.getModel();
                } catch (ChessBaseException e) {
                    log.warn("Failed to get game " + game.getId() + " in the searched database");
                    return;
                }

                try {
                    addedGame = this.database.addGame(model);
                } catch (ChessBaseInvalidDataException e) {
                    log.warn("Failed to add game " + game.getId() + " in the searched database in the output database", e);
                    return;
                }
            }
        }

        if (log.isDebugEnabled()) {
            if (game.isGuidingText()) {
                log.debug(String.format("%d: Text added to new database with id %d",
                        game.getId(), addedGame.getId()));
            } else {
                log.debug(String.format("%d: %s-%s added to new database with id %d",
                        game.getId(), game.getWhite().getFullNameShort(), game.getBlack().getFullNameShort(), addedGame.getId()));
            }
        }
        gamesAdded++;

        if (gamesAdded % 1000 == 0) {
            System.out.println(gamesAdded + " games added");
        }
    }
}
