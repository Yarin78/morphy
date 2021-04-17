package se.yarin.morphy.games;

import se.yarin.morphy.Database;
import se.yarin.morphy.Game;

import java.util.ArrayList;
import java.util.List;

public class GameSearcher {
    private final Database database;

    public GameSearcher(Database database) {
        this.database = database;
    }

    public List<Game> getAll() {
        // TODO: Get rid of this
        List<GameHeader> allGameHeaders = this.database.gameHeaderIndex().getAll();
        List<ExtendedGameHeader> allExtendedGameHeaders = this.database.extendedGameHeaderStorage().getAll();
        ArrayList<Game> games = new ArrayList<>(allGameHeaders.size());
        for (int i = 0; i < allGameHeaders.size(); i++) {
            GameHeader gameHeader = allGameHeaders.get(i);
            ExtendedGameHeader extendedGameHeader = i < allExtendedGameHeaders.size() ? allExtendedGameHeaders.get(i) : ExtendedGameHeader.empty(gameHeader);
            games.add(new Game(database, gameHeader, extendedGameHeader));
        }
        return games;
    }
}
