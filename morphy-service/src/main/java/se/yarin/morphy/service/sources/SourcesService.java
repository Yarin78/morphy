package se.yarin.morphy.service.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.sources.dto.SourceDto;
import se.yarin.morphy.service.sources.dto.SourceDtoConverter;

@Service
public class SourcesService {
  private static final Logger log = LoggerFactory.getLogger(SourcesService.class);

  private final DatabaseService databaseService;
  private final SourceDtoConverter sourceDtoConverter;

  public SourcesService(DatabaseService databaseService, SourceDtoConverter sourceDtoConverter) {
    this.databaseService = databaseService;
    this.sourceDtoConverter = sourceDtoConverter;
  }

  /**
   * Get sources with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (source ID) to start from, null for beginning
   * @param limit Maximum number of sources to return (max 1000)
   * @return Paginated response with sources and pagination info
   */
  public SourceListResponse getSources(@NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new SourceListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<SourceDto> allSources =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream sources from the specified start ID
              return txn.sourceTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(sourceDtoConverter::toDto)
                  .collect(Collectors.toList());
            });

    boolean hasMore = allSources.size() > limit;
    List<SourceDto> sources = hasMore ? allSources.subList(0, limit) : allSources;

    String nextCursor = null;
    if (hasMore && !sources.isEmpty()) {
      long lastSourceId = sources.get(sources.size() - 1).id();
      nextCursor = String.valueOf(lastSourceId + 1);
    }

    return new SourceListResponse(sources, sources.size(), nextCursor, hasMore);
  }

  /**
   * Get a single source by ID.
   *
   * @param databaseId The database ID
   * @param sourceId The source ID
   * @return SourceDto with the source information
   */
  public SourceDto getSource(@NotNull String databaseId, int sourceId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          Source source = txn.sourceTransaction().get(sourceId);
          return sourceDtoConverter.toDto(source);
        });
  }

  /**
   * Get the total number of sources in the specified database.
   *
   * @param databaseId The database ID to count sources in
   * @return The total number of sources in the database
   */
  public int getSourceCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().sourceIndex().count());
  }

  /**
   * Update an existing source in the database.
   *
   * @param databaseId The database ID
   * @param sourceId The source ID to update
   * @param sourceDto The source data to update with
   * @return The updated SourceDto
   * @throws MorphyServiceException if the source cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public SourceDto updateSource(
      @NotNull String databaseId, int sourceId, @NotNull SourceDto sourceDto) {
    try {
      // Convert DTO to entity
      Source source = sourceDtoConverter.toSource(sourceDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            txn.updateSourceById(sourceId, source);
            log.info("Successfully updated source {} in database '{}'", sourceId, databaseId);
          });

      // Return the updated source
      return getSource(databaseId, sourceId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update source " + sourceId + " in database '" + databaseId + "'", e);
    }
  }
}
