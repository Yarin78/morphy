package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.games.GameHeader;

import java.io.IOException;
import java.util.List;

/**
 * A search filter that returns games played by a player with a matching name.
 */
public class PlayerFilter implements SearchFilter {
    private static final int MAX_PLAYERS = 20;

    private final Database database;
    private final PlayerSearcher playerSearcher;
    private List<PlayerEntity> players;
    private final PlayerColor color;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public PlayerFilter(Database database, PlayerSearcher playerSearcher, PlayerColor color) {
        this.database = database;
        this.playerSearcher = playerSearcher;
        this.color = color;
    }

    @Override
    public Database getDatabase() {
        return this.database;
    }

    public void initSearch() throws IOException {
        // If we can quickly determine if there are few enough players in the database that matches the search string,
        // we can get an improved searched
        List<PlayerEntity> players = playerSearcher.quickSearch();
        if (players != null && players.size() < MAX_PLAYERS) {
            this.players = players;
        }
    }

    @Override
    public int countEstimate() {
        if (players == null) {
            return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
        } else {
            int count = 0;
            for (PlayerEntity player : players) {
                count += player.getCount();
            }
            return count;
        }
    }

    @Override
    public int firstGameId() {
        if (players == null) {
            return 1;
        } else {
            int first = Integer.MAX_VALUE;
            for (PlayerEntity player : players) {
                first = Math.min(first, player.getFirstGameId());
            }
            return first;
        }
    }

    @Override
    public boolean matches(GameHeader gameHeader) throws IOException {
        if (gameHeader.isGuidingText()) {
            return false;
        }

        if (color != PlayerColor.BLACK) {
            PlayerEntity whitePlayer = database.getPlayerBase().get(gameHeader.getWhitePlayerId());
            if (playerSearcher.matches(whitePlayer)) {
                return true;
            }
        }
        if (color != PlayerColor.WHITE) {
            PlayerEntity blackPlayer = database.getPlayerBase().get(gameHeader.getBlackPlayerId());
            if (playerSearcher.matches(blackPlayer)) {
                return true;
            }
        }
        return false;
    }
}
