package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;

/** Detailed information about a chess event/tournament. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "startDate", "endDate", "site", "country", "category", "rounds", "type", "timeControl"})
public record EventDetailsDto(
    Long id,
    String name,
    @Nullable Date startDate,
    @Nullable Date endDate,
    @Nullable String site,
    @Nullable String country,
    @Nullable Integer category,
    @Nullable Integer rounds,
    @Nullable String type,
    @Nullable String timeControl) {}
