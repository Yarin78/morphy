package se.yarin.cbhlib.entities;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.parser.Expr;
import se.yarin.cbhlib.util.parser.Interpreter;
import se.yarin.chess.Date;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TournamentSearcher {
    private static final Logger log = LoggerFactory.getLogger(TournamentSearcher.class);

    private static final int QUICK_SEARCH_MAX_HITS = 10000;
    private static final int QUICK_SEARCH_MIN_TRAVERSE = 10;
    private static final int QUICK_SEARCH_MAX_TIME_IN_MS = 50;

    @NonNull private final TournamentBase tournamentBase;

    @Getter private String name = "";
    @Getter int year; // Only set as a side effect of typing a year in the name of a tournament search string
    @Setter int minCategory;
    @Setter int minRounds;
    @Setter int maxRounds = 1000;

    @Setter private Date fromDate = Date.unset();
    @Setter private Date toDate = Date.unset();

    private boolean caseSensitive = true;
    private boolean exactMatch = false;
    private String searchString = "";  // Logging purposes
    @Setter
    private Set<TournamentTimeControl> timeControls;
    @Setter
    private Set<TournamentType> types;
    @Setter
    private Set<String> places;
    @Setter
    private Set<Nation> nations;
    @Setter
    private boolean teamsOnly = false;

    @Setter
    private Collection<TournamentEntity> manual;

    private final List<Expr> rawExpressions = new ArrayList<>();

    public void addExpression(Expr expression) {
        rawExpressions.add(expression);
    }

    @Data
    @AllArgsConstructor
    public class SearchResult {
        @Getter
        private int totalTournaments; // If countAll is false and a limit was used, then the counting stopped at limit
        @Getter
        private int consumedTournaments;
        @Getter
        private List<TournamentEntity> tournaments; // Only set if no Consumer<Tournament> was given
        @Getter
        private long elapsedTime;
    }

    public TournamentSearcher(@NonNull TournamentBase tournamentBase) {
        this.tournamentBase = tournamentBase;
    }

    public TournamentSearcher(@NonNull TournamentBase tournamentBase, TournamentEntity tournament) {
        this(tournamentBase);
        this.manual = Arrays.asList(tournament);
    }

    public TournamentSearcher(@NonNull TournamentBase tournamentBase, String name, boolean caseSensitive, boolean exactMatch) {
        this.tournamentBase = tournamentBase;
        setSearchString(name, caseSensitive, exactMatch);
    }

    public void setSearchString(String name, boolean caseSensitive, boolean exactMatch) {
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

    private Stream<TournamentEntity> getBaseStream(boolean sortByYearTitle) {
        if (manual != null) {
            return manual.stream();
        }

        Stream<TournamentEntity> baseStream = sortByYearTitle
                ? tournamentBase.streamOrderedAscending()
                : tournamentBase.stream();
        if (this.caseSensitive) {
            if (this.year > 0) {
                baseStream = this.tournamentBase.prefixSearch(this.year, this.name);
            } else if (this.fromDate.year() > 0) {
                int endYear = this.toDate.year() == 0 ? 9999 : this.toDate.year();
                baseStream = this.tournamentBase.rangeSearch(this.fromDate.year(), endYear);
            }
        }

        return baseStream;
    }

    /**
     * If it's possible to find all matching tournaments quickly, return them; otherwise null.
     *
     * @return a list of all matching tournaments, or null if a quick search couldn't provide a complete result quickly enough.
     */
    public List<TournamentEntity> quickSearch() {
        // The nature of this operation (aborting if taking too long) makes it unsuitable for streaming operations
        Iterator<TournamentEntity> stream = getBaseStream(false).iterator();
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

    /**
     * Performs the actual search.
     * @param limit maximum number of hits to consume/return
     * @param countAll if true, also count the total number of hits
     * @param sortByYearTitle if true, sorts by year/title; otherwise by id (if possible, depends on filters)
     * @param tournamentConsumer an optional consumer of each hit, called as soon as a hit is found
     * @return the search result
     */
    public SearchResult search(int limit, boolean countAll, boolean sortByYearTitle, Consumer<TournamentEntity> tournamentConsumer) {
        AtomicInteger hitsFound = new AtomicInteger(0), hitsConsumed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        Stream<TournamentEntity> searchStream = getBaseStream(sortByYearTitle).filter(this::matches);
        if (!countAll && limit > 0) {
            searchStream = searchStream.limit(limit);
        }

        ArrayList<TournamentEntity> result = new ArrayList<>();
        searchStream.forEachOrdered(tournament -> {
            if (hitsFound.incrementAndGet() <= limit || limit == 0) {
                hitsConsumed.incrementAndGet();
                if (tournamentConsumer != null) {
                    tournamentConsumer.accept(tournament);
                } else {
                    result.add(tournament);
                }
            }
        });

        return new TournamentSearcher.SearchResult(hitsFound.get(), hitsConsumed.get(), result, System.currentTimeMillis() - startTime);
    }

    /**
     * Determines if a specific tournament matches the search filter
     * @param tournament the tournament to check
     * @return true if the given tournament matches
     */
    public boolean matches(TournamentEntity tournament) {
        if (rawExpressions.size() > 0) {
            Interpreter interpreter = new Interpreter(tournamentBase.getRaw(tournament.getId()));
            if (!rawExpressions.stream().allMatch(expr -> (boolean) interpreter.evaluate(expr))) {
                return false;
            }
        }

        return matches(tournament.getTitle(), name) &&
                (manual == null || manual.contains(tournament)) &&
                (tournament.getCategory() >= minCategory) &&
                (year == 0 || tournament.getDate().year() == year) &&
                (fromDate.isUnset() || fromDate.compareTo(tournament.getDate()) <= 0) &&
                (toDate.isUnset() || toDate.compareTo(tournament.getDate()) >= 0) &&
                (timeControls == null || timeControls.contains(tournament.getTimeControl())) &&
                (types == null || types.contains(tournament.getType())) &&
                (nations == null || nations.contains(tournament.getNation())) &&
                tournament.getRounds() >= minRounds &&
                tournament.getRounds() <= maxRounds &&
                (!teamsOnly || tournament.isTeamTournament()) &&
                (places == null || places.stream().anyMatch(tournament.getPlace()::startsWith));
    }

    private boolean matches(String tournamentName, String searchName) {
        if (exactMatch) {
            return caseSensitive ? tournamentName.equals(searchName) : tournamentName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? tournamentName.startsWith(searchName) : tournamentName.toLowerCase().startsWith(searchName.toLowerCase());
    }


}
