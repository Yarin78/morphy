package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * Interface for classes responsible for searching and matching tournaments in a database
 */
public interface TournamentSearcher {
    /**
     * Gets a stream of all matching tournaments
     * @return a stream with matching tournaments
     */
    Stream<TournamentSearcher.Hit> search();

    /**
     * If it's possible to find all matching tournaments quickly, return them; otherwise null.
     *
     * @return a list of all matching tournaments, or null if a quick search couldn't provide a complete result quickly enough.
     */
    List<TournamentEntity> quickSearch();

    /**
     * Determines if a specific tournament matches the search filter
     * @param tournament the tournament to check
     * @return true if the given tournament matches
     */
    boolean matches(@NonNull TournamentEntity tournament);

    /**
     * A search hit
     */
    class Hit {
        @Getter
        private final TournamentEntity tournament;

        public Hit(TournamentEntity tournament) {
            this.tournament = tournament;
        }
    }
}
