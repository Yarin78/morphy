package se.yarin.morphy.cli.columns;

import se.yarin.morphy.model.GameMovesDto;

public class MovesColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Moves";
  }

  @Override
  public boolean needsMoves() {
    return true;
  }

  @Override
  public String getValue(GameRow row) {
    GameMovesDto moves = row.dto().moves();
    return moves == null || moves.pgn() == null ? "" : moves.pgn();
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
