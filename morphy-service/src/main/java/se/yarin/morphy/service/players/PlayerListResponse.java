package se.yarin.morphy.service.players;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.players.dto.PlayerDto;

/**
 * Response containing a paginated list of players.
 *
 * @param players List of players in this page
 * @param count Number of players in this response
 * @param nextCursor Cursor for fetching the next page, null if no more pages
 * @param hasMore Whether there are more results available
 */
public record PlayerListResponse(
    List<PlayerDto> players, int count, @Nullable String nextCursor, boolean hasMore) {}
