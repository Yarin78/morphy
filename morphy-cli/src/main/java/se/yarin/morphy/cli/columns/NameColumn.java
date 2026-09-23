package se.yarin.morphy.cli.columns;

import se.yarin.morphy.model.PlayerDto;

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
  public String getValue(GameRow row) {
    if ("text".equals(row.dto().type())) {
      return isWhite ? orEmpty(row.dto().textTitle()) : "";
    }
    PlayerDto player = isWhite ? row.dto().whitePlayer() : row.dto().blackPlayer();
    String name = shortName(player);
    return isWhite ? name : ("- " + name);
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  /** Mirrors Player#getFullNameShort for a PlayerDto: "Last, F". */
  static String shortName(PlayerDto player) {
    if (player == null) {
      return "";
    }
    String last = orEmpty(player.lastName());
    String first = orEmpty(player.firstName());
    if (last.isEmpty()) {
      return first;
    }
    if (first.isEmpty()) {
      return last;
    }
    return last + ", " + first.charAt(0);
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
