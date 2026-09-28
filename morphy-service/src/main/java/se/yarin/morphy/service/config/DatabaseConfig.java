package se.yarin.morphy.service.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration model for a ChessBase database. Loaded from JSON configuration file.
 */
public class DatabaseConfig {
  private String id;
  private String displayName;
  private String path;
  private boolean readOnly;

  /**
   * Whether to create an empty database, in the format its path's extension names, when there is
   * none at the path. Without it, a missing database fails to open.
   */
  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  private boolean createIfMissing;

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

  public boolean isCreateIfMissing() {
    return createIfMissing;
  }

  public void setCreateIfMissing(boolean createIfMissing) {
    this.createIfMissing = createIfMissing;
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
        + ", readOnly="
        + readOnly
        + ", createIfMissing="
        + createIfMissing
        + '}';
  }
}
