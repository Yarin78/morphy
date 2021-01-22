package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.SourceEntity;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class SourceFilter extends SearchFilterBase implements SerializedGameHeaderFilter {

    private final List<SourceEntity> sources;
    private final HashSet<Integer> sourceIds;

    public SourceFilter(Database database, SourceEntity source) {
        super(database);

        this.sources = Arrays.asList(source);
        this.sourceIds = sources.stream().map(SourceEntity::getId).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(Game game) {
        return sources.contains(game.getSource());
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if (sourceIds == null) {
            return true;
        }
        int sourceId;
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text
            sourceId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 10);
        } else {
            // Regular game
            sourceId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 21);
        }
        return sourceIds.contains(sourceId);
    }
}
