package se.yarin.morphy.entities;

import java.util.HashMap;
import java.util.Map;

public class TournamentIndexTransaction extends EntityIndexTransaction<Tournament> {
    private final Map<Integer, TournamentExtra> extraChanges = new HashMap<>();
    private final TournamentIndex tournamentIndex;

    public TournamentIndexTransaction(TournamentIndex index) {
        super(index);
        this.tournamentIndex = index;
    }

    @Override
    public void commit() {
        super.commit();

        // Don't put extra elements if 1) they contain no data and 2) is beyond the end of the file
        // This simulates the ChessBase behavior
        for (Map.Entry<Integer, TournamentExtra> entry : extraChanges.entrySet()) {
            int id = entry.getKey();
            TournamentExtra extra = entry.getValue();
            if (!extra.isEmpty() || id < tournamentIndex.extraStorage().numEntries()) {
                tournamentIndex.extraStorage().put(id, extra);
            }
        }
    }

    @Override
    public int addEntity(Tournament entity) {
        int tournamentId = super.addEntity(entity);
        extraChanges.put(tournamentId, entity.extra());
        return tournamentId;
    }

    @Override
    public void putEntityById(int tournamentId, Tournament entity) {
        super.putEntityById(tournamentId, entity);
        extraChanges.put(tournamentId, entity.extra());
    }

    @Override
    public int putEntityByKey(Tournament entity) {
        int tournamentId = super.putEntityByKey(entity);
        extraChanges.put(tournamentId, entity.extra());
        return tournamentId;
    }
}
