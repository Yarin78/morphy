package se.yarin.cbhlib.games.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameLoader;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.chess.GameModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Class responsible for searching for games in a database that matches one or more search filters.
 *
 * A game in the database need to match all filters added to the searcher for the game to show up in the result.
 */
public class GameSearcher {
    private static final Logger log = LoggerFactory.getLogger(GameSearcher.class);

    private final Database database;
    private final GameLoader gameLoader;
    private final ArrayList<SearchFilter> filters;
    private boolean hasSearched = false;

    public GameSearcher(Database database) {
        this.database = database;
        this.gameLoader = new GameLoader(database);
        this.filters = new ArrayList<>();
    }

    /**
     * Performs the actual search. The search is done lazily.
     * This function can only be called once per instance.
     * @return an iterable over all hits.
     */
    public Stream<Hit> streamSearch() {
        return streamSearch(null);
    }

    /**
     * Performs the actual search. The search is done lazily.
     * This function can only be called once per instance.
     * @param progressUpdater an optional updater that will be called after every processed game with the gameId
     *                        of the most recent processed game (but not if it's filtered out by a {@link SerializedGameHeaderFilter}).
     * @return an iterable over all hits.
     */
    public Stream<Hit> streamSearch(Consumer<Integer> progressUpdater) {
        if (hasSearched) {
            throw new IllegalStateException("A search has already been executed");
        }
        hasSearched = true;

        ArrayList<SerializedGameHeaderFilter> serializedFilters = new ArrayList<>();
        for (SearchFilter filter : filters) {
            filter.initSearch();
            if (filter instanceof SerializedGameHeaderFilter) {
                serializedFilters.add((SerializedGameHeaderFilter) filter);
            }
        }
        SerializedGameHeaderFilter rawFilter = null;
        if (serializedFilters.size() == 1) {
            rawFilter = serializedFilters.get(0);
        } else if (serializedFilters.size() > 1) {
            rawFilter = SerializedGameHeaderFilter.chain(serializedFilters);
        }

        int firstGameId = 1;

        for (SearchFilter filter : filters) {
            firstGameId = Math.max(firstGameId, filter.firstGameId());
        }

        log.info("Starting game search from game id " + firstGameId);

        Stream<GameHeader> searchStream = this.database.getHeaderBase()
                .stream(firstGameId, rawFilter);
        if (progressUpdater != null) {
            searchStream = searchStream.peek(game -> progressUpdater.accept(game.getId()));
        }
        return searchStream
                .filter(this::matches)
                .map(Hit::new);
    }

    /**
     * Performs the actual search.
     * @return the search result
     */
    public SearchResult search() {
        return search(0, true);
    }

    /**
     * Performs the actual search.
     * @param limit maximum number of hits to return
     * @param countAll if true, also count the total number of hits
     * @return the search result
     */
    public SearchResult search(int limit, boolean countAll) {
        return search(limit, countAll, null, null);
    }

    /**
     * Performs the actual search.
     * @param limit maximum number of hits to consume/return
     * @param countAll if true, also count the total number of hits
     * @param hitConsumer an optional consumer of each hit, called as soon as a hit is found
     * @return the search result
     */
    public SearchResult search(int limit, boolean countAll, Consumer<Hit> hitConsumer, Consumer<Integer> progressUpdater) {
        AtomicInteger hitsFound = new AtomicInteger(0), hitsConsumed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        Stream<Hit> searchStream = streamSearch(progressUpdater);
        if (!countAll && limit > 0) {
            searchStream = searchStream.limit(limit);
        }

        ArrayList<Hit> result = new ArrayList<>();
        searchStream.forEachOrdered(hit -> {
            if (hitsFound.incrementAndGet() <= limit || limit == 0) {
                hitsConsumed.incrementAndGet();
                if (hitConsumer != null) {
                    hitConsumer.accept(hit);
                } else {
                    result.add(hit);
                }
            }
        });

        return new SearchResult(hitsFound.get(), hitsConsumed.get(), result, System.currentTimeMillis() - startTime);
    }

    /**
     * Adds a new filter to the searcher. This can only be done before {@link #search()} ()} has been called.
     * @param filter the filter to add
     */
    public void addFilter(SearchFilter filter) {
        if (hasSearched) {
            throw new IllegalStateException("A search has already started");
        }
        if (filter.getDatabase() != database) {
            throw new IllegalArgumentException("The database in the filter doesn't match the database in the searcher");
        }
        this.filters.add(filter);
    }

    /**
     * Returns the total number of games in the database
     */
    public int getTotal() {
        return database.getHeaderBase().size();
    }

    private boolean matches(GameHeader current) {
        return !current.isDeleted() && filters.stream().allMatch(filter -> filter.matches(current));
    }

    @Data
    @AllArgsConstructor
    public class SearchResult {
        @Getter
        private int totalHits; // If countAll is false and a limit was used, then the counting stopped at limit
        @Getter
        private int consumedHits;
        @Getter
        private List<Hit> hits; // Only set if no Consumer<Hit> was given
        @Getter
        private long elapsedTime;
    }

    public class Hit {
        @Getter private final GameHeader gameHeader;

        public PlayerEntity getWhite() {
            return database.getPlayerBase().get(gameHeader.getWhitePlayerId());
        }

        public PlayerEntity getBlack() {
            return database.getPlayerBase().get(gameHeader.getBlackPlayerId());
        }

        public TournamentEntity getTournament() {
            return database.getTournamentBase().get(gameHeader.getTournamentId());
        }

        public GameModel getModel() throws ChessBaseException {
            return gameLoader.getGameModel(gameHeader.getId());
        }

        public Hit(GameHeader gameHeader) {
            this.gameHeader = gameHeader;
        }

    }
}
