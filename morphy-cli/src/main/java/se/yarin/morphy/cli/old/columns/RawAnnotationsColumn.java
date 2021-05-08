package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.util.CBUtil;

public class RawAnnotationsColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Annotations (raw data)";
    }

    @Override
    public String getValue(Game game) {
        long annotationOffset = game.getAnnotationOffset();
        byte[] movesData = game.getDatabase().getAnnotationBase().getStorage().readBlob(annotationOffset).array();
        return CBUtil.toHexString(movesData);
    }

    @Override
    public String getId() {
        return "raw";
    }
}
