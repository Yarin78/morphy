package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.PlayerEntity;

/**
 * A search filter that only returns games played by a specific player.
 */
public class SinglePlayerFilter extends SearchFilterBase implements SearchFilter {

    private final PlayerEntity player;
    private final PlayerColor color;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public SinglePlayerFilter(Database database, PlayerEntity player, PlayerColor color) {
        super(database);
        this.player = player;
        this.color = color;
    }

    @Override
    public int countEstimate() {
        int count = this.player.getCount();
        if (count > 0 && this.color != PlayerColor.ANY) {
            count = (count + 1) / 2;
        }
        return count;
    }

    @Override
    public int firstGameId() {
        return this.player.getFirstGameId();
    }

    @Override
    public boolean matches(Game game) {
        boolean isWhite = game.getWhitePlayerId() == this.player.getId();
        boolean isBlack = game.getBlackPlayerId() == this.player.getId();
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }
}
