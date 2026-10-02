package se.yarin.morphy.chessbase.convert;

import se.yarin.morphy.model.GameDto;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.morphy.chessbase.text.ImmutableTextHeaderModel;
import se.yarin.morphy.chessbase.text.ImmutableTextModel;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextModel;
import se.yarin.morphy.pgn.PgnGameMapper;

/**
 * Converts GameDto objects to GameModel or TextModel.
 *
 * <p>This class handles the conversion from the DTO representation to the internal model
 * representation. An entity DTO that has an id binds the header to that existing entity; the
 * entity itself is resolved (validated, or found or created by name) when the model is saved via
 * DatabaseWriteTransaction.
 */
public class GameDtoImporter {

  private final PgnGameMapper mapper = new PgnGameMapper(GameMovesDtos.CODEC);

  /**
   * Converts a GameDto to a GameModel.
   *
   * @param dto the GameDto to convert (must be a regular game, not guiding text)
   * @return the GameModel
   * @throws IllegalArgumentException if the DTO represents guiding text instead of a game, or its
   *     moves can't be read
   */
  public GameModel toGameModel(@NotNull GameDto dto) {
    GameModel model = mapper.toModel(dto);
    // A time control is kept as ChessBase does: on the game as a whole, before the first move.
    // Without one, the movetext may still have one.
    if (dto.timeControl() != null) {
      GameTimeControl.set(model.moves(), dto.timeControl());
    }
    return model;
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

    GameHeaderModel headerModel = mapper.toHeader(dto);
    return buildTextModel(headerModel, dto);
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
