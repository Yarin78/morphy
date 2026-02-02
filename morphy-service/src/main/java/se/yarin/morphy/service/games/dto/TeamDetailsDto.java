package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/**
 * Details about a chess team.
 *
 * <p>When fetching game lists, only id and title are included by default for performance. Full
 * details (teamNumber, season, year, nation) are included when fetching individual games or when
 * explicitly requested.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "title", "teamNumber", "season", "year", "nation"})
public record TeamDetailsDto(
    Long id,
    @Nullable String title,
    @Nullable Integer teamNumber,
    @Nullable Boolean season,
    @Nullable Integer year,
    @Nullable String nation) {}
