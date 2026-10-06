package se.yarin.morphy.service.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.games.dto.GameSearchResponse;

/**
 * A page of the games of a reference database that reached a position.
 *
 * @param summary what was played from the position; with the first page only
 * @param games the page of games
 */
public record PositionSearchResponse(
    @Nullable PositionSummary summary, @NotNull GameSearchResponse games) {}
