package se.yarin.morphy.service.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.games.dto.GameSearchResponse;

/**
 * A page of the games of a position index that reached a position.
 *
 * @param indexId the index searched
 * @param databaseId the database the games are of, which they open in
 * @param index whether the index was used, and how up to date it is
 * @param summary what was played from the position; with the first page only
 * @param games the page of games
 */
public record PositionSearchResponse(
    @NotNull String indexId,
    @NotNull String databaseId,
    @NotNull PositionIndexState index,
    @Nullable PositionSummary summary,
    @NotNull GameSearchResponse games) {}
