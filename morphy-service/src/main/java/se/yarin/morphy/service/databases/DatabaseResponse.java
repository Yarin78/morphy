package se.yarin.morphy.service.databases;

import se.yarin.morphy.service.config.DatabaseConfig;

/** A configured database; a read-only one can be searched but not changed. */
public record DatabaseResponse(String id, String displayName, String path, boolean readOnly) {

  static DatabaseResponse of(DatabaseDto dto) {
    return new DatabaseResponse(dto.id(), dto.displayName(), dto.path(), dto.readOnly());
  }

  static DatabaseResponse of(DatabaseConfig config) {
    return new DatabaseResponse(
        config.getId(), config.getDisplayName(), config.getPath(), config.isReadOnly());
  }
}
