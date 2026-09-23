package se.yarin.morphy.cli.columns;

import se.yarin.morphy.model.GameTagDto;

public class GameTagColumn implements GameColumn {

  @Override
  public String getHeader() {
    return "Game Tag";
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getValue(GameRow row) {
    GameTagDto tag = row.dto().gameTag();
    return tag == null || tag.englishTitle() == null ? "" : tag.englishTitle();
  }

  @Override
  public String getId() {
    return "tag";
  }

  @Override
  public int width() {
    return 40;
  }
}
