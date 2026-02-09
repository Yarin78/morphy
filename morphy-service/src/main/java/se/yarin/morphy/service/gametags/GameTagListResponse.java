package se.yarin.morphy.service.gametags;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.gametags.dto.GameTagDto;

/**
 * Response containing a paginated list of game tags.
 *
 * @param gameTags List of game tags in this page
 * @param count Number of game tags in this response
 * @param nextCursor Cursor for fetching the next page, null if no more pages
 * @param hasMore Whether there are more results available
 */
public record GameTagListResponse(
    List<GameTagDto> gameTags, int count, @Nullable String nextCursor, boolean hasMore) {}
