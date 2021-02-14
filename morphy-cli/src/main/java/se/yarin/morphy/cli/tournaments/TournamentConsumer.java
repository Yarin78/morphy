package se.yarin.morphy.cli.tournaments;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.entities.TournamentSearcher;

import java.util.function.Consumer;

public interface TournamentConsumer extends Consumer<TournamentEntity> {
    void setCurrentDatabase(Database database);

    void init();

    void searchDone(TournamentSearcher.SearchResult searchResult);

    void finish();
}
