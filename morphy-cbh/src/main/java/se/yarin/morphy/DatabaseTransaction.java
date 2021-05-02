package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.games.GameHeader;

public abstract class DatabaseTransaction implements EntityRetriever {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTransaction.class);

    private final @NotNull Database database;
    private final @NotNull GameAdapter gameAdapter = new GameAdapter();

    private final int version;  // The version of the database where the transaction starts from
    private final DatabaseContext.DatabaseLock lock;

    // If the transaction is closed, no further operations can be done to it
    private boolean closed;

    public Database database() {
        return database;
    }

    public GameAdapter gameAdapter() {
        return gameAdapter;
    }

    public int version() {
        return version;
    }

    public boolean isClosed() {
        return closed;
    }

    public abstract EntityIndexTransaction<Player> playerTransaction();
    public abstract TournamentIndexTransaction tournamentTransaction();
    public abstract EntityIndexTransaction<Annotator> annotatorTransaction();
    public abstract EntityIndexTransaction<Source> sourceTransaction();
    public abstract EntityIndexTransaction<Team> teamTransaction();
    public abstract EntityIndexTransaction<GameTag> gameTagTransaction();

    public DatabaseTransaction(@NotNull DatabaseContext.DatabaseLock lock, @NotNull Database database) {
        database.context().acquireLock(lock);

        this.database = database;
        this.version = this.database.context().currentVersion();
        this.lock = lock;
    }

    protected void ensureTransactionIsOpen() {
        if (isClosed()) {
            throw new IllegalStateException("The transaction is closed");
        }
    }

    protected void closeTransaction() {
        closed = true;
        database.context().releaseLock(this.lock);
    }

    public @NotNull Game getGame(int id) {
        GameHeader gameHeader = database.gameHeaderIndex().getGameHeader(id);
        ExtendedGameHeaderStorage storage = database.extendedGameHeaderStorage();
        // TODO: ext storage should either be empty (if cbj file is missing) or hold enough items
        ExtendedGameHeader extendedGameHeader = id <= storage.count() ? storage.get(id) : ExtendedGameHeader.empty(gameHeader);

        return new Game(database, gameHeader, extendedGameHeader);
    }

    public @NotNull Player getPlayer(int id) {
        return playerTransaction().get(id);
    }

    public @NotNull Annotator getAnnotator(int id) {
        return annotatorTransaction().get(id);
    }

    public @NotNull Source getSource(int id) {
        return sourceTransaction().get(id);
    }

    public @NotNull Tournament getTournament(int id) {
        return tournamentTransaction().get(id);
    }

    public @NotNull TournamentExtra getTournamentExtra(int id) {
        return tournamentTransaction().getExtra(id);
    }

    public @NotNull Team getTeam(int id) {
        return teamTransaction().get(id);
    }

    public @NotNull GameTag getGameTag(int id) {
        return gameTagTransaction().get(id);
    }

}
