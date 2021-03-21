package se.yarin.morphy.entities;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allows you to search for multiple players at once, separating the names with |
 * Each name should ideally match exactly single player in the database for quickSearch to work.
 */
public class MultiPlayerSearcher implements PlayerSearcher {
    private static final Logger log = LoggerFactory.getLogger(MultiPlayerSearcher.class);

    private final PlayerIndex playerBase;
    private final List<String> names;

    public MultiPlayerSearcher(PlayerIndex playerBase, String names) {
        this.playerBase = playerBase;
        this.names = Arrays.stream(names.split("\\|")).map(String::strip).collect(Collectors.toList());
    }

    public Stream<Hit> search() {
        return playerBase.stream().filter(this::matches).map(MultiPlayerSearcher.Hit::new);
    }

    @Override
    public List<Player> quickSearch() {
        boolean notUnique = false;
        long start = System.currentTimeMillis();
        ArrayList<Player> players = new ArrayList<>();
        for (String name : names) {
            List<Player> matches = playerBase.prefixSearch(name).limit(2).collect(Collectors.toList());
            if (matches.size() == 2) {
                log.warn(String.format("%s matches at least two players: %s and %s", name, matches.get(0).getFullName(), matches.get(1).getFullName()));
                notUnique = true;
            }
            if (matches.size() == 0) {
                log.warn(String.format("%s matches no player", name));
            }
            players.addAll(matches);
        }
        if (log.isInfoEnabled()) {
            String msg = String.format("Multi-player quick search for %d names finished in %d ms with %d hits",
                    names.size(), System.currentTimeMillis() - start, players.size());
            if (notUnique) {
                msg += " (not unique match)";
            }
            log.info(msg);
        }
        return notUnique ? null : players;
    }

    @Override
    public boolean matches(Player player) {
        return names.stream().anyMatch(player.getFullName()::startsWith);
    }
}
