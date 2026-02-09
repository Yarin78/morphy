package se.yarin.morphy.service.games.dto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.chess.*;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.games.annotations.AnnotationConverter;
import se.yarin.morphy.text.ImmutableTextHeaderModel;
import se.yarin.morphy.text.ImmutableTextModel;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.morphy.text.TextModel;

/**
 * Converts GameDto objects to GameModel or TextModel.
 *
 * <p>This class handles the conversion from the DTO representation to the internal model
 * representation. Entity IDs are stored in the GameHeaderModel's internal fields when present in
 * the DTO. The actual entity resolution (validation or creation) happens later when the model is
 * saved via DatabaseWriteTransaction.
 */
@Component
public class GameDtoImporter {
  private static final Logger log = LoggerFactory.getLogger(GameDtoImporter.class);

  /**
   * Converts a GameDto to a GameModel.
   *
   * @param dto the GameDto to convert (must be a regular game, not guiding text)
   * @return the GameModel
   * @throws IllegalArgumentException if the DTO represents guiding text instead of a game
   */
  public GameModel toGameModel(@NotNull GameDto dto) {
    if ("text".equals(dto.type())) {
      throw new IllegalArgumentException(
          "Cannot convert guiding text to GameModel. Use toTextModel() instead.");
    }

    GameHeaderModel headerModel = buildGameHeaderModel(dto);
    return buildGameModel(headerModel, dto);
  }

  /**
   * Converts a GameDto to a TextModel.
   *
   * @param dto the GameDto to convert (must be guiding text, not a regular game)
   * @return the TextModel
   * @throws IllegalArgumentException if the DTO represents a game instead of guiding text
   */
  public TextModel toTextModel(@NotNull GameDto dto) {
    if (!"text".equals(dto.type())) {
      throw new IllegalArgumentException(
          "Cannot convert regular game to TextModel. Use toGameModel() instead.");
    }

    GameHeaderModel headerModel = buildGameHeaderModel(dto);
    return buildTextModel(headerModel, dto);
  }

  /**
   * Builds a GameHeaderModel from a GameDto. Stores entity IDs in internal fields when present.
   *
   * @param dto the GameDto
   * @return the GameHeaderModel
   */
  private GameHeaderModel buildGameHeaderModel(@NotNull GameDto dto) {

    GameHeaderModel headerModel = new GameHeaderModel();

    boolean isText = "text".equals(dto.type());

    if (!isText) {
      // Set player information
      String whiteName = "?";
      if (dto.whitePlayer() != null && dto.whitePlayer().lastName() != null) {
        whiteName = dto.whitePlayer().lastName();
        if (dto.whitePlayer().firstName() != null && !dto.whitePlayer().firstName().isEmpty()) {
          whiteName += ", " + dto.whitePlayer().firstName();
        }
      }
      headerModel.setWhite(whiteName);

      String blackName = "?";
      if (dto.blackPlayer() != null && dto.blackPlayer().lastName() != null) {
        blackName = dto.blackPlayer().lastName();
        if (dto.blackPlayer().firstName() != null && !dto.blackPlayer().firstName().isEmpty()) {
          blackName += ", " + dto.blackPlayer().firstName();
        }
      }
      headerModel.setBlack(blackName);

      // Store entity IDs if present
      if (dto.whitePlayer() != null && dto.whitePlayer().id() != null) {
        headerModel.setField(
            se.yarin.morphy.GameAdapter.WHITE_ID, dto.whitePlayer().id().intValue());
      }
      if (dto.blackPlayer() != null && dto.blackPlayer().id() != null) {
        headerModel.setField(
            se.yarin.morphy.GameAdapter.BLACK_ID, dto.blackPlayer().id().intValue());
      }

      // Set ELO ratings
      if (dto.whiteElo() != null) {
        headerModel.setWhiteElo(dto.whiteElo());
      }
      if (dto.blackElo() != null) {
        headerModel.setBlackElo(dto.blackElo());
      }

      // Set team information
      if (dto.whiteTeam() != null && dto.whiteTeam().title() != null) {
        headerModel.setWhiteTeam(dto.whiteTeam().title());
        if (dto.whiteTeam().id() != null) {
          headerModel.setField(
              se.yarin.morphy.GameAdapter.WHITE_TEAM_ID, dto.whiteTeam().id().intValue());
        }
      }
      if (dto.blackTeam() != null && dto.blackTeam().title() != null) {
        headerModel.setBlackTeam(dto.blackTeam().title());
        if (dto.blackTeam().id() != null) {
          headerModel.setField(
              se.yarin.morphy.GameAdapter.BLACK_TEAM_ID, dto.blackTeam().id().intValue());
        }
      }
    }

    // Set game metadata
    headerModel.setResult(dto.result() != null ? dto.result() : GameResult.NOT_FINISHED);
    headerModel.setDate(dto.date() != null ? dto.date() : Date.unset());

    if (dto.eco() != null) {
      try {
        headerModel.setEco(new Eco(dto.eco()));
      } catch (IllegalArgumentException e) {
        log.warn("Invalid ECO code in DTO: {}", dto.eco());
        headerModel.setEco(Eco.unset());
      }
    }

    if (dto.round() != null) {
      headerModel.setRound(dto.round());
    }

    if (dto.subRound() != null) {
      headerModel.setSubRound(dto.subRound());
    }

    if (dto.lineEvaluation() != null) {
      headerModel.setLineEvaluation(dto.lineEvaluation());
    }

    // Set tournament information
    if (dto.tournament() != null) {
      if (dto.tournament().name() != null) {
        headerModel.setEvent(dto.tournament().name());
      }
      if (dto.tournament().id() != null) {
        headerModel.setField(
            se.yarin.morphy.GameAdapter.EVENT_ID, dto.tournament().id().intValue());
      }
      if (dto.tournament().startDate() != null) {
        headerModel.setEventDate(dto.tournament().startDate());
      }
      if (dto.tournament().endDate() != null) {
        headerModel.setEventEndDate(dto.tournament().endDate());
      }
      if (dto.tournament().site() != null) {
        headerModel.setEventSite(dto.tournament().site());
      }
      if (dto.tournament().country() != null) {
        headerModel.setEventCountry(dto.tournament().country());
      }
      if (dto.tournament().category() != null) {
        headerModel.setEventCategory(dto.tournament().category());
      }
      if (dto.tournament().rounds() != null) {
        headerModel.setEventRounds(dto.tournament().rounds());
      }
      if (dto.tournament().type() != null) {
        headerModel.setEventType(dto.tournament().type());
      }
      if (dto.tournament().timeControl() != null) {
        headerModel.setEventTimeControl(dto.tournament().timeControl());
      }
    }

    // Set source information
    if (dto.source() != null) {
      if (dto.source().id() != null) {
        headerModel.setField(se.yarin.morphy.GameAdapter.SOURCE_ID, dto.source().id().intValue());
      }
      if (dto.source().title() != null) {
        headerModel.setSourceTitle(dto.source().title());
      }
      if (dto.source().publisher() != null) {
        headerModel.setSource(dto.source().publisher());
      }
      if (dto.source().date() != null) {
        headerModel.setSourceDate(dto.source().date());
      }
    }

    // Set annotator information
    if (dto.annotator() != null) {
      if (dto.annotator().id() != null) {
        headerModel.setField(
            se.yarin.morphy.GameAdapter.ANNOTATOR_ID, dto.annotator().id().intValue());
      }
      if (dto.annotator().name() != null) {
        headerModel.setAnnotator(dto.annotator().name());
      }
    }

    // Set game tag information
    if (dto.gameTag() != null) {
      if (dto.gameTag().id() != null) {
        headerModel.setField(
            se.yarin.morphy.GameAdapter.GAME_TAG_ID, dto.gameTag().id().intValue());
      }
      if (dto.gameTag().englishTitle() != null) {
        headerModel.setGameTag(dto.gameTag().englishTitle());
      }
    }

    return headerModel;
  }

