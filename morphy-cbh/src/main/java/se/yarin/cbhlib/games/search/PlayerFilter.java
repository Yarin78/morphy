package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.games.GameHeader;
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

    public PlayerFilter(Database database, PlayerSearcher playerSearcher, PlayerColor color) {
        super(database);
        this.playerSearcher = playerSearcher;
        this.color = color;
    }

    public void initSearch() {
        // If we can quickly determine if there are few enough players in the database that matches the search string,
        // we can get an improved searched
        List<PlayerEntity> players = playerSearcher.quickSearch();
        if (players != null) {
            if (players.size() < MAX_PLAYERS) {
                // Used by the regular filter
                this.players = players;
            }
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
    public boolean matches(GameHeader gameHeader) {
        if (gameHeader.isGuidingText()) {
            return false;
        }
        if (color != PlayerColor.BLACK) {
            PlayerEntity whitePlayer = getDatabase().getPlayerBase().get(gameHeader.getWhitePlayerId());
            if (playerSearcher.matches(whitePlayer)) {
                return true;
            }
        }
        if (color != PlayerColor.WHITE) {
            PlayerEntity blackPlayer = getDatabase().getPlayerBase().get(gameHeader.getBlackPlayerId());
            if (playerSearcher.matches(blackPlayer)) {
                return true;
            }
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
