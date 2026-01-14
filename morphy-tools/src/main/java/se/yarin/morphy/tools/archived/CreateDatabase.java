package se.yarin.morphy.tools;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.annotations.GameQuotationAnnotation;
import se.yarin.cbhlib.annotations.SymbolAnnotation;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.util.GameGenerator;
import se.yarin.chess.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static se.yarin.chess.Chess.*;

/**
 * Creates a new ChessBase database and populates it with some games
 */
public class CreateDatabase {

    public static void main3(String[] args) throws IOException, ChessBaseInvalidDataException {
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

    public static void main2(String[] args) throws IOException, ChessBaseInvalidDataException {
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

    public static void main(String[] args) throws IOException, ChessBaseInvalidDataException {
        GameModel quoted = new GameModel();
        quoted.moves().root().addMove(E2, E4).addMove(E7, E5).addMove(G1,F3).addMove(B8, C6)
                .parent().addMove(D7, D6).addMove(D2, D4)
                .parent().parent().addMove(G8, F6);
        quoted.moves().root().mainNode().addAnnotation(new SymbolAnnotation(NAG.GOOD_MOVE));
        quoted.header().setWhite("Mardell");
        quoted.header().setWhiteElo(2100);
        quoted.header().setBlack("Kasparov");
        quoted.header().setBlackElo(2800);
        quoted.header().setEvent("test");
        quoted.header().setEventSite("Stockholm");
        quoted.header().setEco(new Eco("A01"));
        quoted.header().setDate(new Date(2016, 9, 1));

        GameModel game = new GameModel();
        game.moves().root().addMove(D2, D4).addMove(G8, F6).addMove(C2, C4);
        game.moves().root().mainNode().addAnnotation(new GameQuotationAnnotation(quoted));
        game.header().setWhite("Carlsen");
        game.header().setBlack("Caruana");

        Database db = Database.create(new File("testbases/tmp/Created/withquot3.cbh"));
        db.addGame(game);
        db.close();
    }
}
