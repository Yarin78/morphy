package se.yarin.morphy.cb2;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.cb2.annotations.AnnotationBlockCodec;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;
import se.yarin.morphy.cb2.games.AnalysisHeader;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.cb2.games.EcoField;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.moves.GuidingTextCodec;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.RecordFile;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.TournamentTimeControl;
import se.yarin.morphy.chessbase.TournamentType;
import se.yarin.morphy.chessbase.text.ImmutableTextHeaderModel;
import se.yarin.morphy.chessbase.text.ImmutableTextModel;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextLanguage;
import se.yarin.morphy.chessbase.text.TextModel;

/**
 * A game, guiding text or analysis, as seen through a transaction: its record, the entities it
 * refers to, and its moves or text, read when asked for.
 *
 * <p>An entity whose text is empty is the placeholder for a field left blank, and is reported as
 * not set.
 */
public final class Game {
  private static final Logger log = LoggerFactory.getLogger(Game.class);

  private final @NotNull DatabaseTransaction transaction;
  private final @NotNull GameRecord record;

  Game(@NotNull DatabaseTransaction transaction, @NotNull GameRecord record) {
    this.transaction = transaction;
    this.record = record;
  }

  public @NotNull DatabaseTransaction transaction() {
    return transaction;
  }

  public int id() {
    return record.id();
  }

  public @NotNull GameRecord record() {
    return record;
  }

  /** Whether this is a game. */
  public boolean isGame() {
    return record instanceof GameHeader;
  }

  /** Whether this is a guiding text. */
  public boolean isText() {
    return record instanceof TextHeader;
  }

  /** Whether this is an analysis. */
  public boolean isAnalysis() {
    return record instanceof AnalysisHeader;
  }

  public boolean deleted() {
    return record.deleted();
  }

  /**
   * The record of a game.
   *
   * @throws IllegalStateException if this is not a game
   */
  public @NotNull GameHeader header() {
    if (record instanceof GameHeader g) {
      return g;
    }
    throw new IllegalStateException("Record " + id() + " is not a game");
  }

  // ── Entities ────────────────────────────────────────────────────────────

  private @Nullable Player player(long id) {
    return transaction.entity(EntityType.PLAYER, id, Player.class);
  }

  public @Nullable Player white() {
    return record instanceof GameHeader g ? player(g.whiteId()) : null;
  }

  public @Nullable Player black() {
    return record instanceof GameHeader g ? player(g.blackId()) : null;
  }

  /** The annotator of a game, or the author of a text or analysis. */
  public @Nullable Player annotator() {
    return player(annotatorId());
  }

  public long annotatorId() {
    return switch (record) {
      case GameHeader g -> g.annotatorId();
      case TextHeader t -> t.annotatorId();
      case AnalysisHeader a -> a.annotatorId();
    };
  }

  public long tournamentId() {
    return switch (record) {
      case GameHeader g -> g.tournamentId();
      case TextHeader t -> t.tournamentId();
      case AnalysisHeader a -> -1;
    };
  }

  public @Nullable Tournament tournament() {
    return transaction.entity(EntityType.TOURNAMENT, tournamentId(), Tournament.class);
  }

  public long sourceId() {
    return switch (record) {
      case GameHeader g -> g.sourceId();
      case TextHeader t -> t.sourceId();
      case AnalysisHeader a -> a.sourceId();
    };
  }

  public @Nullable Source source() {
    return transaction.entity(EntityType.SOURCE, sourceId(), Source.class);
  }

  public @Nullable Team whiteTeam() {
    return record instanceof GameHeader g
        ? transaction.entity(EntityType.TEAM, g.whiteTeamId(), Team.class)
        : null;
  }

  public @Nullable Team blackTeam() {
    return record instanceof GameHeader g
        ? transaction.entity(EntityType.TEAM, g.blackTeamId(), Team.class)
        : null;
  }

  /** The game tag of a game, or the title of a text or analysis. */
  public long gameTagId() {
    return switch (record) {
      case GameHeader g -> g.gameTagId();
      case TextHeader t -> t.titleId();
      case AnalysisHeader a -> a.titleId();
    };
  }

  public @Nullable GameTag gameTag() {
    return transaction.entity(EntityType.GAME_TAG, gameTagId(), GameTag.class);
  }

  // ── Moves and text ──────────────────────────────────────────────────────

  /**
   * The moves of a game or analysis, with their annotations. Each call returns a new model. If the
   * annotations can't be read, the moves come without them.
   *
   * @throws IllegalStateException if this is a guiding text
   * @throws InvalidDataException if the moves can't be decoded
   */
  public @NotNull GameMovesModel moves() {
    if (isText()) {
      throw new IllegalStateException("Record " + id() + " is a guiding text");
    }
    RecordFile.Record data = transaction.movesData(record);
    GameMovesModel moves;
    try {
      moves = MoveStreamCodec.decode(data.tag(), data.content());
    } catch (InvalidDataException e) {
      throw new InvalidDataException("Game " + id() + ": " + e.getMessage(), e);
    }
    byte[] annotations = transaction.annotationData(record);
    if (annotations != null && !AnnotationBlockCodec.decode(annotations, moves)) {
      log.warn("The annotations of game {} can't be read and are left out", id());
    }
    return moves;
  }

  /** Whether the annotations can be read; if not, {@link #moves()} leaves them out. */
  public boolean annotationsReadable() {
    byte[] annotations = transaction.annotationData(record);
    return annotations == null || AnnotationBlockCodec.read(annotations) != null;
  }

