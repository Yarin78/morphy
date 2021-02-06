package se.yarin.cbhlib.games.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Class responsible for searching for games in a database that matches one or more search filters.
 *
 * A game in the database need to match all filters added to the searcher for the game to show up in the result.
 */
public class GameSearcher {
    private static final Logger log = LoggerFactory.getLogger(GameSearcher.class);

    private final Database database;
    private final ArrayList<SearchFilter> filters;
    private boolean hasSearched = false;

    public GameSearcher(Database database) {
        this.database = database;
        this.filters = new ArrayList<>();
    }

    /**
     * Performs the actual search. The search is done lazily.
     * This function can only be called once per instance.
     * @return an iterable over all hits.
     */
    public Stream<Game> streamSearch() {
        return streamSearch(null);
    }

    public Iterable<Game> iterableSearch() {
        return iterableSearch(null);
    }

    public Stream<Game> streamSearch(Consumer<Integer> progressUpdater) {
        Iterable<Game> games = iterableSearch(progressUpdater);
        Stream<Game> stream = StreamSupport.stream(games.spliterator(), false);
        if (progressUpdater != null) {
            stream = stream.peek(game -> progressUpdater.accept(game.getId()));
        }
        return stream;
    }

    /**
     * Performs the actual search. The search is done lazily.
     * This function can only be called once per instance.
     * @param progressUpdater an optional updater that will be called after every processed game with the gameId
     *                        of the most recent processed game (but not if it's filtered out by a {@link SerializedGameHeaderFilter}).
     * @return an iterable over all hits.
     */
    public Iterable<Game> iterableSearch(Consumer<Integer> progressUpdater) {
        if (hasSearched) {
            throw new IllegalStateException("A search has already been executed");
        }
        hasSearched = true;

        // Combine the filters and extract the raw filters that are used in the iterators
        ArrayList<SerializedGameHeaderFilter> serializedFilters = new ArrayList<>();
        ArrayList<SerializedExtendedGameHeaderFilter> serializedExtendedFilters = new ArrayList<>();
        for (SearchFilter filter : filters) {
            filter.initSearch();
            if (filter instanceof SerializedGameHeaderFilter) {
                serializedFilters.add((SerializedGameHeaderFilter) filter);
            }
            if (filter instanceof SerializedExtendedGameHeaderFilter) {
                serializedExtendedFilters.add((SerializedExtendedGameHeaderFilter) filter);
            }
        }
        SerializedGameHeaderFilter rawFilter = null;
        SerializedExtendedGameHeaderFilter rawExtendedFilter = null;
        if (serializedFilters.size() == 1) {
            rawFilter = serializedFilters.get(0);
        } else if (serializedFilters.size() > 1) {
            rawFilter = SerializedGameHeaderFilter.chain(serializedFilters);
        }
        if (serializedExtendedFilters.size() == 1) {
            rawExtendedFilter = serializedExtendedFilters.get(0);
        } else if (serializedExtendedFilters.size() > 1) {
            rawExtendedFilter = SerializedExtendedGameHeaderFilter.chain(serializedExtendedFilters);
        }

        int firstGameId = 1;

        for (SearchFilter filter : filters) {
            firstGameId = Math.max(firstGameId, filter.firstGameId());
        }

        log.debug("Starting game search from game id " + firstGameId);

        Iterator<GameHeader> headerIterator = this.database.getHeaderBase()
                .stream(firstGameId, rawFilter)
                .iterator();

        // If there are no headers in the extended base (old database), the search should work anyway
        final Iterator<ExtendedGameHeader> extendedHeaderIterator =
            this.database.getExtendedHeaderBase().size() > 0 ?
                this.database.getExtendedHeaderBase()
                        .stream(firstGameId, rawExtendedFilter)
                        .iterator() : null;

        return () -> new SearchIterator(headerIterator, extendedHeaderIterator, progressUpdater);
    }

    public class SearchIterator implements Iterator<Game> {
        private final Iterator<GameHeader> leftIterator;
        private final Iterator<ExtendedGameHeader> rightIterator;
        private final Consumer<Integer> progressUpdater;
        private Game cache;
        private boolean iteratorDone;
        private GameHeader currentLeft;
        private ExtendedGameHeader currentRight;

        public SearchIterator(@NonNull Iterator<GameHeader> leftIterator,
                              Iterator<ExtendedGameHeader> rightIterator,
                              Consumer<Integer> progressUpdater) {
            this.leftIterator = leftIterator;
            this.rightIterator = rightIterator;
            this.progressUpdater = progressUpdater;
            stepLeft();
            stepRight();

            this.cache = null;
        }

        private void stepLeft() {
            if (leftIterator.hasNext()) {
                currentLeft = leftIterator.next();
                if (progressUpdater != null) {
                    progressUpdater.accept(currentLeft.getId());
                }
            } else {
                iteratorDone = true;
            }
        }

        private void stepRight() {
            if (rightIterator == null) {
                return;
            }
            if (rightIterator.hasNext()) {
                currentRight = rightIterator.next();
                if (progressUpdater != null) {
                    progressUpdater.accept(currentRight.getId());
                }
            } else {
                iteratorDone = true;
            }
        }

        public void ensureCache() {
            if (cache != null) {
                return;
            }
            while (!iteratorDone) {
                int diff = currentRight == null ? 0 : currentLeft.getId() - currentRight.getId();
                if (diff < 0) {
                    stepLeft();
                } else if (diff > 0) {
                    stepRight();
                } else {
                    Game game = new Game(database, currentLeft, currentRight == null ? ExtendedGameHeader.empty(currentLeft) : currentRight);
                    stepLeft();
                    stepRight();
                    if (matches(game)) {
                        cache = game;
                        return;
                    }
                }
            }
        }


        @Override
        public boolean hasNext() {
            ensureCache();
            return cache != null;
        }

        @Override
        public Game next() {
            ensureCache();
            if (cache == null) {
                throw new NoSuchElementException();
            }
            Game game = cache;
            cache = null;
            return game;
        }
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
     * @param gameConsumer an optional consumer of each hit, called as soon as a hit is found
     * @return the search result
     */
    public SearchResult search(int limit, boolean countAll, Consumer<Game> gameConsumer, Consumer<Integer> progressUpdater) {
        AtomicInteger hitsFound = new AtomicInteger(0), hitsConsumed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        Stream<Game> searchStream = streamSearch(progressUpdater);
        if (!countAll && limit > 0) {
            searchStream = searchStream.limit(limit);
        }

        ArrayList<Game> result = new ArrayList<>();
        searchStream.forEachOrdered(game -> {
            if (hitsFound.incrementAndGet() <= limit || limit == 0) {
                hitsConsumed.incrementAndGet();
                if (gameConsumer != null) {
                    gameConsumer.accept(game);
                } else {
                    result.add(game);
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

    private boolean matches(Game current) {
        return !current.isDeleted() && filters.stream().allMatch(filter -> filter.matches(current));
    }

    @Data
    @AllArgsConstructor
    public static class SearchResult {
        @Getter
        private int totalGames; // If countAll is false and a limit was used, then the counting stopped at limit
        @Getter
        private int consumedGames;
        @Getter
        private List<Game> games; // Only set if no Consumer<Game> was given
        @Getter
        private long elapsedTime;
    }
}
