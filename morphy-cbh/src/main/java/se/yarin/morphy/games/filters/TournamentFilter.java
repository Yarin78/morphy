package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class TournamentFilter implements ItemStorageFilter<GameHeader>, GameFilter {
    private final @NotNull HashSet<Integer> tournamentIds;

    public TournamentFilter(@NotNull Tournament tournament) {
        this(Collections.singleton(tournament));
    }

    public TournamentFilter(@NotNull Collection<Tournament> tournaments) {
        this.tournamentIds = tournaments.stream().map(Tournament::id).collect(Collectors.toCollection(HashSet::new));
    }

    public List<Integer> tournamentIds() {
        return new ArrayList<>(tournamentIds);
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return tournamentIds.contains(gameHeader.tournamentId());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        int tournamentId;

        if (IsGameFilter.isGame(buf)) {
            // Regular game
            tournamentId = ByteBufferUtil.getUnsigned24BitB(buf, 15);
        } else {
            // Guiding text
            tournamentId = ByteBufferUtil.getUnsigned24BitB(buf, 7);
        }
        return tournamentIds.contains(tournamentId);
    }

    @Override
    public String toString() {
        if (tournamentIds.size() == 1) {
            return "tournamentId=" + tournamentIds.stream().findFirst().get();
        } else {
            return "tournamentId in ( " + tournamentIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
