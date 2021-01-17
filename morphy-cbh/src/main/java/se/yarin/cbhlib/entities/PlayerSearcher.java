package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;

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
    Stream<PlayerSearcher.Hit> search();

    /**
     * If it's possible to find all matching players quickly, return them; otherwise null.
     *
     * @return a list of all matching players, or null if a quick search couldn't provide a complete result quickly enough.
     */
    List<PlayerEntity> quickSearch();

    /**
     * Determines if a specific player matches the search filter
     * @param player the player to check
     * @return true if the given player matches
     */
    boolean matches(@NonNull PlayerEntity player);

    /**
     * A search hit
     */
    class Hit {
        @Getter
        private final PlayerEntity player;

        public Hit(PlayerEntity player) {
            this.player = player;
        }
    }
}
