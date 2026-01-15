package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.chess.GameModel;

public class MovesColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Moves";
  }

  @Override
  public String getValue(Game game) {
    try {
      GameModel model = game.getModel();
      return model.moves().toString();
    } catch (MorphyException e) {
      return "<critical error>";
    }
  }

  @Override
  public boolean trimValueToWidth() {
    return false;
  }

  @Override
  public String getId() {
    return "moves";
  }
}
