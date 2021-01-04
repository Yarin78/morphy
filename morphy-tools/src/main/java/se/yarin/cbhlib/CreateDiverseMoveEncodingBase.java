package se.yarin.cbhlib;

import se.yarin.cbhlib.util.TestGames;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;

import java.io.File;
import java.io.IOException;

/**
 * Creates a database with games using all supported move encodings
 */
public class CreateDiverseMoveEncodingBase {

    public static void main(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Weird Move Encodings/multimode3.cbh"));

        addGame(db, TestGames.getCrazyGame(), "crazyGame");
        addGame(db, TestGames.getEndGame(), "endGame");
        // Variations doesn't work in ChessBase 13 in mode 1,3,5,7 - probably a bug in ChessBase
        addGame(db, TestGames.getVariationGame(), "variations");

        db.close();
    }

    private static void addGame(Database db, GameMovesModel moves, String title) throws IOException {
        for (int i = 0; i < 8; i++) {
            db.getMovesBase().setEncodingMode(i);

            GameHeaderModel header = new GameHeaderModel();
            GameModel gameModel = new GameModel(header, moves);

            gameModel.header().setWhite(title + ", mode " + i);

            db.addGame(gameModel);
        }
    }
}
