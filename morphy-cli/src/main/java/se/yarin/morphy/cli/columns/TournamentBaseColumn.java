package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Tournament;

public abstract class TournamentBaseColumn implements GameColumn, TournamentColumn {

  public int width() {
    return getHeader().length();
  }

  public int marginLeft() {
    return 1;
  }

  public int marginRight() {
    return 1;
  }

  public boolean trimValueToWidth() {
    return true;
  }

  @Override
  public String getValue(Game game) {
    return getTournamentValue(game.database(), game.tournament());
  }

  public abstract String getTournamentValue(Database db, Tournament tournament);

  @Override
  public String getTournamentId() {
    String id = getId();
    assert id.startsWith("tournament-") : id;
    return id.substring(11);
  }
}
