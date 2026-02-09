package se.yarin.morphy.service.sources;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.sources.dto.SourceDto;

/**
 * Response containing a paginated list of sources.
 *
 * @param sources List of sources in this page
 * @param count Number of sources in this response
 * @param nextCursor Cursor for fetching the next page, null if no more pages
 * @param hasMore Whether there are more results available
 */
public record SourceListResponse(
    List<SourceDto> sources, int count, @Nullable String nextCursor, boolean hasMore) {}
