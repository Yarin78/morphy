package se.yarin.morphy.service.games;

import java.util.List;
import se.yarin.morphy.service.games.dto.GameDto;

public record GameHeaderListResponse(
    List<GameDto> games, int count, String nextCursor, boolean hasMore) {}
