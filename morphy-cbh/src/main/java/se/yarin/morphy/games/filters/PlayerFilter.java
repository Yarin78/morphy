package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.util.CBUtil;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.queries.GameEntityJoin;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerFilter extends IsGameFilter implements GameEntityFilter<Player> {

    private final @NotNull Set<Integer> playerIds;

    private final @NotNull GameEntityJoinCondition matchCondition;

    public PlayerFilter(int playerId, @Nullable GameEntityJoinCondition matchCondition) {
        this(new int[] { playerId }, matchCondition);
    }

    public PlayerFilter(int[] playerIds, @Nullable GameEntityJoinCondition matchCondition) {
        this.playerIds = Arrays.stream(playerIds).boxed().collect(Collectors.toUnmodifiableSet());
        this.matchCondition = matchCondition == null ? GameEntityJoinCondition.ANY : matchCondition;
    }

    public PlayerFilter(@NotNull Player player, @Nullable GameEntityJoinCondition matchCondition) {
        this(Collections.singletonList(player), matchCondition);
    }

    public PlayerFilter(@NotNull Collection<Player> players, @Nullable GameEntityJoinCondition matchCondition) {
        this.playerIds = players.stream().map(Player::id).collect(Collectors.toCollection(HashSet::new));
        this.matchCondition = matchCondition == null ? GameEntityJoinCondition.ANY : matchCondition;
    }

    @Override
    public EntityType entityType() {
        return EntityType.PLAYER;
    }

    public List<Integer> entityIds() {
        return new ArrayList<>(playerIds);
    }

    @Override
    public @NotNull GameEntityJoinCondition matchCondition() {
        return matchCondition;
    }

    @Override
    public boolean matches(int id, @NotNull GameHeader gameHeader) {
        if (!super.matches(id, gameHeader)) {
            return false;
        }
        return matchCondition.matches(gameHeader.whitePlayerId(), gameHeader.blackPlayerId(), gameHeader.result(), playerIds);
    }

    @Override
    public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
        if (!super.matchesSerialized(id, buf)) {
            return false;
        }

        int whitePlayerId = ByteBufferUtil.getUnsigned24BitB(buf, 9);
        int blackPlayerId = ByteBufferUtil.getUnsigned24BitB(buf, 12);
        GameResult result = CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf, 27));
        return matchCondition.matches(whitePlayerId, blackPlayerId, result, playerIds);
    }

    @Override
    public String toString() {
        String s;
        if (playerIds.size() == 1) {
            s = "playerId=" + playerIds.stream().findFirst().get();
        } else {
            s = "playerId in ( " + playerIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
        return s + ", condition=" + matchCondition;
    }
}
