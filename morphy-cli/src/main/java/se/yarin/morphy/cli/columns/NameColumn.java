package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;

public class NameColumn implements GameColumn {

  private final boolean isWhite;

  public NameColumn(boolean isWhite) {
    this.isWhite = isWhite;
  }

  @Override
  public String getHeader() {
    return isWhite ? "White" : "  Black";
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getValue(Game game) {
    if (game.guidingText()) {
      return isWhite ? game.getTextTitle() : "";
    }
    Player player = isWhite ? game.white() : game.black();
    String name = player.getFullNameShort();
    return isWhite ? name : ("- " + name);
  }

  @Override
  public String getId() {
    return "name";
  }

  @Override
  public int width() {
    return isWhite ? 20 : 22;
  }
}
