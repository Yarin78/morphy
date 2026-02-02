package se.yarin.morphy.service.games.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.chess.GameModel;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.text.TextModel;

/**
 * Converter for transforming Game entities from the database into GameDto objects for API
 * responses.
 */
@Component
public class GameDtoConverter {
  private static final Logger log = LoggerFactory.getLogger(GameDtoConverter.class);

  /**
   * Converts a Game to a GameDto without moves or text.
   *
   * @param game the game to convert
   * @return the GameDto
   */
  public GameDto toDto(@NotNull Game game) {
    return toDto(game, false, false, false, false, false);
  }

  /**
   * Converts a Game to a GameDto with full control over included information.
   *
   * @param game the game to convert
   * @param includeMoves whether to include the game moves (as PGN)
   * @param includeText whether to include the game text commentary
   * @param includeEventDetails whether to include full event details (dates, location, etc.)
   * @param includeSourceDetails whether to include full source details (publisher, date)
   * @param includeTeamDetails whether to include full team details (teamNumber, season, year, nation)
   * @return the GameDto
   */
  public GameDto toDto(
      @NotNull Game game,
      boolean includeMoves,
      boolean includeText,
      boolean includeEventDetails,
      boolean includeSourceDetails,
      boolean includeTeamDetails) {
    // Basic game information
    Long id = (long) game.id();
    String type = game.guidingText() ? "text" : "game";

    // Player information
    Long whiteId = game.whitePlayerId() == -1 ? null : (long) game.whitePlayerId();
    String white = whiteId != null ? game.white().getFullName() : "";  // Mandatory PGN field
    Integer whiteElo = game.whiteElo() == 0 ? null : game.whiteElo();

    Long blackId = game.blackPlayerId() == -1 ? null : (long) game.blackPlayerId();
    String black = blackId != null ? game.black().getFullName() : "";  // Mandatory PGN field
    Integer blackElo = game.blackElo() == 0 ? null : game.blackElo();

    // Team information
    TeamDetailsDto whiteTeam = convertTeam(game, true, includeTeamDetails);
    TeamDetailsDto blackTeam = convertTeam(game, false, includeTeamDetails);

    // Game metadata
    var result = game.result();  // Mandatory PGN field - GameResult is never null
    var date = game.playedDate();  // Mandatory PGN field - Date class handles unset dates
    String eco = !game.eco().isSet() ? null : game.eco().toString();
    Integer round = game.round() == 0 ? null : game.round();
    Integer subRound = game.subRound() == 0 ? null : game.subRound();
    var lineEvaluation = game.lineEvaluation();

    // Event information
    EventDetailsDto event = convertEvent(game, includeEventDetails);

    // Source information
    SourceDetailsDto source = convertSource(game, includeSourceDetails);

    // Annotator
    AnnotatorDetailsDto annotator = convertAnnotator(game);

    // Game tag
    GameTagDetailsDto gameTag = convertGameTag(game);

    // Moves (optional)
    GameMovesDto moves = includeMoves ? convertMoves(game) : null;

    // Text (optional)
    GameTextDto text = includeText ? convertText(game) : null;

    return new GameDto(
        id,
        type,
        whiteId,
        white,
        whiteElo,
        blackId,
        black,
        blackElo,
        whiteTeam,
        blackTeam,
        result,
        date,
        eco,
        round,
        subRound,
        lineEvaluation,
        event,
        source,
        annotator,
        gameTag,
        moves,
        text);
  }

  @Nullable
  private TeamDetailsDto convertTeam(@NotNull Game game, boolean isWhite, boolean includeDetails) {
    int teamId = isWhite ? game.whiteTeamId() : game.blackTeamId();

    if (teamId == -1) {
      return null;
    }

    Team team = isWhite ? game.whiteTeam() : game.blackTeam();
    String title = team.title().isEmpty() ? null : team.title();

    // If details not requested, return minimal information
    if (!includeDetails) {
      return new TeamDetailsDto((long) teamId, title, null, null, null, null);
    }

    // Include full details
    Integer teamNumber = team.teamNumber() == 0 ? null : team.teamNumber();
    Boolean season = team.season() ? true : null;
    Integer year = team.year() == 0 ? null : team.year();
    String nation = team.nation() != Nation.NONE ? team.nation().getIocCode() : null;

    return new TeamDetailsDto((long) teamId, title, teamNumber, season, year, nation);
  }

