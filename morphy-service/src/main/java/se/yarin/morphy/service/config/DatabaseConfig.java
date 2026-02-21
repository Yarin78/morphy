package se.yarin.morphy.service.config;

/**
 * Configuration model for a ChessBase database. Loaded from JSON configuration file.
 */
public class DatabaseConfig {
  private String id;
  private String displayName;
  private String path;
  private boolean readOnly;

  public DatabaseConfig() {}

  public DatabaseConfig(String id, String displayName, String path) {
    this(id, displayName, path, false);
  }

  public DatabaseConfig(String id, String displayName, String path, boolean readOnly) {
    this.id = id;
    this.displayName = displayName;
    this.path = path;
    this.readOnly = readOnly;
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

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
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
