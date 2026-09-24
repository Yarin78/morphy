package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.games.annotations.AnnotationStatistics;
import se.yarin.morphy.games.annotations.StatisticalAnnotation;
import se.yarin.morphy.text.*;

import java.util.EnumSet;

/**
 * Contains the logic for mapping a {@link Game} to a {@link se.yarin.chess.GameModel} and vice
 * versa.
 */
public class GameAdapter {

  private static final Logger log = LoggerFactory.getLogger(GameAdapter.class);

  // ==========================================================
  // Functions for converting a Game into a GameModel/TextModel
  // ==========================================================

  /**
   * Creates a mutable model of the game, header and moves
   *
   * @param game the game to get the model for
   * @return a mutable game model
   * @throws MorphyInvalidDataException if the model couldn't be created due to broken references. If
   *     the move data is broken, a model is still returned with as many moves as could be decoded.
   */
  public @NotNull GameModel getGameModel(@NotNull Game game) throws MorphyInvalidDataException {
    GameHeaderModel headerModel = getGameHeaderModel(game);

    DatabaseCbh database = game.database();
    GameMovesModel moves = database.moveRepository().getMoves(game.getMovesOffset(), game.id());
    database.annotationRepository().getAnnotations(moves, game.getAnnotationOffset());

    return new GameModel(headerModel, moves);
  }

  public @NotNull TextHeaderModel getTextHeaderModel(@NotNull Game game) {
    GameHeader header = game.header();
    if (!header.guidingText()) {
      throw new IllegalArgumentException(
          "Can't get text header model for a game (id " + header.id() + ")");
    }

    Annotator annotator = game.annotator();
    Source source = game.source();
    Tournament tournament = game.tournament();

    return ImmutableTextHeaderModel.builder()
        .round(header.round())
        .subRound(header.round())
        .tournament(tournament.title())
        .tournamentDate(tournament.date())
        .source(source.title())
        .annotator(annotator.name())
        .build();
  }

  public @NotNull TextModel getTextModel(@NotNull Game game) throws MorphyException {
    TextHeaderModel header = getTextHeaderModel(game);
    TextContentsModel contents =
        game.database().moveRepository().getText(game.getMovesOffset(), game.id());
    return ImmutableTextModel.builder().header(header).contents(contents).build();
  }

  /**
   * Creates a mutable model of the game header.
   *
   * @param game the game to get the header model for
   * @return a mutable game header model
   * @throws MorphyInvalidDataException if an entity couldn't be resolved
   */
  public GameHeaderModel getGameHeaderModel(@NotNull Game game) throws MorphyInvalidDataException {
    GameHeaderModel model = new GameHeaderModel();

    if (game.guidingText()) {
      throw new IllegalArgumentException(
          "Can't get game header model for a guiding text (id " + game.id() + ")");
    }

    Player whitePlayer = game.white();
    Player blackPlayer = game.black();
    Annotator annotator = game.annotator();
    Source source = game.source();
    Tournament tournament = game.tournament();
    Team whiteTeam = game.whiteTeam();
    Team blackTeam = game.blackTeam();
    GameTag gameTag = game.gameTag();

    model.setWhite(whitePlayer.getFullName());
    model.setWhiteId((long) whitePlayer.id());
    if (game.whiteElo() > 0) {
      model.setWhiteElo(game.whiteElo());
    }
    if (whiteTeam != null) {
      model.setWhiteTeamId((long) whiteTeam.id());
      model.setWhiteTeam(whiteTeam.title());
    }
    model.setBlack(blackPlayer.getFullName());
    model.setBlackId((long) blackPlayer.id());
    if (game.blackElo() > 0) {
      model.setBlackElo(game.blackElo());
    }
    if (blackTeam != null) {
      model.setBlackTeamId((long) blackTeam.id());
      model.setBlackTeam(blackTeam.title());
    }
    model.setResult(game.result());
    model.setDate(game.playedDate());
    model.setEco(game.eco());
    if (game.round() > 0) {
      model.setRound(game.round());
    }
    if (game.subRound() > 0) {
      model.setSubRound(game.subRound());
    }

    model.setEvent(tournament.title());
    model.setEventId((long) tournament.id());
    model.setEventDate(tournament.date());
    model.setEventSite(tournament.place());
    if (tournament.nation() != Nation.NONE) {
      model.setEventCountry(tournament.nation().getIocCode());
    }
    if (tournament.type() != TournamentType.NONE) {
      model.setEventType(tournament.type().getName());
    }
    if (tournament.timeControl() != TournamentTimeControl.NORMAL) {
      model.setEventTimeControl(tournament.timeControl().getName());
    }
    if (tournament.category() > 0) {
      model.setEventCategory(tournament.category());
    }
    if (tournament.rounds() > 0) {
      model.setEventRounds(tournament.rounds());
    }

    model.setSourceTitle(source.title());
    model.setSource(source.publisher());
    model.setSourceDate(source.publication());
    model.setSourceId((long) source.id());
    model.setAnnotator(annotator.name());
    model.setAnnotatorId((long) annotator.id());
    if (gameTag != null) {
      model.setGameTagId((long) gameTag.id());
      model.setGameTag(gameTag.englishTitle());
    }

    model.setLineEvaluation(game.lineEvaluation());

    return model;
  }

