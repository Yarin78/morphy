package se.yarin.morphy.service.tournaments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;

/** Detailed information about a chess tournament. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "title",
  "startDate",
  "endDate",
  "place",
  "country",
  "category",
  "rounds",
  "type",
  "timeControl",
  "complete",
  "teamTournament",
  "gameCount",
  "rawData",
  "rawExtraData"
})
public record TournamentDto(
    Long id,
    String title,
    @Nullable Date startDate,
    @Nullable Date endDate,
    @Nullable String place,
    @Nullable String country,
    @Nullable Integer category,
    @Nullable Integer rounds,
    @Nullable String type,
    @Nullable String timeControl,
    @Nullable Boolean complete,
    @Nullable Boolean teamTournament,
    @Nullable Integer gameCount,
    @Nullable byte[] rawData,
    @Nullable byte[] rawExtraData) {}
