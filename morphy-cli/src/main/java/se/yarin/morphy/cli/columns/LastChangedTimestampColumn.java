package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LastChangedTimestampColumn implements GameColumn {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  @Override
  public String getHeader() {
    return "Last changed";
  }

  @Override
  public String getValue(Game game) {
    if (game.lastChangedTimestamp() == 0) {
      return "";
    }
    return FORMATTER.format(game.lastChangedTime());
  }

  @Override
  public String getId() {
    return "updated";
  }

  @Override
  public int width() {
    return 19;
  }
}
