package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

public class TeamFilter implements ItemStorageFilter<ExtendedGameHeader> {
    private final @NotNull HashSet<Integer> teamIds;
    private final @NotNull PlayerColor color;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public TeamFilter(@NotNull Team team, @NotNull PlayerColor color) {
        this(Collections.singletonList(team), color);
    }

    public TeamFilter(@NotNull Collection<Team> teams, @NotNull PlayerColor color) {
        this.teamIds = teams.stream().map(Team::id).collect(Collectors.toCollection(HashSet::new));
        this.color = color;
    }

    @Override
    public boolean matches(@NotNull ExtendedGameHeader extendedGameHeader) {
        boolean isWhite = teamIds.contains(extendedGameHeader.whiteTeamId());
        boolean isBlack = teamIds.contains(extendedGameHeader.blackTeamId());
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        int whiteTeamId = ByteBufferUtil.getIntB(buf, 0);
        int blackTeamId = ByteBufferUtil.getIntB(buf, 4);

        boolean isWhite = teamIds.contains(whiteTeamId);
        boolean isBlack = teamIds.contains(blackTeamId);
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }
}
