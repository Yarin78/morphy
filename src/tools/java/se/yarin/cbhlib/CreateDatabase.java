package se.yarin.cbhlib;

import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Creates a new ChessBase database and populates it with some games
 */
public class CreateDatabase {

    public static void main(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Created/random3.cbh"));
        GameGenerator gameGenerator = new GameGenerator();

        Random random = new Random();

        int noOps = 10000, maxGames = 10000;

        for (int i = 0; i < noOps; i++) {
            int gameId = random.nextInt(maxGames) + 1;
            GameModel game = gameGenerator.getRandomGame();

            if (gameId <= db.getHeaderBase().size()) {
                db.replaceGame(gameId, game);
            } else {
                db.addGame(game);
            }
        }
        db.close();
    }

    public static void main2(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Created/db8.cbh"));
        GameGenerator gameGenerator = new GameGenerator();

        GameModel[] games = new GameModel[3];
        for (int i = 0; i < games.length; i++) {
            games[i] = gameGenerator.getRandomGame(false, false);
        }
        Arrays.sort(games, (o1, o2) -> o1.moves().countPly(false) - o2.moves().countPly(false));

        db.addGame(games[0]);
        db.addGame(games[1]);
        db.replaceGame(1, games[2]);

        db.close();
    }
}
