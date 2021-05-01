package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TournamentIndexReadTransaction extends EntityIndexReadTransaction<Tournament> implements TournamentIndexTransaction {
    private final @Nullable TournamentExtraStorage tournamentExtraStorage;

    public TournamentIndexReadTransaction(@NotNull TournamentIndex index) {
        this(index, null);
    }

    public TournamentIndexReadTransaction(@NotNull TournamentIndex index, @Nullable TournamentExtraStorage extraStorage) {
        super(index);
        this.tournamentExtraStorage = extraStorage;
    }

    public @NotNull TournamentExtra getExtra(int id) {
        if (tournamentExtraStorage == null) {
            throw new IllegalStateException("Transaction does not contain extra storage");
        }
        return tournamentExtraStorage.get(id);
    }
}
