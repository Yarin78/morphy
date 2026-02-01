package se.yarin.morphy.service.games;

import java.util.List;

public record GameHeaderListResponse(
    List<GameHeaderResponse> games, int count, String nextCursor, boolean hasMore) {}