  @Nullable
  private EventDetailsDto convertEvent(@NotNull Game game, boolean includeDetails) {
    int tournamentId = game.tournamentId();
    if (tournamentId == -1) {
      return null;
    }

    Tournament tournament = game.tournament();
    String name = tournament.title().isEmpty() ? "" : tournament.title();

    // If details not requested, return minimal information
    if (!includeDetails) {
      return new EventDetailsDto((long) tournamentId, name, null, null, null, null, null, null, null, null);
    }

    // Include full details
    TournamentExtra extra = game.tournamentExtra();

    String timeControl = null;
    if (tournament.timeControl() != null
        && tournament.timeControl() != TournamentTimeControl.NORMAL) {
      timeControl = tournament.timeControl().getLongName();
    }

    return new EventDetailsDto(
        (long) tournamentId,
        name,
        tournament.date().isUnset() ? null : tournament.date(),
        extra.endDate().isUnset() ? null : extra.endDate(),
        tournament.place().isEmpty() ? null : tournament.place(),
        tournament.nation() != Nation.NONE ? tournament.nation().getIocCode() : null,
        tournament.category() == 0 ? null : tournament.category(),
        tournament.rounds() == 0 ? null : tournament.rounds(),
        tournament.getPrettyTypeName().isEmpty() ? null : tournament.getPrettyTypeName(),
        timeControl);
  }

  @Nullable
  private SourceDetailsDto convertSource(@NotNull Game game, boolean includeDetails) {
    int sourceId = game.sourceId();
    if (sourceId == -1) {
      return null;
    }

    Source source = game.source();
    String title = source.title().isEmpty() ? null : source.title();

    // If details not requested, return minimal information (just id and title)
    if (!includeDetails) {
      return new SourceDetailsDto((long) sourceId, null, title, null);
    }

    // Include full details
    return new SourceDetailsDto(
        (long) sourceId,
        source.publisher().isEmpty() ? null : source.publisher(),
        title,
        source.date().isUnset() ? null : source.date());
  }

  @Nullable
  private AnnotatorDetailsDto convertAnnotator(@NotNull Game game) {
    int annotatorId = game.annotatorId();
    if (annotatorId == -1) {
      return null;
    }

    Annotator annotator = game.annotator();

    return new AnnotatorDetailsDto(
        (long) annotatorId, annotator.name().isEmpty() ? null : annotator.name());
  }

  @Nullable
  private GameTagDetailsDto convertGameTag(@NotNull Game game) {
    int gameTagId = game.gameTagId();
    if (gameTagId == -1) {
      return null;
    }

    GameTag gameTag = game.gameTag();

    return new GameTagDetailsDto(
        (long) gameTagId, gameTag.englishTitle().isEmpty() ? null : gameTag.englishTitle());
  }

  @Nullable
  private GameMovesDto convertMoves(@NotNull Game game) {
    try {
      GameModel model = game.getModel();
      PgnExporter exporter = new PgnExporter();
      String movesPgn = exporter.exportMovesOnly(model.moves());
      return new GameMovesDto(movesPgn);
    } catch (Exception e) {
      log.error("Failed to export moves for game {}", game.id(), e);
      return null;
    }
  }

  @Nullable
  private GameTextDto convertText(@NotNull Game game) {
    try {
      if (game.guidingText()) {
        TextModel textModel = game.getTextModel();
        String contents = textModel.contents().getContents();
        return new GameTextDto(contents);
      }
      return null;
    } catch (Exception e) {
      log.error("Failed to load text for game {}", game.id(), e);
      return null;
    }
  }
}
