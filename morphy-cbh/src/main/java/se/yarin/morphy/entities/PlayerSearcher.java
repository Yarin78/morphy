package se.yarin.morphy.entities;


import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * Interface for classes responsible for searching and matching players in a database
 */
public interface PlayerSearcher {

    /**
     * Gets a stream of all matching players
     * @return a stream with matching players
     */
    Stream<Hit> search();

    /**
     * If it's possible to find all matching players quickly, return them; otherwise null.
     *
     * @return a list of all matching players, or null if a quick search couldn't provide a complete result quickly enough.
     */
    List<Player> quickSearch();

    /**
     * Determines if a specific player matches the search filter
     * @param player the player to check
     * @return true if the given player matches
     */
    boolean matches(@NotNull Player player);

    /**
     * A search hit
     */
    class Hit {
        private final Player player;

        public Player getPlayer() {
            return player;
        }

        public Hit(Player player) {
            this.player = player;
        }
    }
}
