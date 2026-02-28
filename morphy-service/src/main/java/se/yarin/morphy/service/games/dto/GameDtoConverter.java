package se.yarin.morphy.service.games.dto;

import java.nio.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.chess.GameModel;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.games.Medal;
import se.yarin.morphy.games.annotations.AnnotationConverter;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;
import se.yarin.morphy.service.annotators.dto.AnnotatorDtoConverter;
import se.yarin.morphy.service.gametags.dto.GameTagDto;
import se.yarin.morphy.service.gametags.dto.GameTagDtoConverter;
import se.yarin.morphy.service.players.dto.PlayerDto;
import se.yarin.morphy.service.players.dto.PlayerDtoConverter;
import se.yarin.morphy.service.sources.dto.SourceDto;
import se.yarin.morphy.service.sources.dto.SourceDtoConverter;
import se.yarin.morphy.service.teams.dto.TeamDto;
import se.yarin.morphy.service.teams.dto.TeamDtoConverter;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;
import se.yarin.morphy.service.tournaments.dto.TournamentDtoConverter;
import se.yarin.morphy.text.TextModel;

/**
 * Converter for transforming Game entities from the database into GameDto objects for API
 * responses.
 */
@Component
public class GameDtoConverter {
  private static final Logger log = LoggerFactory.getLogger(GameDtoConverter.class);

  private final TournamentDtoConverter tournamentDtoConverter;
  private final PlayerDtoConverter playerDtoConverter;
  private final AnnotatorDtoConverter annotatorDtoConverter;
  private final SourceDtoConverter sourceDtoConverter;
  private final TeamDtoConverter teamDtoConverter;
  private final GameTagDtoConverter gameTagDtoConverter;

  public GameDtoConverter(
      PlayerDtoConverter playerDtoConverter,
      TournamentDtoConverter tournamentDtoConverter,
      AnnotatorDtoConverter annotatorDtoConverter,
      SourceDtoConverter sourceDtoConverter,
      TeamDtoConverter teamDtoConverter,
      GameTagDtoConverter gameTagDtoConverter) {
    this.playerDtoConverter = playerDtoConverter;
    this.tournamentDtoConverter = tournamentDtoConverter;
    this.annotatorDtoConverter = annotatorDtoConverter;
    this.sourceDtoConverter = sourceDtoConverter;
    this.teamDtoConverter = teamDtoConverter;
    this.gameTagDtoConverter = gameTagDtoConverter;
  }

  /**
   * Converts a Game to a GameDto without moves or text.
   *
   * @param game the game to convert
   * @return the GameDto
   */
  public GameDto toDto(@NotNull Game game) {
    return toDto(game, false, false, false, false, false, false);
  }

  /**
   * Converts a Game to a GameDto with full control over included information.
   *
   * @param game the game to convert
   * @param includeMoves whether to include the game moves (as PGN)
   * @param includeText whether to include the game text commentary
   * @param includeTournamentDetails whether to include full tournament details (dates, location,
   *     etc.)
   * @param includeSourceDetails whether to include full source details (publisher, date)
   * @param includeTeamDetails whether to include full team details (teamNumber, season, year,
   *     nation)
   * @return the GameDto
   */
  public GameDto toDto(
      @NotNull Game game,
      boolean includeMoves,
      boolean includeText,
      boolean includeTournamentDetails,
      boolean includeSourceDetails,
      boolean includeTeamDetails) {
    return toDto(
        game, includeMoves, includeText, includeTournamentDetails, includeSourceDetails,
        includeTeamDetails, false);
  }

