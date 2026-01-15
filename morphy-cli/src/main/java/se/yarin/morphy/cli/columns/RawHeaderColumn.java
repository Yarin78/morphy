package se.yarin.morphy.cli.columns;

import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.Game;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class RawHeaderColumn implements GameColumn {
  private final int start;
  private final int length;

  @Override
  public String getHeader() {
    if (length == 1) {
      return String.format("CBH %d", start);
    }
    return String.format("CBH %d,%d", start, length);
  }

  public RawHeaderColumn(int start, int length) {
    if (start < 0 || start + length > 46) {
      throw new IllegalArgumentException("Header length is 46 bytes");
    }
    this.start = start;
    this.length = length;
  }

  @Override
  public String getValue(Game game) {
    ByteBuffer buf = game.database().gameHeaderIndex().getRaw(game.id());
    if (start >= buf.limit()) {
      return "";
    }
    buf = buf.slice(start, Math.min(length, buf.limit() - start));
    return CBUtil.toHexString(buf);
  }

  @Override
  public int marginLeft() {
    return 2;
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public int width() {
    return this.length == 1 ? 6 : Math.max(9, this.length * 3);
  }

  @Override
  public boolean trimValueToWidth() {
    return true;
  }

  @Override
  public String getId() {
    return "raw";
  }
}
