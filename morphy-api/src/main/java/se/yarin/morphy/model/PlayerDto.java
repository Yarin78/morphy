package se.yarin.morphy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/** Detailed information about a chess player. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "lastName", "firstName", "gameCount", "fideId", "chessBaseId"})
public record PlayerDto(
    Long id,
    @Nullable String lastName,
    @Nullable String firstName,
    @Nullable Integer gameCount,
    // v2 superset: links to ChessBase's external player data. Unset (null) for v1 databases.
    @Nullable Long fideId,
    @Nullable Long chessBaseId) implements EntityDto {}
