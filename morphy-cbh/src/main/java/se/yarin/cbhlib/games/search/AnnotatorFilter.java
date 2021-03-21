package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.AnnotatorEntity;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.util.ByteBufferUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class AnnotatorFilter extends SearchFilterBase implements SerializedGameHeaderFilter {

    private final List<AnnotatorEntity> annotators;
    private final HashSet<Integer> annotatorIds;

    public AnnotatorFilter(Database database, AnnotatorEntity annotator) {
        super(database);

        this.annotators = Arrays.asList(annotator);
        this.annotatorIds = annotators.stream().map(AnnotatorEntity::getId).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(Game game) {
        return annotators.contains(game.getAnnotator());
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if (annotatorIds == null) {
            return true;
        }
        int annotatorId;
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text
            annotatorId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 13);
        } else {
            // Regular game
            annotatorId = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 18);
        }
        return annotatorIds.contains(annotatorId);
    }
}
