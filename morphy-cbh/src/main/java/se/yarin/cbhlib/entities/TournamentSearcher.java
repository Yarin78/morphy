package se.yarin.cbhlib.entities;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class TournamentSearcher {
    private static final Logger log = LoggerFactory.getLogger(TournamentSearcher.class);

    private final TournamentBase tournamentBase;

    @Getter private final String name;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentSearcher(TournamentBase tournamentBase, String name, boolean caseSensitive, boolean exactMatch) {
        this.tournamentBase = tournamentBase;
        this.name = name;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private Stream<TournamentEntity> getBaseStream() {
        return tournamentBase.stream();
    }

    public Stream<TournamentSearcher.Hit> search() {
        return getBaseStream().filter(this::matches).map(TournamentSearcher.Hit::new);
    }

    public boolean matches(TournamentEntity tournament) {
        return matches(tournament.getTitle(), name);
    }

    private boolean matches(String tournamentName, String searchName) {
        if (exactMatch) {
            return caseSensitive ? tournamentName.equals(searchName) : tournamentName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? tournamentName.startsWith(searchName) : tournamentName.toLowerCase().startsWith(searchName.toLowerCase());
    }

    public class Hit {
        @Getter
        private final TournamentEntity tournament;

        public Hit(TournamentEntity tournament) {
            this.tournament = tournament;
        }
    }
}
