package se.yarin.morphy.service.positions;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A position index, as listed: its definition and whether it can be searched.
 *
 * @param id the index's id
 * @param name its short name
 * @param databaseId the database whose games it holds, which they open in
 * @param filter the games it holds; blank for every game
 * @param status {@code ready} to search; {@code missing} or {@code stale} (the database has changed,
 *     or the index was built with another filter) to build; {@code building}; or {@code failed},
 *     the last build having failed
 * @param message what's to know about the status: the build's progress, why it failed, or how to
 *     build the index
 * @param games the games it holds, when built
 * @param builtAt when it was built, if it was
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PositionIndexInfo(
    @NotNull String id,
    @NotNull String name,
    @NotNull String databaseId,
    @NotNull String filter,
    @NotNull String status,
    @Nullable String message,
    @Nullable Long games,
    @Nullable String builtAt) {}
