package se.yarin.cbhlib.games.search;

import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.GameTagEntity;
import se.yarin.cbhlib.games.SerializedExtendedGameHeaderFilter;
import se.yarin.util.ByteBufferUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GameTagFilter extends SearchFilterBase implements SerializedExtendedGameHeaderFilter {
    private static final int MAX_GAME_TAGS = 20;

    private final String searchString;
    private List<GameTagEntity> gameTags;
    private HashSet<Integer> gameTagIds;

    public GameTagFilter(Database database, @NonNull GameTagEntity gameTag) {
        super(database);

        this.searchString = "";
        this.gameTags = Arrays.asList(gameTag);
    }

    public GameTagFilter(Database database, @NonNull String searchString) {
        super(database);

        this.searchString = searchString;
    }

    public void initSearch() {
        if (this.gameTags == null) {
            // If we can quickly determine if there are few enough game tags in the database that matches the search string,
            // we can get an improved searched
            Stream<GameTagEntity> stream = getDatabase().getGameTagBase().prefixSearch(searchString);
            List<GameTagEntity> matchingGameTags = stream.limit(MAX_GAME_TAGS + 1).collect(Collectors.toList());
            if (matchingGameTags.size() <= MAX_GAME_TAGS) {
                this.gameTags = matchingGameTags;
            }
        }

        if (this.gameTags != null) {
            this.gameTagIds = this.gameTags.stream().map(GameTagEntity::getId).collect(Collectors.toCollection(HashSet::new));
        }
    }

    @Override
    public int countEstimate() {
        if (gameTags == null) {
            return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
        } else {
            int count = 0;
            for (GameTagEntity gameTag : gameTags) {
                count += gameTag.getCount();
            }
            return count;
        }
    }

    @Override
    public int firstGameId() {
        if (gameTags == null) {
            return 1;
        } else {
            int first = Integer.MAX_VALUE;
            for (GameTagEntity gameTag : gameTags) {
                first = Math.min(first, gameTag.getFirstGameId());
            }
            return first;
        }
    }


    @Override
    public boolean matches(Game game) {
        GameTagEntity gameTag = game.getGameTag();
        if (this.gameTags != null) {
            return this.gameTags.contains(gameTag);
        } else {
            if (gameTag != null && gameTag.getEnglishTitle().startsWith(this.searchString)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(byte[] serializedExtendedGameHeader) {
        if (gameTagIds != null) {
            int gameTagId = ByteBufferUtil.getIntB(serializedExtendedGameHeader, 116);
            return gameTagIds.contains(gameTagId);
        }
        return false;
    }
}
