package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TournamentIndexWriteTransaction extends EntityIndexWriteTransaction<Tournament> implements TournamentIndexTransaction {
    private final Map<Integer, TournamentExtra> extraChanges = new HashMap<>();
    private @Nullable final TournamentExtraStorage tournamentExtraStorage;

    public TournamentIndexWriteTransaction(@NotNull TournamentIndex index) {
        this(index, null);
    }

    public TournamentIndexWriteTransaction(@NotNull TournamentIndex index, @Nullable TournamentExtraStorage extraStorage) {
        super(index);
        this.tournamentExtraStorage = extraStorage;
    }

    @Override
    public void commit() {
        super.commit(() -> {
            if (tournamentExtraStorage != null) {
                // Don't put extra elements if 1) they contain no data and 2) is beyond the end of the file
                // This simulates the ChessBase behavior
                for (Map.Entry<Integer, TournamentExtra> entry : extraChanges.entrySet()) {
                    int id = entry.getKey();
                    TournamentExtra extra = entry.getValue();
                    if (!extra.isEmpty() || id < tournamentExtraStorage.numEntries()) {
                        tournamentExtraStorage.put(id, extra);
                    }
                }
            }
        });
    }

    @Override
    protected void clearChanges() {
        super.clearChanges();
        extraChanges.clear();
    }

    public @NotNull TournamentExtra getExtra(int id) {
        if (tournamentExtraStorage == null) {
            throw new IllegalStateException("Transaction does not contain extra storage");
        }
        if (extraChanges.containsKey(id)) {
            return extraChanges.get(id);
        }
        return tournamentExtraStorage.get(id);
    }

    /**
     * Looks up an entity based on key and returns the id.
     * If the entity is missing, it's created in the transaction with 0 count in the statistics.
     * @param tournament the entity to lookup
     * @return the id of an existing matching entity, or the id of the newly created entity
     */
    public int getOrCreate(@NotNull Tournament tournament, @Nullable TournamentExtra extra) {
        Tournament existing = this.get(tournament);
        if (existing != null) {
            return existing.id();
        }

        if (tournament.count() != 0 || tournament.firstGameId() != 0) {
            // New entities should always have 0 in the stats
            // In case entities are added from another database, this might not be the case
            // so they have to be reset before added to this index.
            tournament = (Tournament) tournament.withCountAndFirstGameId(0, 0);
        }
        return addEntity(tournament, extra);
    }

    @Override
    public int addEntity(@NotNull Tournament entity) {
        return addEntity(entity, null);
    }

    public int addEntity(@NotNull Tournament entity, @Nullable TournamentExtra extra) {
        if (extra != null && tournamentExtraStorage == null) {
            throw new IllegalArgumentException("Tried to modify TournamentExtra without a storage reference");
        }
        int tournamentId = super.addEntity(entity);
        if (extra != null) {
            extraChanges.put(tournamentId, extra);
        }
        return tournamentId;
    }

    @Override
    public void putEntityById(int tournamentId, @NotNull Tournament entity) {
        putEntityById(tournamentId, entity, null);
    }

    public void putEntityById(int tournamentId, @NotNull Tournament entity, @Nullable TournamentExtra extra) {
        if (extra != null && tournamentExtraStorage == null) {
            throw new IllegalArgumentException("Tried to modify TournamentExtra without a storage reference");
        }
        super.putEntityById(tournamentId, entity);
        if (extra != null) {
            extraChanges.put(tournamentId, extra);
        }
    }

    @Override
    public int putEntityByKey(@NotNull Tournament entity) {
        return putEntityByKey(entity, null);
    }

    public int putEntityByKey(@NotNull Tournament entity, @Nullable TournamentExtra extra) {
        if (extra != null && tournamentExtraStorage == null) {
            throw new IllegalArgumentException("Tried to modify TournamentExtra without a storage reference");
        }

        int tournamentId = super.putEntityByKey(entity);
        if (extra != null) {
            extraChanges.put(tournamentId, extra);
        }
        return tournamentId;
    }

}
