package se.yarin.morphy.service.tournaments;

import java.util.List;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;

/** Response object for paginated tournament lists. */
public record TournamentListResponse(
    List<TournamentDto> tournaments, int count, String nextCursor, boolean hasMore) {}
