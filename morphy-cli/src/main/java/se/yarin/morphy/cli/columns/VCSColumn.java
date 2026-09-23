package se.yarin.morphy.cli.columns;

public class VCSColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "VCS";
  }

  @Override
  public String getValue(GameRow row) {
    String vcs = row.dto().vcs();
    return vcs == null ? "" : vcs;
  }

  @Override
  public String getId() {
    return "vcs";
  }
}
