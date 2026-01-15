package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class EcoColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "ECO";
  }

  @Override
  public String getValue(Game game) {
    String eco;
    if (game.guidingText()) {
      eco = "";
    } else {
      eco = game.eco().toString().substring(0, 3);
      if (eco.equals("???")) {
        eco = "";
      }
    }
    return eco;
  }

  @Override
  public String getId() {
    return "eco";
  }
}
