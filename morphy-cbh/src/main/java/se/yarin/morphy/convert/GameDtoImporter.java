package se.yarin.morphy.convert;

import se.yarin.morphy.model.GameDto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.morphy.text.ImmutableTextHeaderModel;
import se.yarin.morphy.text.ImmutableTextModel;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.morphy.text.TextModel;

/**
 * Converts GameDto objects to GameModel or TextModel.
 *
 * <p>This class handles the conversion from the DTO representation to the internal model
 * representation. An entity DTO that has an id binds the header to that existing entity; the
 * entity itself is resolved (validated, or found or created by name) when the model is saved via
 * DatabaseWriteTransaction.
 */
public class GameDtoImporter {
  private static final Logger log = LoggerFactory.getLogger(GameDtoImporter.class);

  /**
   * Converts a GameDto to a GameModel.
   *
   * @param dto the GameDto to convert (must be a regular game, not guiding text)
   * @return the GameModel
   * @throws IllegalArgumentException if the DTO represents guiding text instead of a game, or its
   *     moves can't be read
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
   * Builds a GameHeaderModel from a GameDto. An entity DTO with an id binds the header to that
   * existing entity; one without is resolved by name when the game is written.
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
        headerModel.setWhiteId(dto.whitePlayer().id());
      }
      if (dto.blackPlayer() != null && dto.blackPlayer().id() != null) {
        headerModel.setBlackId(dto.blackPlayer().id());
      }

      // Set ELO ratings
      if (dto.whiteElo() != null) {
        headerModel.setWhiteElo(dto.whiteElo());
      }
      if (dto.blackElo() != null) {
        headerModel.setBlackElo(dto.blackElo());
      }

      // Set team information
      if (dto.whiteTeam() != null) {
        headerModel.setWhiteTeam(dto.whiteTeam().title());
        headerModel.setWhiteTeamId(dto.whiteTeam().id());
      }
      if (dto.blackTeam() != null) {
        headerModel.setBlackTeam(dto.blackTeam().title());
        headerModel.setBlackTeamId(dto.blackTeam().id());
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
      if (dto.tournament().title() != null) {
        headerModel.setEvent(dto.tournament().title());
      }
      if (dto.tournament().id() != null) {
        headerModel.setEventId(dto.tournament().id());
      }
      if (dto.tournament().startDate() != null) {
        headerModel.setEventDate(dto.tournament().startDate());
      }
      if (dto.tournament().endDate() != null) {
        headerModel.setEventEndDate(dto.tournament().endDate());
      }
      if (dto.tournament().place() != null) {
        headerModel.setEventSite(dto.tournament().place());
      }
      if (dto.tournament().nation() != null) {
        headerModel.setEventCountry(dto.tournament().nation());
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
        headerModel.setSourceId(dto.source().id());
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
        headerModel.setAnnotatorId(dto.annotator().id());
      }
      if (dto.annotator().name() != null) {
        headerModel.setAnnotator(dto.annotator().name());
      }
    }

    // Set game tag information
    if (dto.gameTag() != null) {
      if (dto.gameTag().id() != null) {
        headerModel.setGameTagId(dto.gameTag().id());
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
    // Moves are required to read back; an unreadable movetext is an invalid game, not an empty one
    String pgn = dto.moves() == null ? null : dto.moves().pgn();
    GameMovesModel movesModel =
        pgn == null || pgn.isEmpty() ? new GameMovesModel() : GameMovesPgn.fromPgn(pgn);

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
