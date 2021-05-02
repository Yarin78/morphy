package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.*;

public class DatabaseReadTransaction extends DatabaseTransaction {
    private final EntityIndexReadTransaction<Player> playerTransaction;
    private final TournamentIndexReadTransaction tournamentTransaction;
    private final EntityIndexReadTransaction<Annotator> annotatorTransaction;
    private final EntityIndexReadTransaction<Source> sourceTransaction;
    private final EntityIndexReadTransaction<Team> teamTransaction;
    private final EntityIndexReadTransaction<GameTag> gameTagTransaction;

    public DatabaseReadTransaction(@NotNull Database database) {
        super(DatabaseContext.DatabaseLock.READ, database);

        this.playerTransaction = database.playerIndex().beginReadTransaction();
        this.tournamentTransaction = database.tournamentIndex().beginReadTransaction(database.tournamentExtraStorage());
        this.annotatorTransaction = database.annotatorIndex().beginReadTransaction();
        this.sourceTransaction = database.sourceIndex().beginReadTransaction();
        this.teamTransaction = database.teamIndex().beginReadTransaction();
        this.gameTagTransaction = database.gameTagIndex().beginReadTransaction();
    }

    @Override
    public EntityIndexTransaction<Player> playerTransaction() {
        return playerTransaction;
    }

    @Override
    public TournamentIndexTransaction tournamentTransaction() {
        return tournamentTransaction;
    }

    @Override
    public EntityIndexTransaction<Annotator> annotatorTransaction() {
        return annotatorTransaction;
    }

    @Override
    public EntityIndexTransaction<Source> sourceTransaction() {
        return sourceTransaction;
    }

    @Override
    public EntityIndexTransaction<Team> teamTransaction() {
        return teamTransaction;
    }

    @Override
    public EntityIndexTransaction<GameTag> gameTagTransaction() {
        return gameTagTransaction;
    }
}
