package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.games.GameHeader;

public abstract class DatabaseTransaction extends TransactionBase implements EntityRetriever {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTransaction.class);

    private final @NotNull GameAdapter gameAdapter = new GameAdapter();
    private final @NotNull Database database;

    public Database database() {
        return database;
    }

    public GameAdapter gameAdapter() {
        return gameAdapter;
    }

    public abstract int version();

    public abstract EntityIndexTransaction<Player> playerTransaction();
    public abstract TournamentIndexTransaction tournamentTransaction();
    public abstract EntityIndexTransaction<Annotator> annotatorTransaction();
    public abstract EntityIndexTransaction<Source> sourceTransaction();
    public abstract EntityIndexTransaction<Team> teamTransaction();
    public abstract EntityIndexTransaction<GameTag> gameTagTransaction();

    public DatabaseTransaction(@NotNull DatabaseContext.DatabaseLock lock, @NotNull Database database) {
        super(lock, database.context());

        this.database = database;
    }

    public @NotNull Game getGame(int gameId) {
        if (gameId < 1) {
            throw new IllegalArgumentException("Invalid game id: " + gameId);
        }
        GameHeader gameHeader = database.gameHeaderIndex().getGameHeader(gameId);
        ExtendedGameHeaderStorage storage = database.extendedGameHeaderStorage();
        // TODO: ext storage should either be empty (if cbj file is missing) or hold enough items
        ExtendedGameHeader extendedGameHeader = gameId <= storage.count() ? storage.get(gameId) : ExtendedGameHeader.empty(gameHeader);

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

    public void close() {
        playerTransaction().close();
        tournamentTransaction().close();
        annotatorTransaction().close();
        sourceTransaction().close();
        teamTransaction().close();
        gameTagTransaction().close();

        super.close();
    }
}
