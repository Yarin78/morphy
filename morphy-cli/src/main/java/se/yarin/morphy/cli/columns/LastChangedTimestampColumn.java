package se.yarin.morphy.cli.columns;

import java.time.Instant;
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
  public String getValue(GameRow row) {
    String lastChanged = row.dto().lastChanged();
    return lastChanged == null ? "" : FORMATTER.format(Instant.parse(lastChanged));
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
