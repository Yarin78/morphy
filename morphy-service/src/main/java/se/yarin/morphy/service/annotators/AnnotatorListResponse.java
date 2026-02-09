package se.yarin.morphy.service.annotators;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;

/**
 * Response containing a paginated list of annotators.
 *
 * @param annotators List of annotators in this page
 * @param count Number of annotators in this response
 * @param nextCursor Cursor for fetching the next page, null if no more pages
 * @param hasMore Whether there are more results available
 */
public record AnnotatorListResponse(
    List<AnnotatorDto> annotators, int count, @Nullable String nextCursor, boolean hasMore) {}
