package se.yarin.morphy.cli.columns;

import java.time.Instant;
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
  public String getValue(GameRow row) {
    Long timestamp = row.dto().creationTimestamp();
    return timestamp == null ? "" : FORMATTER.format(Instant.ofEpochMilli(timestamp));
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
