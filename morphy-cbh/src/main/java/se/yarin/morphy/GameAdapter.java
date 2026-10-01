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
import se.yarin.morphy.chessbase.annotations.AnnotationStatistics;
import se.yarin.morphy.chessbase.annotations.StatisticalAnnotation;
import se.yarin.morphy.text.*;

import java.util.EnumSet;
import java.util.Map;
import se.yarin.morphy.chessbase.*;
import se.yarin.morphy.chessbase.text.*;
import se.yarin.morphy.chessbase.TournamentType;
import se.yarin.morphy.chessbase.TournamentTimeControl;

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
      model.setWhiteEloType(game.whiteRatingType().toEloType());
    }
    if (whiteTeam != null) {
      model.setWhiteTeamId((long) whiteTeam.id());
      model.setWhiteTeam(whiteTeam.title());
      if (whiteTeam.teamNumber() > 0) {
        model.setWhiteTeamNumber(whiteTeam.teamNumber());
      }
      if (whiteTeam.season()) {
        model.setWhiteTeamSeason(true);
      }
      if (whiteTeam.year() > 0) {
        model.setWhiteTeamYear(whiteTeam.year());
      }
      if (whiteTeam.nation() != Nation.NONE) {
        model.setWhiteTeamNation(whiteTeam.nation().getIocCode());
      }
    }
    model.setBlack(blackPlayer.getFullName());
    model.setBlackId((long) blackPlayer.id());
    if (game.blackElo() > 0) {
      model.setBlackElo(game.blackElo());
      model.setBlackEloType(game.blackRatingType().toEloType());
    }
    if (blackTeam != null) {
      model.setBlackTeamId((long) blackTeam.id());
      model.setBlackTeam(blackTeam.title());
      if (blackTeam.teamNumber() > 0) {
        model.setBlackTeamNumber(blackTeam.teamNumber());
      }
      if (blackTeam.season()) {
        model.setBlackTeamSeason(true);
      }
      if (blackTeam.year() > 0) {
        model.setBlackTeamYear(blackTeam.year());
      }
      if (blackTeam.nation() != Nation.NONE) {
        model.setBlackTeamNation(blackTeam.nation().getIocCode());
      }
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
    if (tournament.complete()) {
      model.setEventComplete(true);
    }
    if (tournament.teamTournament()) {
      model.setEventTeamTournament(true);
    }

    model.setSourceTitle(source.title());
    model.setSource(source.publisher());
    model.setSourceDate(source.date());
    model.setSourcePublication(source.publication());
    if (source.version() > 0) {
      model.setSourceVersion(source.version());
    }
    if (source.quality() != SourceQuality.UNSET) {
      model.setSourceQuality(source.quality().name());
    }
    model.setSourceId((long) source.id());
    model.setAnnotator(annotator.name());
    model.setAnnotatorId((long) annotator.id());
    if (gameTag != null) {
      model.setGameTagId((long) gameTag.id());
      model.setGameTag(gameTag.title());
      for (GameTagLanguage language : GameTagLanguage.values()) {
        String title = gameTagTitle(gameTag, language);
        if (title != null && !title.isEmpty()) {
          model.setGameTagTitle(language, title);
        }
      }
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
        .whiteRatingType(ratingType(headerModel.getWhiteEloType()))
        .blackRatingType(ratingType(headerModel.getBlackEloType()));
  }

  /** An elo type as v1 stores it; FIDE for none, and for one v1 can't store. */
  private static @NotNull RatingType ratingType(@Nullable EloType type) {
    RatingType ratingType = type == null ? null : RatingType.of(type);
    return ratingType == null ? RatingType.unspecified() : ratingType;
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
      // Stored in place of the ECO; without it, the game isn't known as Chess960 from its header
      gameHeader.chess960StartPosition(model.root().position().chess960StartPosition());
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

  private static @NotNull SourceQuality sourceQuality(@NotNull String name) {
    try {
      return SourceQuality.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SourceQuality.UNSET;
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
    if (headerModel.getSourcePublication() != null) {
      builder.publication(headerModel.getSourcePublication());
    }
    if (headerModel.getSourceVersion() != null) {
      builder.version(headerModel.getSourceVersion());
    }
    if (headerModel.getSourceQuality() != null) {
      builder.quality(sourceQuality(headerModel.getSourceQuality()));
    }
    return builder.build();
  }

  /**
   * A game tag with the titles the header has by language, or with its title in English if it has
   * none. There's no room for a Portuguese title.
   */
  public @NotNull GameTag toGameTag(@NotNull GameHeaderModel headerModel) {
    Map<GameTagLanguage, String> titles = headerModel.getGameTagTitles();
    if (titles.isEmpty()) {
      return GameTag.of(headerModel.getGameTag() == null ? "" : headerModel.getGameTag());
    }
    ImmutableGameTag.Builder builder = ImmutableGameTag.builder();
    titles.forEach(
        (language, title) -> {
          switch (language) {
            case ENGLISH -> builder.englishTitle(title);
            case GERMAN -> builder.germanTitle(title);
            case FRENCH -> builder.frenchTitle(title);
            case SPANISH -> builder.spanishTitle(title);
            case ITALIAN -> builder.italianTitle(title);
            case DUTCH -> builder.dutchTitle(title);
            case SLOVENIAN -> builder.slovenianTitle(title);
            case PORTUGUESE -> {}
          }
        });
    return builder.build();
  }

  /** A game tag's title in a language; null for one a v1 game tag can't have. */
  private static @Nullable String gameTagTitle(@NotNull GameTag gameTag, @NotNull GameTagLanguage language) {
    return switch (language) {
      case ENGLISH -> gameTag.englishTitle();
      case GERMAN -> gameTag.germanTitle();
      case FRENCH -> gameTag.frenchTitle();
      case SPANISH -> gameTag.spanishTitle();
      case ITALIAN -> gameTag.italianTitle();
      case DUTCH -> gameTag.dutchTitle();
      case SLOVENIAN -> gameTag.slovenianTitle();
      case PORTUGUESE -> null;
    };
  }

  /** A team with the details the header has for white's or black's. */
  public @NotNull Team toTeam(@NotNull GameHeaderModel headerModel, boolean white) {
    String title = white ? headerModel.getWhiteTeam() : headerModel.getBlackTeam();
    Integer number = white ? headerModel.getWhiteTeamNumber() : headerModel.getBlackTeamNumber();
    Boolean season = white ? headerModel.getWhiteTeamSeason() : headerModel.getBlackTeamSeason();
    Integer year = white ? headerModel.getWhiteTeamYear() : headerModel.getBlackTeamYear();
    String nation = white ? headerModel.getWhiteTeamNation() : headerModel.getBlackTeamNation();
    ImmutableTeam.Builder builder = ImmutableTeam.builder();
    if (title != null) {
      builder.title(title);
    }
    if (number != null) {
      builder.teamNumber(number);
    }
    if (season != null) {
      builder.season(season);
    }
    if (year != null) {
      builder.year(year);
    }
    if (nation != null) {
      builder.nation(Nation.fromIOC(nation));
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
    if (Boolean.TRUE.equals(headerModel.getEventComplete())) {
      // ChessBase sets both complete bits; see TournamentIndex
      builder.complete(true);
      builder.legacyComplete(true);
    }
    if (Boolean.TRUE.equals(headerModel.getEventTeamTournament())) {
      builder.teamTournament(true);
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
