package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;
import se.yarin.morphy.service.gametags.dto.GameTagDto;
import se.yarin.morphy.service.players.dto.PlayerDto;
import se.yarin.morphy.service.sources.dto.SourceDto;
import se.yarin.morphy.service.teams.dto.TeamDto;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;

/**
 * Data Transfer Object for a chess game.
 *
 * <p>This DTO contains all game information including metadata, player details, tournament
 * information, and optionally the game moves and text commentary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "type",
  "whitePlayer",
  "whiteElo",
  "blackPlayer",
  "blackElo",
  "whiteTeam",
  "blackTeam",
  "result",
  "date",
  "eco",
  "round",
  "subRound",
  "lineEvaluation",
  "tournament",
  "source",
  "annotator",
  "gameTag",
  "medals",
  "noMoves",
  "ait",
  "vcs",
  "finalMaterial",
  "gameVersion",
  "creationTimestamp",
  "lastChanged",
  "moves",
  "text",
  "rawData",
  "rawExtendedData"
})
public record GameDto(
    // Game identity
    Long id,
    String type, // "game" for regular chess game, "text" for guiding text

    // Player information
    @Nullable PlayerDto whitePlayer,
    @Nullable Integer whiteElo,
    @Nullable PlayerDto blackPlayer,
    @Nullable Integer blackElo,

    // Team information
    @Nullable TeamDto whiteTeam,
    @Nullable TeamDto blackTeam,

    // Game metadata (result and date are mandatory PGN fields)
    GameResult result, // Mandatory PGN field
    Date date, // Mandatory PGN field (Date class handles unset dates as ????.??.??)
    @Nullable String eco,
    @Nullable Integer round,
    @Nullable Integer subRound,
    @Nullable NAG lineEvaluation,

    // Tournament information
    @Nullable TournamentDto tournament,

    // Source information
    @Nullable SourceDto source,

    // Annotator
    @Nullable AnnotatorDto annotator,

    // Game tag
    @Nullable GameTagDto gameTag,

    // Medals
    @Nullable List<String> medals,

    // Additional game metadata
    @Nullable Integer noMoves,
    @Nullable String ait,
    @Nullable String vcs,
    @Nullable String finalMaterial,
    @Nullable Integer gameVersion,
    @Nullable Long creationTimestamp,
    @Nullable String lastChanged,

    // Game content (nullable for header-only queries)
    @Nullable GameMovesDto moves,
    @Nullable GameTextDto text,

    // Debug: raw storage bytes
    @Nullable byte[] rawData,
    @Nullable byte[] rawExtendedData) {}