  // ==========================================================
  // Functions for converting a GameModel/TextModel into a Game
  // (including any dependent entities)
  // ==========================================================

  public void setGameData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameModel model) {
    setHeaderGameData(gameHeader, extendedGameHeader, model.header());
    setEntityIds(gameHeader, extendedGameHeader, model.header());
    setMovesGameData(gameHeader, extendedGameHeader, model.moves());
  }

  public void setTextData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull TextModel model) {
    // Text entries don't have players, and entity IDs default to -1 for resolution
    gameHeader.whitePlayerId(-1);
    gameHeader.blackPlayerId(-1);
    gameHeader.tournamentId(-1);
    gameHeader.annotatorId(-1);
    gameHeader.sourceId(-1);

    setHeaderTextData(gameHeader, extendedGameHeader, model.header());
  }

  /** The v1 id of an entity the header is bound to, or -1 if the entity must be resolved by name. */
  private static int v1Id(@Nullable Long id) {
    return id == null ? -1 : Math.toIntExact(id);
  }

  public void setEntityIds(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameHeaderModel headerModel) {
    // An entity id in the header binds the game to that existing entity; -1 means it is resolved
    // (found or created) from the name when the game is written
    gameHeader.whitePlayerId(v1Id(headerModel.getWhiteId()));
    gameHeader.blackPlayerId(v1Id(headerModel.getBlackId()));
    gameHeader.tournamentId(v1Id(headerModel.getEventId()));
    gameHeader.annotatorId(v1Id(headerModel.getAnnotatorId()));
    gameHeader.sourceId(v1Id(headerModel.getSourceId()));
    extendedGameHeader.whiteTeamId(v1Id(headerModel.getWhiteTeamId()));
    extendedGameHeader.blackTeamId(v1Id(headerModel.getBlackTeamId()));
    extendedGameHeader.gameTagId(v1Id(headerModel.getGameTagId()));
  }

  public void setHeaderGameData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameHeaderModel headerModel) {
    gameHeader.playedDate(headerModel.getDate() == null ? Date.today() : headerModel.getDate());
    gameHeader.result(
        headerModel.getResult() == null ? GameResult.NOT_FINISHED : headerModel.getResult());
    if (headerModel.getRound() != null) {
      gameHeader.round(headerModel.getRound());
    }
    if (headerModel.getSubRound() != null) {
      gameHeader.subRound(headerModel.getSubRound());
    }
    if (headerModel.getWhiteElo() != null) {
      gameHeader.whiteElo(headerModel.getWhiteElo());
    }
    if (headerModel.getBlackElo() != null) {
      gameHeader.blackElo(headerModel.getBlackElo());
    }
    gameHeader.eco(headerModel.getEco() == null ? Eco.unset() : headerModel.getEco());

    gameHeader.lineEvaluation(headerModel.getLineEvaluation());

    extendedGameHeader
        .whiteRatingType(RatingType.international(TournamentTimeControl.NORMAL))
        .blackRatingType(RatingType.international(TournamentTimeControl.NORMAL));
  }

  private void setMovesGameData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameMovesModel model) {
    AnnotationStatistics stats = new AnnotationStatistics();
    collectStats(model.root(), stats);

    EnumSet<GameHeaderFlags> gameFlags = stats.getFlags();

    // The number of moves in the main line. A game that starts with a move by black, which is
    // possible when the game starts from a set-up position, counts that move as a move. A game
    // without moves has 0 moves.
    int plies = model.countPly(false);
    boolean blackFirst = !Chess.isWhitePly(model.root().ply());
    int moves = plies == 0 ? 0 : (plies + (blackFirst ? 1 : 0) + 1) / 2;

    int v = model.countPly(true) - model.countPly(false);
    if (v > 0) {
      gameFlags.add(GameHeaderFlags.VARIATIONS);
      gameHeader.variationsMagnitude(v > 1000 ? 4 : v > 300 ? 3 : v > 50 ? 2 : 1);
    }
    if (model.isSetupPosition()) {
      gameFlags.add(GameHeaderFlags.SETUP_POSITION);
    }
    if (!model.root().position().isRegularChess()) {
      gameFlags.add(GameHeaderFlags.UNORTHODOX);
    }

    // TODO: Stream flag (if it should be kept here!?)
    gameHeader
        .noMoves(moves > 255 ? -1 : moves)
        .medals(stats.getMedals())
        .flags(gameFlags)
        .commentariesMagnitude(stats.getCommentariesMagnitude())
        .symbolsMagnitude(stats.getSymbolsMagnitude())
        .graphicalSquaresMagnitude(stats.getGraphicalSquaresMagnitude())
        .graphicalArrowsMagnitude(stats.getGraphicalArrowsMagnitude())
        .trainingMagnitude(stats.getTrainingMagnitude())
        .timeSpentMagnitude(stats.getTimeSpentMagnitude());

    FinalMaterial.Result finalMaterial = FinalMaterial.calculate(model);
    FinalMaterial total = FinalMaterial.sum(finalMaterial.player1(), finalMaterial.player2());
    extendedGameHeader
        .finalMaterial(!total.isEmpty())
        .materialPlayer1(finalMaterial.player1())
        .materialPlayer2(finalMaterial.player2())
        .materialTotal(total)
        .endgameInfo(EndgameInfo.empty()); // This is seemingly not used any longer
  }

  public void setHeaderTextData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull TextHeaderModel headerModel) {

    gameHeader.guidingText(true);
    if (headerModel.round() != 0) {
      gameHeader.round(headerModel.round());
    }
    if (headerModel.subRound() != 0) {
      gameHeader.subRound(headerModel.subRound());
    }
  }

  private void collectStats(
      @NotNull GameMovesModel.Node node, @NotNull AnnotationStatistics stats) {
    for (Annotation annotation : node.getAnnotations()) {
      if (annotation instanceof StatisticalAnnotation sa) {
        sa.updateStatistics(stats);
      }
    }
    for (GameMovesModel.Node child : node.children()) {
      collectStats(child, stats);
    }
  }

  public @NotNull Source toSource(@NotNull GameHeaderModel headerModel) {
    ImmutableSource.Builder builder = ImmutableSource.builder();
    if (headerModel.getSourceTitle() != null) {
      builder.title(headerModel.getSourceTitle());
    }
    if (headerModel.getSource() != null) {
      builder.publisher(headerModel.getSource());
    }
    if (headerModel.getSourceDate() != null) {
      builder.date(headerModel.getSourceDate());
    }
    return builder.build();
  }

  public @NotNull Tournament toTournament(@NotNull GameHeaderModel headerModel) {
    ImmutableTournament.Builder builder = ImmutableTournament.builder();
    if (headerModel.getEvent() != null) {
      builder.title(headerModel.getEvent());
    }
    if (headerModel.getEventDate() != null) {
      builder.date(headerModel.getEventDate());
    }
    if (headerModel.getEventSite() != null) {
      builder.place(headerModel.getEventSite());
    }
    if (headerModel.getEventCountry() != null) {
      builder.nation(Nation.fromIOC(headerModel.getEventCountry()));
    }
    if (headerModel.getEventType() != null) {
      builder.type(TournamentType.fromName(headerModel.getEventType()));
    }
    if (headerModel.getEventTimeControl() != null) {
      builder.timeControl(TournamentTimeControl.fromName(headerModel.getEventTimeControl()));
    }
    if (headerModel.getEventCategory() != null) {
      builder.category(headerModel.getEventCategory());
    }
    if (headerModel.getEventRounds() != null) {
      builder.rounds(headerModel.getEventRounds());
    }
    return builder.build();
  }

  public @NotNull TournamentExtra toTournamentExtra(@NotNull GameHeaderModel headerModel) {
    ImmutableTournamentExtra.Builder builder = ImmutableTournamentExtra.builder();
    if (headerModel.getEventEndDate() != null) {
      builder.endDate(headerModel.getEventEndDate());
    }
    return builder.build();
  }
}
