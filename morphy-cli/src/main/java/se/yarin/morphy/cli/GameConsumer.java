package se.yarin.morphy.cli;

import se.yarin.cbhlib.games.search.GameSearcher;

import java.util.function.Consumer;

public interface GameConsumer extends Consumer<GameSearcher.Hit> {
    void init();

    void done(GameSearcher.SearchResult searchResult);
}
