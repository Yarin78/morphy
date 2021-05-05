package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PlayerFilter extends GameStorageFilter {

    private final @NotNull HashSet<Integer> playerIds;
    private final @NotNull PlayerColor color;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public PlayerFilter(@NotNull Player player, @NotNull PlayerColor color) {
        this(Collections.singletonList(player), color);
    }

    public PlayerFilter(@NotNull Collection<Player> players, @NotNull PlayerColor color) {
        this.playerIds = players.stream().map(Player::id).collect(Collectors.toCollection(HashSet::new));
        this.color = color;
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        if (!super.matches(gameHeader)) {
            return false;
        }
        boolean isWhite = playerIds.contains(gameHeader.whitePlayerId());
        boolean isBlack = playerIds.contains(gameHeader.blackPlayerId());
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        if (!super.matchesSerialized(buf)) {
            return false;
        }

        int whitePlayerId = ByteBufferUtil.getUnsigned24BitB(buf, 9);
        int blackPlayerId = ByteBufferUtil.getUnsigned24BitB(buf, 12);

        boolean isWhite = playerIds.contains(whitePlayerId);
        boolean isBlack = playerIds.contains(blackPlayerId);
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }
}