  /**
   * The body of a guiding text, with its titles from its title entity.
   *
   * @throws IllegalStateException if this is not a guiding text
   */
  public @NotNull TextContentsModel textContents() {
    if (!isText()) {
      throw new IllegalStateException("Record " + id() + " is not a guiding text");
    }
    RecordFile.Record data = transaction.movesData(record);
    TextContentsModel contents = GuidingTextCodec.decode(data.content());
    Map<TextLanguage, String> titles = new HashMap<>();
    GameTag title = gameTag();
    if (title != null) {
      for (GameTag.Title t : title.titles()) {
        TextLanguage language = GuidingTextCodec.language(t.language());
        if (language != null && !t.text().isEmpty()) {
          titles.put(language, t.text());
        }
      }
    }
    return new TextContentsModel(
        contents.format(), titles, contents.contents(), contents.formatting(), contents.unknown());
  }

  // ── Models ──────────────────────────────────────────────────────────────

  /** The game as a model: its header and its moves. */
  public @NotNull GameModel model() {
    return new GameModel(headerModel(), moves());
  }

  /** A guiding text as a model. */
  public @NotNull TextModel textModel() {
    Tournament tournament = tournament();
    Player author = annotator();
    Source source = source();
    return ImmutableTextModel.builder()
        .header(
            ImmutableTextHeaderModel.builder()
                .tournament(tournament == null ? "" : tournament.title())
                .tournamentDate(tournament == null ? Date.unset() : Dates.decode(tournament.startDate()))
                .annotator(author == null ? "" : author.fullName())
                .source(source == null ? "" : source.title())
                .build())
        .contents(textContents())
        .build();
  }

  /**
   * The header of a game or analysis as a model, bound to the entities by id. Placeholder
   * entities leave their fields unset.
   */
  public @NotNull GameHeaderModel headerModel() {
    GameHeaderModel model = new GameHeaderModel();
    if (record instanceof GameHeader g) {
      Player white = white(), black = black();
      model.setWhiteId(g.whiteId());
      if (white != null && !white.isEmpty()) {
        model.setWhite(white.fullName());
      }
      model.setBlackId(g.blackId());
      if (black != null && !black.isEmpty()) {
        model.setBlack(black.fullName());
      }
      if (g.whiteElo() > 0) {
        model.setWhiteElo(g.whiteElo());
      }
      if (g.blackElo() > 0) {
        model.setBlackElo(g.blackElo());
      }
      Team whiteTeam = whiteTeam(), blackTeam = blackTeam();
      if (whiteTeam != null) {
        model.setWhiteTeamId(g.whiteTeamId());
        model.setWhiteTeam(whiteTeam.title());
      }
      if (blackTeam != null) {
        model.setBlackTeamId(g.blackTeamId());
        model.setBlackTeam(blackTeam.title());
      }
      model.setResult(result(g.result()));
      if (g.result() == GameResult.NOT_FINISHED.ordinal() && g.lineEvaluation() > 0) {
        model.setLineEvaluation(nag(g.lineEvaluation()));
      }
      model.setDate(Dates.decode(g.playedDate()));
      if (!g.chess960()) {
        model.setEco(EcoField.eco(g.eco()));
      }
      if (g.round() > 0) {
        model.setRound(g.round());
      }
      if (g.subRound() > 0) {
        model.setSubRound(g.subRound());
      }
      if (g.board() > 0) {
        model.setBoard(g.board());
      }
      setTournament(model, g.tournamentId());
    }
    Source source = source();
    model.setSourceId(sourceId());
    if (source != null && !source.isEmpty()) {
      model.setSourceTitle(source.title());
      model.setSource(source.publisher());
      model.setSourceDate(Dates.decode(source.publicationDate()));
    }
    Player annotator = annotator();
    model.setAnnotatorId(annotatorId());
    if (annotator != null && !annotator.isEmpty()) {
      model.setAnnotator(annotator.fullName());
    }
    GameTag tag = gameTag();
    if (tag != null) {
      model.setGameTagId(gameTagId());
      if (!tag.isEmpty()) {
        model.setGameTag(tag.title());
      }
    }
    return model;
  }

  private void setTournament(GameHeaderModel model, long tournamentId) {
    model.setEventId(tournamentId);
    Tournament t = tournament();
    if (t == null || t.isEmpty()) {
      return;
    }
    model.setEvent(t.title());
    model.setEventDate(Dates.decode(t.startDate()));
    if (t.endDate() != 0) {
      model.setEventEndDate(Dates.decode(t.endDate()));
    }
    model.setEventSite(t.place());
    if (t.nation() > 0 && t.nation() < Nation.values().length) {
      model.setEventCountry(Nation.values()[t.nation()].getIocCode());
    }
    int type = t.tournamentType();
    if (type > 0 && type < TournamentType.values().length) {
      model.setEventType(TournamentType.values()[type].getName());
    }
    TournamentTimeControl timeControl =
        t.correspondence()
            ? TournamentTimeControl.CORRESPONDENCE
            : t.rapid()
                ? TournamentTimeControl.RAPID
                : t.blitz() ? TournamentTimeControl.BLITZ : TournamentTimeControl.NORMAL;
    if (timeControl != TournamentTimeControl.NORMAL) {
      model.setEventTimeControl(timeControl.getName());
    }
    if (t.category() > 0) {
      model.setEventCategory(t.category());
    }
    if (t.rounds() > 0) {
      model.setEventRounds(t.rounds());
    }
  }

  private static GameResult result(int value) {
    GameResult[] results = GameResult.values();
    return value >= 0 && value < results.length ? results[value] : GameResult.NOT_FINISHED;
  }

  private static NAG nag(int value) {
    NAG[] nags = NAG.values();
    return value >= 0 && value < nags.length ? nags[value] : NAG.NONE;
  }

  @Override
  public String toString() {
    return "Game " + id() + " (" + record.getClass().getSimpleName() + ")";
  }
}
