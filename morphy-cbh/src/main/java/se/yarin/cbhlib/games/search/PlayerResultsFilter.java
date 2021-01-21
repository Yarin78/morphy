package se.yarin.cbhlib.games.search;

import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.PlayerEntity;
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
    public boolean matches(Game game) {
        if (game.isGuidingText()) {
            return false;
        }
        PlayerEntity player = null;
        if (game.getResult().equals(GameResult.WHITE_WINS) || game.getResult().equals(GameResult.WHITE_WINS_ON_FORFEIT)) {
            player = wins ? game.getWhite() : game.getBlack();
        } else if (game.getResult().equals(GameResult.BLACK_WINS) || game.getResult().equals(GameResult.BLACK_WINS_ON_FORFEIT)) {
            player = !wins ? game.getWhite() : game.getBlack();
        }
        return player != null && playerSearcher.matches(player);
    }
}
