package se.yarin.morphy.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Class responsible for searching and matching players in a database given a partial name
 */
public class SinglePlayerSearcher implements PlayerSearcher {
    private static final Logger log = LoggerFactory.getLogger(SinglePlayerSearcher.class);

    private static final int QUICK_SEARCH_MAX_HITS = 10000;
    private static final int QUICK_SEARCH_MIN_TRAVERSE = 10;
    private static final int QUICK_SEARCH_MAX_TIME_IN_MS = 50;

    private final PlayerIndex playerIndex;
    //@Getter
    private final String firstName;
    //@Getter
    private final String lastName;
    private final boolean caseSensitive;
    private final boolean exactMatch;
    private final String searchString;  // Logging purposes

    private static String resolveFirstName(String name) {
        if (name.contains(",")) {
            return name.substring(name.indexOf(",") + 1).strip();
        }
        if (name.contains(" ")) {
            return name.substring(0, name.indexOf(" ")).strip();
        }
        return "";
    }

    private static String resolveLastName(String name) {
        if (name.contains(",")) {
            return name.substring(0, name.indexOf(",")).strip();
        }
        if (name.contains(" ")) {
            return name.substring(name.indexOf(" ") + 1).strip();
        }
        return name;
    }

    public SinglePlayerSearcher(PlayerIndex playerIndex, String name, boolean caseSensitive, boolean exactMatch) {
        this(playerIndex, resolveLastName(name), resolveFirstName(name), caseSensitive, exactMatch);
    }

    public SinglePlayerSearcher(PlayerIndex playerIndex, String lastName, String firstName, boolean caseSensitive, boolean exactMatch) {
        this.playerIndex = playerIndex;
        this.firstName = firstName;
        this.lastName = lastName;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
        this.searchString = firstName.isEmpty() ? lastName : String.format("%s, %s", lastName, firstName);
    }

    private Stream<Player> getBaseStream() {
        Stream<Player> baseStream = playerIndex.stream();
        if (this.caseSensitive) {
            baseStream = this.playerIndex.prefixSearch(this.lastName);
        }
        return baseStream;
    }

    public Stream<Hit> search() {
        return getBaseStream().filter(this::matches).map(Hit::new);
    }

    public List<Player> quickSearch() {
        // The nature of this operation (aborting if taking too long) makes it unsuitable for streaming operations
        Iterator<Player> stream = getBaseStream().iterator();
        // Start the timer after the initial prefix lookup since we might have a random high startup time
        long start = System.currentTimeMillis();
        ArrayList<Player> result = new ArrayList<>();
        int steps = 0;
        while (stream.hasNext()) {
            Player player = stream.next();
            steps += 1;
            if (matches(player)) {
                result.add(player);
                if (result.size() > QUICK_SEARCH_MAX_HITS) {
                    log.info(String.format("Player quick search for '%s' interrupted after %d ms due to too many hits (> %d)",
                            searchString, System.currentTimeMillis() - start, QUICK_SEARCH_MAX_HITS));
                    return null;
                }
            }
            if (steps >= QUICK_SEARCH_MIN_TRAVERSE) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > QUICK_SEARCH_MAX_TIME_IN_MS) {
                    log.info(String.format("Player quick search for '%s' interrupted after %d ms", searchString, elapsed));
                    return null;
                }
            }
        }
        log.info(String.format("Player quick search for '%s' finished in %d ms with %d hits",
                searchString, System.currentTimeMillis() - start, result.size()));
        return result;
    }

    public boolean matches(Player player) {
        return matches(player.lastName(), lastName) && matches(player.firstName(), firstName);
    }

    private boolean matches(String playerName, String searchName) {
        if (exactMatch) {
            return caseSensitive ? playerName.equals(searchName) : playerName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? playerName.startsWith(searchName) : playerName.toLowerCase().startsWith(searchName.toLowerCase());
    }
}

