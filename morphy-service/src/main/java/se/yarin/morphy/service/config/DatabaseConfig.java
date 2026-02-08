package se.yarin.morphy.service.config;

/**
 * Configuration model for a ChessBase database. Loaded from JSON configuration file. All databases
 * are writable.
 */
public class DatabaseConfig {
  private String id;
  private String displayName;
  private String path;

  public DatabaseConfig() {}

  public DatabaseConfig(String id, String displayName, String path) {
    this.id = id;
    this.displayName = displayName;
    this.path = path;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @Override
  public String toString() {
    return "DatabaseConfig{"
        + "id='"
        + id
        + '\''
        + ", displayName='"
        + displayName
        + '\''
        + ", path='"
        + path
        + '\''
        + '}';
  }
}
