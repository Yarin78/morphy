package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DatabaseReadTransaction extends DatabaseTransaction {
    private final int version;

    private final EntityIndexReadTransaction<Player> playerTransaction;
    private final TournamentIndexReadTransaction tournamentTransaction;
    private final EntityIndexReadTransaction<Annotator> annotatorTransaction;
    private final EntityIndexReadTransaction<Source> sourceTransaction;
    private final EntityIndexReadTransaction<Team> teamTransaction;
    private final EntityIndexReadTransaction<GameTag> gameTagTransaction;

    public DatabaseReadTransaction(@NotNull Database database) {
        super(DatabaseContext.DatabaseLock.READ, database);

        this.version = database.context().currentVersion();
        this.playerTransaction = database.playerIndex().beginReadTransaction();
        this.tournamentTransaction = database.tournamentIndex().beginReadTransaction(database.tournamentExtraStorage());
        this.annotatorTransaction = database.annotatorIndex().beginReadTransaction();
        this.sourceTransaction = database.sourceIndex().beginReadTransaction();
        this.teamTransaction = database.teamIndex().beginReadTransaction();
        this.gameTagTransaction = database.gameTagIndex().beginReadTransaction();
    }

    public int version() {
        return version;
    }

    @Override
    public EntityIndexReadTransaction<Player> playerTransaction() {
        return playerTransaction;
    }

    @Override
    public TournamentIndexReadTransaction tournamentTransaction() {
        return tournamentTransaction;
    }

    @Override
    public EntityIndexReadTransaction<Annotator> annotatorTransaction() {
        return annotatorTransaction;
    }

    @Override
    public EntityIndexReadTransaction<Source> sourceTransaction() {
        return sourceTransaction;
    }

    @Override
    public EntityIndexReadTransaction<Team> teamTransaction() {
        return teamTransaction;
    }

    @Override
    public EntityIndexReadTransaction<GameTag> gameTagTransaction() {
        return gameTagTransaction;
    }

    /**
     * Returns an iterable of all games in the database, sorted by id.
     * @return an iterable of all games
     */
    public @NotNull Iterable<Game> iterable() {
        return iterable(1);
    }

    /**
     * Returns an iterable of all games matching the given filter in the index, sorted by id.
     * @param filter a search filter; null will return all games
     * @return an iterable of all games
     */
    public @NotNull Iterable<Game> iterable(@Nullable GameFilter filter) {
        return iterable(1, filter);
    }

    /**
     * Returns an iterable of all games in the index, sorted by id.
     * @param startId the first id in the iterable
     * @return an iterable of all games
     */
    public @NotNull Iterable<Game> iterable(int startId) {
        return iterable(startId, null);
    }

    /**
     * Returns an iterable of all games matching the given filter in the index, sorted by id.
     * @param startId the first id in the iterable
     * @param filter a search filter; null will return all games
     * @return an iterable of all games
     */
    public @NotNull Iterable<Game> iterable(int startId, @Nullable GameFilter filter) {
        return () -> new GameIterator(this, startId, filter);
    }

    /**
     * Returns a stream of all games in the index, sorted by id.
     * @return a stream of all games
     */
    public @NotNull Stream<Game> stream() {
        return StreamSupport.stream(iterable().spliterator(), false);
    }

    /**
     * Returns a stream of all games matching the given filter in the index, sorted by id.
     * @param filter a search filter; null will return all games
     * @return a stream of all games
     */
    public @NotNull Stream<Game> stream(@Nullable GameFilter filter) {
        return StreamSupport.stream(iterable(1, filter).spliterator(), false);
    }

    /**
     * Returns a stream of all games in the index, sorted by id.
     * @param startId the first id in the stream
     * @return a stream of all games
     */
    public @NotNull Stream<Game> stream(int startId) {
        return StreamSupport.stream(iterable(startId).spliterator(), false);
    }

    /**
     * Returns a stream of all games matching the given filter in the index, sorted by id.
     * @param startId the first id in the stream
     * @param filter a search filter; null will return all games
     * @return a stream of all games
     */
    public @NotNull Stream<Game> stream(int startId, @Nullable GameFilter filter) {
        return StreamSupport.stream(iterable(startId, filter).spliterator(), false);
    }
}