  public GameDto toDto(
      @NotNull Game game,
      boolean includeMoves,
      boolean includeText,
      boolean includeTournamentDetails,
      boolean includeSourceDetails,
      boolean includeTeamDetails,
      boolean debugRawData) {
    // Basic game information
    Long id = (long) game.id();
    String type = game.guidingText() ? "text" : "game";
    String textTitle = game.guidingText() ? game.getTextTitle() : null;

    // Player information (only for games, not guiding text)
    PlayerDto whitePlayer = null;
    Integer whiteElo = null;
    PlayerDto blackPlayer = null;
    Integer blackElo = null;

    if (!game.guidingText()) {
      whitePlayer = convertPlayer(game.white());
      whiteElo = game.whiteElo() == 0 ? null : game.whiteElo();
      blackPlayer = convertPlayer(game.black());
      blackElo = game.blackElo() == 0 ? null : game.blackElo();
    }

    // Team information
    TeamDto whiteTeam = convertTeam(game, true, includeTeamDetails);
    TeamDto blackTeam = convertTeam(game, false, includeTeamDetails);

    // Game metadata
    var result = game.result(); // Mandatory PGN field - GameResult is never null
    var date = game.playedDate(); // Mandatory PGN field - Date class handles unset dates
    String eco = !game.eco().isSet() ? null : game.eco().toString();
    Integer round = game.round() == 0 ? null : game.round();
    Integer subRound = game.subRound() == 0 ? null : game.subRound();
    var lineEvaluation = game.lineEvaluation();

    // Tournament information
    TournamentDto tournament = convertTournament(game, includeTournamentDetails);

    // Source information
    SourceDto source = convertSource(game, includeSourceDetails);

    // Annotator
    AnnotatorDto annotator = convertAnnotator(game);

    // Game tag
    GameTagDto gameTag = convertGameTag(game);

    // Medals
    var medalSet = game.header().medals();
    List<String> medals =
        medalSet.isEmpty()
            ? null
            : medalSet.stream().map(Medal::name).toList();

    // Flags
    Boolean setupPosition = game.header().flags().contains(GameHeaderFlags.SETUP_POSITION) ? true : null;

    // Additional game metadata
    Integer noMoves = game.noMoves() == 0 ? null : game.noMoves();
    String ait = game.ait().isBlank() ? null : game.ait();
    String vcs = game.vcs().isBlank() ? null : game.vcs();
    String finalMaterial = game.finalMaterialString().isEmpty() ? null : game.finalMaterialString();
    Integer gameVersion = game.gameVersion() == 0 ? null : game.gameVersion();
    Long creationTimestamp =
        game.creationTimestamp() == 0 ? null : game.creationTimestamp();
    String lastChanged =
        game.lastChangedTimestamp() == 0 ? null : game.lastChangedTime().toString();

    // Moves (optional)
    GameMovesDto moves = includeMoves ? convertMoves(game) : null;

    // Text (optional)
    GameTextDto text = includeText ? convertText(game) : null;

    // Raw data (debug)
    byte[] rawData = null;
    byte[] rawExtendedData = null;
    if (debugRawData) {
      rawData = toBytes(game.database().gameHeaderIndex().getRaw(game.id()));
      rawExtendedData = toBytes(game.database().extendedGameHeaderStorage().getRaw(game.id()));
    }

    return new GameDto(
        id,
        type,
        textTitle,
        whitePlayer,
        whiteElo,
        blackPlayer,
        blackElo,
        whiteTeam,
        blackTeam,
        result,
        date,
        eco,
        round,
        subRound,
        lineEvaluation,
        tournament,
        source,
        annotator,
        gameTag,
        medals,
        setupPosition,
        noMoves,
        ait,
        vcs,
        finalMaterial,
        gameVersion,
        creationTimestamp,
        lastChanged,
        moves,
        text,
        rawData,
        rawExtendedData);
  }

  @Nullable
  private PlayerDto convertPlayer(@NotNull Player player) {
    // Return null if player is not set (id == -1)
    if (player.id() == -1) {
      return null;
    }
    return playerDtoConverter.toDto(player);
  }

  @Nullable
  private TeamDto convertTeam(@NotNull Game game, boolean isWhite, boolean includeDetails) {
    int teamId = isWhite ? game.whiteTeamId() : game.blackTeamId();

    if (teamId == -1) {
      return null;
    }

    Team team = isWhite ? game.whiteTeam() : game.blackTeam();

    // If details not requested, return minimal information (just id and title)
    if (!includeDetails) {
      return new TeamDto(
          (long) teamId,
          team.title().isEmpty() ? null : team.title(),
          null,
          null,
          null,
          null,
          null,
          null);
    }

    // Include full details (using the converter)
    return teamDtoConverter.toDto(team);
  }

  @Nullable
  private TournamentDto convertTournament(@NotNull Game game, boolean includeDetails) {
    int tournamentId = game.tournamentId();
    if (tournamentId == -1) {
      return null;
    }

    Tournament tournament = game.tournament();

    if (!includeDetails) {
      return new TournamentDto(
          (long) tournamentId,
          tournament.title(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    TournamentExtra extra = game.tournamentExtra();

    // Return full tournament details but without gameCount (not needed in game context)
    return tournamentDtoConverter.toDto(tournament, extra);
  }

  @Nullable
  private SourceDto convertSource(@NotNull Game game, boolean includeDetails) {
    int sourceId = game.sourceId();
    if (sourceId == -1) {
      return null;
    }

    Source source = game.source();

    // If details not requested, return minimal information (just id and title)
    if (!includeDetails) {
      return new SourceDto(
          (long) sourceId,
          source.title().isEmpty() ? null : source.title(),
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    // Include full details (using the converter)
    return sourceDtoConverter.toDto(source);
  }

  @Nullable
  private AnnotatorDto convertAnnotator(@NotNull Game game) {
    int annotatorId = game.annotatorId();
    if (annotatorId == -1) {
      return null;
    }

    Annotator annotator = game.annotator();
    return annotatorDtoConverter.toDto(annotator);
  }

  @Nullable
  private GameTagDto convertGameTag(@NotNull Game game) {
    int gameTagId = game.gameTagId();
    if (gameTagId == -1) {
      return null;
    }

    GameTag gameTag = game.gameTag();
    return gameTagDtoConverter.toDto(gameTag);
  }

  @Nullable
  private GameMovesDto convertMoves(@NotNull Game game) {
    try {
      GameModel model = game.getModel();
      PgnExporter exporter =
          new PgnExporter(
              PgnFormatOptions.DEFAULT,
              (AnnotationConverter.getRoundTripConverter())::convertToPgn);
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

  static byte[] toBytes(@NotNull ByteBuffer buf) {
    byte[] bytes = new byte[buf.remaining()];
    buf.get(bytes);
    return bytes;
  }
}
