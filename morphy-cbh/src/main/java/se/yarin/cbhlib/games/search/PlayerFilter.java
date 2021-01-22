package se.yarin.cbhlib.games.search;

import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.ManualPlayerSearcher;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A search filter that returns games played by a player with a matching name.
 */
public class PlayerFilter extends SearchFilterBase implements SearchFilter, SerializedGameHeaderFilter {
    private static final int MAX_PLAYERS = 20;

    private final PlayerSearcher playerSearcher;
    private List<PlayerEntity> players;
    private HashSet<Integer> playerIds;
    private final PlayerColor color;

    public enum PlayerColor {
        ANY,
        WHITE,
        BLACK
    }

    public PlayerFilter(@NonNull Database database, @NonNull PlayerEntity player, @NonNull PlayerColor color) {
        this(database, new ManualPlayerSearcher(player), color);
    }

    public PlayerFilter(@NonNull Database database, @NonNull List<PlayerEntity> players, @NonNull PlayerColor color) {
        this(database, new ManualPlayerSearcher(players), color);
    }

    public PlayerFilter(@NonNull Database database, @NonNull PlayerSearcher playerSearcher, @NonNull PlayerColor color) {
        super(database);
        this.playerSearcher = playerSearcher;
        this.color = color;
    }

    public void initSearch() {
        if (playerSearcher != null) {
            // If we can quickly determine if there are few enough players in the database that matches the search string,
            // we can get an improved searched
            List<PlayerEntity> foundPlayers = playerSearcher.quickSearch();
            if (foundPlayers != null) {
                if (foundPlayers.size() < MAX_PLAYERS) {
                    // Used by the regular filter
                    this.players = foundPlayers;
                }
            }
        }

        if (this.players != null) {
            // Used by the serialized filter
            this.playerIds = players.stream().map(PlayerEntity::getId).collect(Collectors.toCollection(HashSet::new));
        }
    }

    @Override
    public int countEstimate() {
        if (players == null) {
            return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
        } else {
            int count = 0;
            for (PlayerEntity player : players) {
                count += player.getCount();
            }
            return count;
        }
    }

    @Override
    public int firstGameId() {
        if (players == null) {
            return 1;
        } else {
            int first = Integer.MAX_VALUE;
            for (PlayerEntity player : players) {
                first = Math.min(first, player.getFirstGameId());
            }
            return first;
        }
    }

    @Override
    public boolean matches(Game game) {
        if (game.isGuidingText()) {
            return false;
        }
        if (color != PlayerColor.BLACK && playerSearcher.matches(game.getWhite())) {
            return true;
        }
        if (color != PlayerColor.WHITE && playerSearcher.matches(game.getBlack())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if (playerIds == null) {
            return true;
        }
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text has no players
            return false;
        }
        int whitePlayerId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 9);
        if (color != PlayerColor.BLACK && playerIds.contains(whitePlayerId)) {
            return true;
        }

        int blackPlayerId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 12);
        if (color != PlayerColor.WHITE && playerIds.contains(blackPlayerId)) {
            return true;
        }

        return false;
    }
}
