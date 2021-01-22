package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SingleTournamentSearcher implements TournamentSearcher {
    private static final Logger log = LoggerFactory.getLogger(SingleTournamentSearcher.class);

    private static final int QUICK_SEARCH_MAX_HITS = 10000;
    private static final int QUICK_SEARCH_MIN_TRAVERSE = 10;
    private static final int QUICK_SEARCH_MAX_TIME_IN_MS = 50;

    private final TournamentBase tournamentBase;

    @Getter private final String name;
    @Getter private final int year;
    private final boolean caseSensitive;
    private final boolean exactMatch;
    private final String searchString;  // Logging purposes
    @Setter
    private Set<TournamentTimeControl> timeControls;
    @Setter
    private Set<TournamentType> types;
    @Setter
    private Set<String> places;

    public SingleTournamentSearcher(TournamentBase tournamentBase, String name, boolean caseSensitive, boolean exactMatch) {
        this.tournamentBase = tournamentBase;

        this.searchString = name;

        if (Pattern.matches("^[0-9]{4}.*", name)) {
            this.year = Integer.parseInt(name.substring(0, 4));
            this.name = name.substring(4).strip();
        } else if (Pattern.matches(".*[0-9]{4}$", name)) {
            this.year = Integer.parseInt(name.substring(name.length() - 4));
            this.name = name.substring(0, name.length() - 4).strip();
        } else {
            this.year = 0;
            this.name = name;
        }

        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private Stream<TournamentEntity> getBaseStream() {
        Stream<TournamentEntity> baseStream = tournamentBase.stream();
        if (this.year > 0 && this.caseSensitive) {
            baseStream = this.tournamentBase.prefixSearch(this.year, this.name);
        }
        return baseStream;
    }

    public List<TournamentEntity> quickSearch() {
        // The nature of this operation (aborting if taking too long) makes it unsuitable for streaming operations
        Iterator<TournamentEntity> stream = getBaseStream().iterator();
        // Start the timer after the initial prefix lookup since we might have a random high startup time
        long start = System.currentTimeMillis();
        ArrayList<TournamentEntity> result = new ArrayList<>();
        int steps = 0;
        while (stream.hasNext()) {
            TournamentEntity tournament = stream.next();
            steps += 1;
            if (matches(tournament)) {
                result.add(tournament);
                if (result.size() > QUICK_SEARCH_MAX_HITS) {
                    log.info(String.format("Tournament quick search for '%s' interrupted after %d ms due to too many hits (> %d)",
                            searchString, System.currentTimeMillis() - start, QUICK_SEARCH_MAX_HITS));
                    return null;
                }
            }
            if (steps >= QUICK_SEARCH_MIN_TRAVERSE) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > QUICK_SEARCH_MAX_TIME_IN_MS) {
                    log.info(String.format("Tournament quick search for '%s' interrupted after %d ms", searchString, elapsed));
                    return null;
                }
            }
        }
        log.info(String.format("Tournament quick search for '%s' finished in %d ms with %d hits",
                searchString, System.currentTimeMillis() - start, result.size()));
        return result;
    }

    public Stream<TournamentSearcher.Hit> search() {
        return getBaseStream().filter(this::matches).map(TournamentSearcher.Hit::new);
    }

    public boolean matches(TournamentEntity tournament) {
        return matches(tournament.getTitle(), name) &&
                (year != 0 || tournament.getDate().year() == year) &&
                (timeControls == null || timeControls.contains(tournament.getTimeControl())) &&
                (types == null || types.contains(tournament.getType())) &&
                (places == null || places.stream().anyMatch(tournament.getPlace()::startsWith));
    }

    private boolean matches(String tournamentName, String searchName) {
        if (exactMatch) {
            return caseSensitive ? tournamentName.equals(searchName) : tournamentName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? tournamentName.startsWith(searchName) : tournamentName.toLowerCase().startsWith(searchName.toLowerCase());
    }
}
