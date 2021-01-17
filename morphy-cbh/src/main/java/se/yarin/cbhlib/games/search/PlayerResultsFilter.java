package se.yarin.cbhlib.games.search;

import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameResult;

public class PlayerResultsFilter extends SearchFilterBase implements SearchFilter {
    private final boolean wins;
    private final PlayerSearcher playerSearcher;

    public PlayerResultsFilter(Database db, String results, @NonNull PlayerSearcher playerSearcher) {
        super(db);
        this.playerSearcher = playerSearcher;
        switch (results.toLowerCase()) {
            case "win" -> wins = true;
            case "loss" -> wins = false;
            default -> throw new IllegalArgumentException("Invalid results: " + results);
        }
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        if (gameHeader.isGuidingText()) {
            return false;
        }
        int playerId = -1;
        if (gameHeader.getResult().equals(GameResult.WHITE_WINS) || gameHeader.getResult().equals(GameResult.WHITE_WINS_ON_FORFEIT)) {
            playerId = wins ? gameHeader.getWhitePlayerId() : gameHeader.getBlackPlayerId();
        } else if (gameHeader.getResult().equals(GameResult.BLACK_WINS) || gameHeader.getResult().equals(GameResult.BLACK_WINS_ON_FORFEIT)) {
            playerId = !wins ? gameHeader.getWhitePlayerId() : gameHeader.getBlackPlayerId();
        }
        if (playerId < 0) {
            return false;
        }
        return playerSearcher.matches(getDatabase().getPlayerBase().get(playerId));
    }
}
