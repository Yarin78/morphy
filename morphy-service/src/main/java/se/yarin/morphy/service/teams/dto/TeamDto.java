package se.yarin.morphy.service.teams.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/** Detailed information about a chess team. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "title",
  "teamNumber",
  "season",
  "year",
  "nation",
  "gameCount",
  "rawData"
})
public record TeamDto(
    Long id,
    @Nullable String title,
    @Nullable Integer teamNumber,
    @Nullable Boolean season,
    @Nullable Integer year,
    @Nullable String nation,
    @Nullable Integer gameCount,
    @Nullable byte[] rawData) {}
