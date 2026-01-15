package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
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

  public static final String DATABASE_ID = "databaseId";
  public static final String WHITE_ID = "whiteId";
  public static final String BLACK_ID = "blackId";
  public static final String EVENT_ID = "eventId";
  public static final String ANNOTATOR_ID = "annotatorId";
  public static final String SOURCE_ID = "sourceId";
  public static final String WHITE_TEAM_ID = "whiteTeamId";
  public static final String BLACK_TEAM_ID = "blackTeamId";
  public static final String GAME_TAG_ID = "gameTagId";

  /**
   * Creates a mutable model of the game, header and moves
   *
   * @param game the game to get the model for
   * @return a mutable game model
   * @throws MorphyInvalidDataException if the model couldn't be created due to broken references If
   *     the move data is broken, a model is still returned with as many moves as could be decoded
   */
  public @NotNull GameModel getGameModel(@NotNull Game game) throws MorphyInvalidDataException {
    GameHeaderModel headerModel = getGameHeaderModel(game);

    GameMovesModel moves =
        game.database().moveRepository().getMoves(game.getMovesOffset(), game.id());
    game.database().annotationRepository().getAnnotations(moves, game.getAnnotationOffset());

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
   * Creates a mutable model of the game header
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

    // TODO: Instead of duplicating each CBH specific field, it's probably better to keep a
    // reference
    // to the raw header data. The GameHeaderModel should probably just contain the most generic
    // fields,
    // while ID's and internal CBH stuff like "last updated", "version" etc should not be there,
    // but an opaque blob/reference to the CBH/CBJ data should be kept instead.
    // For data that can be deduced from the game moves (like eco, final material, endgame etc),
    // perhaps
    // the version of the game can be used to deduce if it needs to be recalculated?

    model.setField(DATABASE_ID, game.database());

    model.setWhite(whitePlayer.getFullName());
    model.setField(WHITE_ID, whitePlayer.id());
    if (game.whiteElo() > 0) {
      model.setWhiteElo(game.whiteElo());
    }
    if (whiteTeam != null) {
      model.setField(WHITE_TEAM_ID, whiteTeam.id());
      model.setWhiteTeam(whiteTeam.title());
    }
    model.setBlack(blackPlayer.getFullName());
    model.setField(BLACK_ID, blackPlayer.id());
    if (game.blackElo() > 0) {
      model.setBlackElo(game.blackElo());
    }
    if (blackTeam != null) {
      model.setField(BLACK_TEAM_ID, blackTeam.id());
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
    model.setField(EVENT_ID, tournament.id());
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
    model.setField(SOURCE_ID, source.id());
    model.setAnnotator(annotator.name());
    model.setField(ANNOTATOR_ID, annotator.id());
    if (gameTag != null) {
      model.setField(GAME_TAG_ID, gameTag.id());
    }

    model.setLineEvaluation(game.lineEvaluation());

    return model;
  }

  public void setGameData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameModel model) {
    setHeaderGameData(gameHeader, extendedGameHeader, model.header());
    setMovesGameData(gameHeader, extendedGameHeader, model.moves());
  }

  public void setTextData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull TextModel model) {
    setHeaderTextData(gameHeader, extendedGameHeader, model.header());
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

    NAG eval = headerModel.getLineEvaluation();
    gameHeader.lineEvaluation(eval == null ? NAG.NONE : eval);

    extendedGameHeader
        .whiteRatingType(RatingType.international(TournamentTimeControl.NORMAL))
        .blackRatingType(RatingType.international(TournamentTimeControl.NORMAL))
        .creationTimestamp(0) // TODO
        .lastChangedTimestamp(0); // TODO
  }

  private void setMovesGameData(
      @NotNull ImmutableGameHeader.Builder gameHeader,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
      @NotNull GameMovesModel model) {
    // TODO: Add tests!!

    AnnotationStatistics stats = new AnnotationStatistics();

    collectStats(model.root(), stats);

    EnumSet<GameHeaderFlags> gameFlags = stats.getFlags();

    int moves = (model.countPly(false) + 1) / 2;

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

    extendedGameHeader
        .finalMaterial(false) // TODO
        .endgameInfo(EndgameInfo.empty()); // TODO
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

    extendedGameHeader
        .creationTimestamp(0) // TODO
        .lastChangedTimestamp(0) // TODO
        .build();
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
