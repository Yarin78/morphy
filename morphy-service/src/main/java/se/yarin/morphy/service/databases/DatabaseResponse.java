package se.yarin.morphy.service.databases;

import com.fasterxml.jackson.annotation.JsonInclude;
import se.yarin.morphy.service.config.DatabaseConfig;

/**
 * A configured database; a read-only one can be searched but not changed. A reference database
 * has a short name, and its games can be searched by position.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseResponse(
    String id, String displayName, String path, boolean readOnly, String referenceName) {

  static DatabaseResponse of(DatabaseDto dto) {
    return new DatabaseResponse(
        dto.id(), dto.displayName(), dto.path(), dto.readOnly(), dto.referenceName());
  }

  static DatabaseResponse of(DatabaseConfig config) {
    return new DatabaseResponse(
        config.getId(),
        config.getDisplayName(),
        config.getPath(),
        config.isReadOnly(),
        config.getReferenceName());
  }
}
