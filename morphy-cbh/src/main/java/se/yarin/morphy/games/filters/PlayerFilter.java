package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerFilter extends IsGameFilter {

    private final @NotNull HashSet<Integer> playerIds;
    private final @NotNull PlayerColor color;
    private final @NotNull PlayerResult result;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public enum PlayerResult {
        ANY,
        WIN,
        LOSS
    }

    public PlayerFilter(@NotNull Player player, @NotNull PlayerColor color, @NotNull PlayerResult result) {
        this(Collections.singletonList(player), color, result);
    }

    public PlayerFilter(@NotNull Collection<Player> players, @NotNull PlayerColor color, @NotNull PlayerResult result) {
        this.playerIds = players.stream().map(Player::id).collect(Collectors.toCollection(HashSet::new));
        this.color = color;
        this.result = result;
    }

    public List<Integer> playerIds() {
        return new ArrayList<>(playerIds);
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        if (!super.matches(gameHeader)) {
            return false;
        }
        boolean isWhite = playerIds.contains(gameHeader.whitePlayerId());
        boolean isBlack = playerIds.contains(gameHeader.blackPlayerId());
        // TODO: Test this logic
        boolean colorMatches = (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);

        boolean resultMatches = result == PlayerResult.ANY ||
                (result == PlayerResult.WIN && playerIds.contains(gameHeader.winningPlayerId())) ||
                (result == PlayerResult.LOSS && (playerIds.contains(gameHeader.losingPlayerId()) || gameHeader.result() == GameResult.BOTH_LOST));

        return colorMatches && resultMatches;
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
        // TODO: Add support for results
        return (isWhite && this.color != PlayerColor.BLACK) || (isBlack && this.color != PlayerColor.WHITE);
    }

    @Override
    public String toString() {
        // TODO: color and result
        if (playerIds.size() == 1) {
            return "playerId=" + playerIds.stream().findFirst().get();
        } else {
            return "playerId in ( " + playerIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
