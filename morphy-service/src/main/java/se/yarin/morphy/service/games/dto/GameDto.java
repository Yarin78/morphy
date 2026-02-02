package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;

/**
 * Data Transfer Object for a chess game.
 *
 * <p>This DTO contains all game information including metadata, player details, event information,
 * and optionally the game moves and text commentary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id", "type",
    "whiteId", "white", "whiteElo", "blackId", "black", "blackElo",
    "whiteTeam", "blackTeam",
    "result", "date", "eco", "round", "subRound", "lineEvaluation",
    "event", "source", "annotator", "gameTag",
    "moves", "text"
})
public record GameDto(
    // Game identity
    Long id,
    String type,  // "game" for regular chess game, "text" for guiding text

    // Player information (white and black are mandatory PGN fields)
    @Nullable Long whiteId,
    String white,  // Mandatory PGN field - empty string if not set
    @Nullable Integer whiteElo,
    @Nullable Long blackId,
    String black,  // Mandatory PGN field - empty string if not set
    @Nullable Integer blackElo,

    // Team information
    @Nullable TeamDetailsDto whiteTeam,
    @Nullable TeamDetailsDto blackTeam,

    // Game metadata (result and date are mandatory PGN fields)
    GameResult result,  // Mandatory PGN field
    Date date,  // Mandatory PGN field (Date class handles unset dates as ????.??.??)
    @Nullable String eco,
    @Nullable Integer round,
    @Nullable Integer subRound,
    @Nullable NAG lineEvaluation,

    // Event information
    @Nullable EventDetailsDto event,

    // Source information
    @Nullable SourceDetailsDto source,

    // Annotator
    @Nullable AnnotatorDetailsDto annotator,

    // Game tag
    @Nullable GameTagDetailsDto gameTag,

    // Game content (nullable for header-only queries)
    @Nullable GameMovesDto moves,
    @Nullable GameTextDto text) {}
