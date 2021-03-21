package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.entities.TournamentSearcher;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.util.ByteBufferUtil;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class TournamentFilter extends SearchFilterBase implements SerializedGameHeaderFilter {
    private static final int MAX_TOURNAMENTS = 50;

    private final TournamentSearcher tournamentSearcher;
    private List<TournamentEntity> tournaments;
    private HashSet<Integer> tournamentIds;

    public TournamentFilter(Database database, TournamentEntity tournament) {
        this(database, new TournamentSearcher(database.getTournamentBase(), tournament));
    }

    public TournamentFilter(Database database, TournamentSearcher tournamentSearcher) {
        super(database);
        this.tournamentSearcher = tournamentSearcher;
    }

    public void initSearch() {
        // If we can quickly determine if there are few enough tournaments in the database that matches the search string,
        // we can get an improved searched
        List<TournamentEntity> tournaments = tournamentSearcher.quickSearch();
        if (tournaments != null) {
            if (tournaments.size() < MAX_TOURNAMENTS) {
                // Used by the regular filter
                this.tournaments = tournaments;
            }
            // Used by the serialized filter
            this.tournamentIds = tournaments.stream().map(TournamentEntity::getId).collect(Collectors.toCollection(HashSet::new));
        }
    }

    @Override
    public int countEstimate() {
        if (tournaments == null) {
            return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
        } else {
            int count = 0;
            for (TournamentEntity tournament : tournaments) {
                count += tournament.getCount();
            }
            return count;
        }
    }

    @Override
    public int firstGameId() {
        if (tournaments == null) {
            return 1;
        } else {
            int first = Integer.MAX_VALUE;
            for (TournamentEntity tournament : tournaments) {
                first = Math.min(first, tournament.getFirstGameId());
            }
            return first;
        }
    }

    @Override
    public boolean matches(Game game) {
        return tournamentSearcher.matches(game.getTournament());
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if (tournamentIds == null) {
            return true;
        }
        int tournamentId;
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text
            tournamentId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 7);
        } else {
            // Regular game
            tournamentId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 15);
        }
        return tournamentIds.contains(tournamentId);
    }
}
