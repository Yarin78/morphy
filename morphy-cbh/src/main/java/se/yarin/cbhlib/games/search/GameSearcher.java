package se.yarin.cbhlib.games.search;

import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameLoader;
import se.yarin.chess.GameModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

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
    public Iterable<Hit> search() throws IOException {
        if (hasSearched) {
            throw new IllegalStateException("A search has already been executed");
        }
        hasSearched = true;

        for (SearchFilter filter : filters) {
            filter.initSearch();
        }

        int firstGameId = 1;

        for (SearchFilter filter : filters) {
            firstGameId = Math.max(firstGameId, filter.firstGameId());
        }

        log.info("Starting game search from game id " + firstGameId);
        Iterator<GameHeader> iterator = this.database.getHeaderBase().iterator(firstGameId);

        return () -> new HitIterator(iterator);
    }

    /**
     * Adds a new filter to the searcher. This can only be done before {@link #search()} has been called.
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


    private class HitIterator implements Iterator<Hit> {

        private Iterator<GameHeader> iterator;
        private Hit nextHit;

        private Random random = new Random();

        public HitIterator(Iterator<GameHeader> iterator) {
            this.iterator = iterator;
        }

        private void searchNext() throws IOException {
            while (this.iterator.hasNext()) {
                GameHeader current = this.iterator.next();
                if (current.isDeleted()) continue;

                boolean matches = true;
                for (SearchFilter filter : filters) {
                    if (!filter.matches(current)) {
                        matches = false;
                        break;
                    }
                }

                //if (current.isGuidingText()) continue;

                if (matches) {
                    this.nextHit = new Hit(current);
                    break;
                }
            }
        }

        @SneakyThrows  // TODO: fix
        @Override
        public boolean hasNext() {
            if (nextHit != null) {
                return true;
            }
            searchNext();
            return nextHit != null;
        }

        @SneakyThrows  // TODO: fix
        @Override
        public Hit next() {
            if (nextHit == null) {
                searchNext();
            }

            Hit hit = nextHit;
            nextHit = null;
            return hit;
        }
    }

    public class Hit {
        @Getter private final GameHeader gameHeader;

        public PlayerEntity getWhite() throws IOException {
            return database.getPlayerBase().get(gameHeader.getWhitePlayerId());
        }

        public PlayerEntity getBlack() throws IOException {
            return database.getPlayerBase().get(gameHeader.getBlackPlayerId());
        }

        public TournamentEntity getTournament() throws IOException {
            return database.getTournamentBase().get(gameHeader.getTournamentId());
        }

        public GameModel getModel() throws IOException, ChessBaseException {
            return gameLoader.getGameModel(gameHeader.getId());
        }

        public Hit(GameHeader gameHeader) {
            this.gameHeader = gameHeader;
        }

    }
}
