package se.yarin.morphy.service.annotators;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.filter.AnnotatorQueryBuilder;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;
import se.yarin.morphy.service.annotators.dto.AnnotatorDtoConverter;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.search.EntitySearchExecutor;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@Service
public class AnnotatorsService {
  private static final Logger log = LoggerFactory.getLogger(AnnotatorsService.class);

  private final DatabaseService databaseService;
  private final AnnotatorDtoConverter annotatorDtoConverter;
  private final EntitySearchExecutor entitySearchExecutor;

  public AnnotatorsService(
      DatabaseService databaseService,
      AnnotatorDtoConverter annotatorDtoConverter,
      EntitySearchExecutor entitySearchExecutor) {
    this.databaseService = databaseService;
    this.annotatorDtoConverter = annotatorDtoConverter;
    this.entitySearchExecutor = entitySearchExecutor;
  }

  /**
   * Get annotators with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (annotator ID) to start from, null for beginning
   * @param limit Maximum number of annotators to return (max 1000)
   * @return Paginated response with annotators and pagination info
   */
  public AnnotatorListResponse getAnnotators(
      @NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new AnnotatorListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<AnnotatorDto> allAnnotators =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream annotators from the specified start ID
              return txn.annotatorTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(annotatorDtoConverter::toDto)
                  .collect(Collectors.toList());
            });

    boolean hasMore = allAnnotators.size() > limit;
    List<AnnotatorDto> annotators = hasMore ? allAnnotators.subList(0, limit) : allAnnotators;

    String nextCursor = null;
    if (hasMore && !annotators.isEmpty()) {
      long lastAnnotatorId = annotators.get(annotators.size() - 1).id();
      nextCursor = String.valueOf(lastAnnotatorId + 1);
    }

    return new AnnotatorListResponse(annotators, annotators.size(), nextCursor, hasMore);
  }

  /**
   * Get a single annotator by ID.
   *
   * @param databaseId The database ID
   * @param annotatorId The annotator ID
   * @return AnnotatorDto with the annotator information, or null if the annotator doesn't exist,
   *     is deleted, or is unused (has no games)
   */
  public @Nullable AnnotatorDto getAnnotator(@NotNull String databaseId, int annotatorId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            Annotator annotator = txn.annotatorTransaction().get(annotatorId);
            // Return null if annotator is unused (has no games)
            if (annotator.count() == 0) {
              return null;
            }
            return annotatorDtoConverter.toDto(annotator);
          } catch (IllegalArgumentException e) {
            // Annotator doesn't exist or is deleted
            return null;
          }
        });
  }

  /**
   * Get the total number of annotators in the specified database.
   *
   * @param databaseId The database ID to count annotators in
   * @return The total number of annotators in the database
   */
  public int getAnnotatorCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().annotatorIndex().count());
  }

  /**
   * Update an existing annotator in the database.
   *
   * @param databaseId The database ID
   * @param annotatorId The annotator ID to update
   * @param annotatorDto The annotator data to update with
   * @return The updated AnnotatorDto
   * @throws MorphyServiceException if the annotator cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public AnnotatorDto updateAnnotator(
      @NotNull String databaseId, int annotatorId, @NotNull AnnotatorDto annotatorDto) {
    try {
      // Convert DTO to entity
      Annotator annotator = annotatorDtoConverter.toAnnotator(annotatorDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            // Check if another annotator with the same key exists
            Annotator existing = txn.annotatorTransaction().get(annotator);
            if (existing != null && existing.id() != annotatorId) {
              throw new IllegalArgumentException(
                  "Cannot update annotator: another annotator with the same key already exists");
            }

            txn.updateAnnotatorById(annotatorId, annotator);
            log.info("Successfully updated annotator {} in database '{}'", annotatorId, databaseId);
          });

      // Return the updated annotator
      return getAnnotator(databaseId, annotatorId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update annotator " + annotatorId + " in database '" + databaseId + "'", e);
    }
  }

  public EntitySearchResponse<AnnotatorDto> searchAnnotators(
      @NotNull String databaseId, @NotNull EntitySearchRequest request) {
    AnnotatorQueryBuilder queryBuilder = new AnnotatorQueryBuilder();
    return databaseService.withReadTransaction(
        databaseId,
        txn ->
            entitySearchExecutor.executeSearch(
                txn,
                EntityType.ANNOTATOR,
                queryBuilder,
                annotatorDtoConverter::toDto,
                request));
  }
}
