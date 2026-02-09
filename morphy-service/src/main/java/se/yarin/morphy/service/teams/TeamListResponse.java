package se.yarin.morphy.service.teams;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.teams.dto.TeamDto;

/**
 * Response containing a paginated list of teams.
 *
 * @param teams List of teams in this page
 * @param count Number of teams in this response
 * @param nextCursor Cursor for fetching the next page, null if no more pages
 * @param hasMore Whether there are more results available
 */
public record TeamListResponse(
    List<TeamDto> teams, int count, @Nullable String nextCursor, boolean hasMore) {}
