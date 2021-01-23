package se.yarin.morphy.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;

public class DatabaseBuilder extends GameConsumerBase {
    private static final Logger log = LogManager.getLogger();

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
        GameModel model;
        try {
            model = database.getGameModel(game);
        } catch (ChessBaseException e) {
            log.warn("Failed to get game " + game.getId() + " in the searched database");
            return;
        }

        try {
            this.database.addGame(model);
        } catch (ChessBaseInvalidDataException e) {
            log.warn("Failed to add game " + game.getId() + " in the searched database in the output database", e);
        }
        gamesAdded++;
        // System.out.printf("Game %d added (%s - %s )%n", hit.getGameHeader().getId(), model.header().getWhite(), model.header().getBlack());

        if (gamesAdded % 1000 == 0) {
            System.out.println(gamesAdded + " games added");
        }
    }
}