  /**
   * Builds a GameModel from a GameHeaderModel and GameDto.
   *
   * @param headerModel the header model
   * @param dto the GameDto
   * @return the GameModel
   */
  private GameModel buildGameModel(@NotNull GameHeaderModel headerModel, @NotNull GameDto dto) {

    GameMovesModel movesModel;

    // Parse moves if present
    if (dto.moves() != null && dto.moves().pgn() != null && !dto.moves().pgn().isEmpty()) {
      try {
        // Parse the moves-only PGN directly
        PgnParser parser =
            new PgnParser((AnnotationConverter.getRoundTripConverter())::convertToChessBase);
        movesModel = parser.parseMoves(dto.moves().pgn());

      } catch (PgnFormatException e) {
        log.error("Failed to parse PGN from DTO: {}", e.getMessage());
        // Fall back to empty moves
        movesModel = new GameMovesModel();
      }
    } else {
      // No moves provided, create empty moves model
      movesModel = new GameMovesModel();
    }

    // Build and return the complete GameModel
    return new GameModel(headerModel, movesModel);
  }

  /**
   * Builds a TextModel from a GameHeaderModel and GameDto.
   *
   * @param headerModel the header model (contains tournament/source/annotator info)
   * @param dto the GameDto
   * @return the TextModel
   */
  private TextModel buildTextModel(@NotNull GameHeaderModel headerModel, @NotNull GameDto dto) {

    // Build TextHeaderModel from the GameHeaderModel data
    ImmutableTextHeaderModel.Builder textHeaderBuilder = ImmutableTextHeaderModel.builder();

    if (headerModel.getEvent() != null) {
      textHeaderBuilder.tournament(headerModel.getEvent());
    }
    if (headerModel.getEventDate() != null) {
      textHeaderBuilder.tournamentDate(headerModel.getEventDate());
    }
    if (headerModel.getAnnotator() != null) {
      textHeaderBuilder.annotator(headerModel.getAnnotator());
    }

    if (headerModel.getSourceTitle() != null) {
      textHeaderBuilder.source(headerModel.getSourceTitle());
    }

    if (headerModel.getRound() != null) {
      textHeaderBuilder.round(headerModel.getRound());
    }
    if (headerModel.getSubRound() != null) {
      textHeaderBuilder.subRound(headerModel.getSubRound());
    }

    var textHeader = textHeaderBuilder.build();

    // Build TextContentsModel
    TextContentsModel textContents = new TextContentsModel();
    if (dto.text() != null && dto.text().contents() != null) {
      textContents.setContents(dto.text().contents());
    }

    // Build and return TextModel
    return ImmutableTextModel.builder().header(textHeader).contents(textContents).build();
  }
}
