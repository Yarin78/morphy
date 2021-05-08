package se.yarin.morphy.cli.old.games;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.search.GameSearcher;

import java.util.function.Consumer;

public interface GameConsumer extends Consumer<Game> {
    void init();

    void searchDone(GameSearcher.SearchResult searchResult);

    void finish();
}
