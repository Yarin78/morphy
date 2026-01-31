package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CreationTimestampColumn implements GameColumn {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  @Override
  public String getHeader() {
    return "Created";
  }

  @Override
  public String getValue(Game game) {
    if (game.creationTimestamp() == 0) {
      return "";
    }
    return FORMATTER.format(game.creationTime());
  }

  @Override
  public String getId() {
    return "created";
  }

  @Override
  public int width() {
    return 19;
  }
}
