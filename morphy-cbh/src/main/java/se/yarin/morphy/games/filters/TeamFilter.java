package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class TeamFilter implements ItemStorageFilter<ExtendedGameHeader>, GameFilter, GameEntityFilter<Team> {
    private final @NotNull Set<Integer> teamIds;
    private final @NotNull GameEntityJoinCondition matchCondition;

    public TeamFilter(int teamId, @Nullable GameEntityJoinCondition matchCondition) {
        this(new int[] { teamId }, matchCondition);
    }

    public TeamFilter(int[] teamIds, @Nullable GameEntityJoinCondition matchCondition) {
        this.teamIds = Arrays.stream(teamIds).boxed().collect(Collectors.toUnmodifiableSet());
        // This ought to be supported, but requires that we have the default GameHeader as well as ExtendedGameHeader
        if (matchCondition == GameEntityJoinCondition.WINNER || matchCondition == GameEntityJoinCondition.LOSER) {
            throw new IllegalArgumentException("Cannot use WINNER or LOSER with TeamFilter");
        }
        this.matchCondition = matchCondition == null ? GameEntityJoinCondition.ANY : matchCondition;
    }

    public TeamFilter(@NotNull Team team, @Nullable GameEntityJoinCondition matchCondition) {
        this(Collections.singletonList(team), matchCondition);
    }

    public TeamFilter(@NotNull Collection<Team> teams, @Nullable GameEntityJoinCondition matchCondition) {
        this.teamIds = teams.stream().map(Team::id).collect(Collectors.toCollection(HashSet::new));
        if (matchCondition == GameEntityJoinCondition.WINNER || matchCondition == GameEntityJoinCondition.LOSER) {
            throw new IllegalArgumentException("Cannot use WINNER or LOSER with TeamFilter");
        }
        this.matchCondition = matchCondition == null ? GameEntityJoinCondition.ANY : matchCondition;
    }

    @Override
    public List<Integer> entityIds() {
        return new ArrayList<>(teamIds);
    }

    @Override
    public EntityType entityType() {
        return EntityType.TEAM;
    }

    @Override
    public @NotNull GameEntityJoinCondition matchCondition() {
        return matchCondition;
    }

    @Override
    public boolean matches(@NotNull ExtendedGameHeader extendedGameHeader) {
        return matchCondition.matches(extendedGameHeader.whiteTeamId(), extendedGameHeader.blackTeamId(), GameResult.DRAW, teamIds);
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        int whiteTeamId = ByteBufferUtil.getIntB(buf, 0);
        int blackTeamId = ByteBufferUtil.getIntB(buf, 4);

        return matchCondition.matches(whiteTeamId, blackTeamId, GameResult.DRAW, teamIds);
    }

    public @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() { return this; }

    @Override
    public String toString() {
        String s;
        if (teamIds.size() == 1) {
            s = "teamId=" + teamIds.stream().findFirst().get();
        } else {
            s = "teamId in ( " + teamIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
        return s + ", condition=" + matchCondition;
    }
}
