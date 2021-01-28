package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GameIdFilter extends SearchFilterBase {
    private final Set<Integer> ids;

    public GameIdFilter(Database database, Collection<Integer> ids) {
        super(database);
        this.ids = new HashSet<>(ids);
    }

    @Override
    public int firstGameId() {
        return Collections.min(ids);
    }

    @Override
    public int countEstimate() {
        return ids.size();
    }

    @Override
    public boolean matches(Game game) {
        return ids.contains(game.getId());
    }
}
