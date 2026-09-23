package se.yarin.morphy.cli.columns;

import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.Game;

public class RawAnnotationsColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Annotations (raw data)";
  }

  @Override
  public String getValue(GameRow row) {
    Game game = row.game();
    if (game == null) {
      return "";
    }
    long annotationOffset = game.getAnnotationOffset();
    byte[] movesData =
        game.database().annotationRepository().getAnnotationsBlob(annotationOffset).array();
    return CBUtil.toHexString(movesData);
  }

  @Override
  public String getId() {
    return "raw";
  }
}
