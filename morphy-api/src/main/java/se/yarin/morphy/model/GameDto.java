package se.yarin.morphy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.EloType;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;

/**
 * Data Transfer Object for a chess game.
 *
 * <p>This DTO contains all game information including metadata, player details, tournament
 * information, and optionally the game moves and text commentary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "type",
  "textTitle",
  "whitePlayer",
  "whiteElo",
  "whiteEloType",
  "blackPlayer",
  "blackElo",
  "blackEloType",
  "whiteTeam",
  "blackTeam",
  "result",
  "date",
  "eco",
  "round",
  "subRound",
  "board",
  "lineEvaluation",
  "timeControl",
  "tournament",
  "source",
  "annotator",
  "gameTag",
  "medals",
  "deleted",
  "topGame",
  "setupPosition",
  "variant",
  "noMoves",
  "notation",
  "variationMoves",
  "ait",
  "vcs",
  "finalMaterial",
  "gameVersion",
  "creationTimestamp",
  "lastChanged",
  "moves",
  "text",
  "extraTags"
})
public record GameDto(
    // Game identity
    Long id,
    String type, // "game" for regular chess game, "text" for guiding text
    @Nullable String textTitle, // title for guiding texts

    // Player information
    @Nullable PlayerDto whitePlayer,
    @Nullable Integer whiteElo,
    @Nullable EloType whiteEloType, // null when there's no elo
    @Nullable PlayerDto blackPlayer,
    @Nullable Integer blackElo,
    @Nullable EloType blackEloType,

    // Team information
    @Nullable TeamDto whiteTeam,
    @Nullable TeamDto blackTeam,

    // Game metadata (result and date are mandatory PGN fields)
    GameResult result, // Mandatory PGN field
    Date date, // Mandatory PGN field (Date class handles unset dates as ????.??.??)
    @Nullable String eco,
    @Nullable Integer round,
    @Nullable Integer subRound,
    @Nullable Integer board, // the board in a team match; not stored by every format
    @Nullable NAG lineEvaluation,
    @Nullable TimeControlDto timeControl, // the time control, a ChessBase annotation of the game

    // Tournament information
    @Nullable TournamentDto tournament,

    // Source information
    @Nullable SourceDto source,

    // Annotator
    @Nullable AnnotatorDto annotator,

    // Game tag
    @Nullable GameTagDto gameTag,

    // Medals
    @Nullable List<String> medals,

    // Deleted
    @Nullable Boolean deleted,

    // Top game
    @Nullable Boolean topGame,

    // Flags
    @Nullable Boolean setupPosition,
    @Nullable String variant, // the PGN Variant, "Chess960"; null for regular chess

    // Additional game metadata
    @Nullable Integer noMoves,
    @Nullable String notation,
    @Nullable Integer variationMoves,
    @Nullable String ait,
    @Nullable String vcs,
    @Nullable String finalMaterial,
    @Nullable Integer gameVersion,
    @Nullable Long creationTimestamp,
    @Nullable String lastChanged,

    // Game content (nullable for header-only queries)
    @Nullable GameMovesDto moves,
    @Nullable GameTextDto text,

    // Tags of a PGN game that have no field of their own, in file order. Set by PGN databases
    // only; null elsewhere.
    @Nullable Map<String, String> extraTags) {

  /** A builder for a new game, with no field set. */
  public static @NotNull Builder builder() {
    return new Builder();
  }

  /** A builder starting from this game, to make a copy with some fields changed. */
  public @NotNull Builder toBuilder() {
    return new Builder(this);
  }

  /** Builds a {@link GameDto} field by field, rather than with its many constructor arguments. */
  public static final class Builder {
    private Long id;
    private String type;
    private String textTitle;
    private PlayerDto whitePlayer;
    private Integer whiteElo;
    private EloType whiteEloType;
    private PlayerDto blackPlayer;
    private Integer blackElo;
    private EloType blackEloType;
    private TeamDto whiteTeam;
    private TeamDto blackTeam;
    private GameResult result;
    private Date date;
    private String eco;
    private Integer round;
    private Integer subRound;
    private Integer board;
    private NAG lineEvaluation;
    private TimeControlDto timeControl;
    private TournamentDto tournament;
    private SourceDto source;
    private AnnotatorDto annotator;
    private GameTagDto gameTag;
    private List<String> medals;
    private Boolean deleted;
    private Boolean topGame;
    private Boolean setupPosition;
    private String variant;
    private Integer noMoves;
    private String notation;
    private Integer variationMoves;
    private String ait;
    private String vcs;
    private String finalMaterial;
    private Integer gameVersion;
    private Long creationTimestamp;
    private String lastChanged;
    private GameMovesDto moves;
    private GameTextDto text;
    private Map<String, String> extraTags;

    private Builder() {}

    private Builder(@NotNull GameDto game) {
      id = game.id;
      type = game.type;
      textTitle = game.textTitle;
      whitePlayer = game.whitePlayer;
      whiteElo = game.whiteElo;
      whiteEloType = game.whiteEloType;
      blackPlayer = game.blackPlayer;
      blackElo = game.blackElo;
      blackEloType = game.blackEloType;
      whiteTeam = game.whiteTeam;
      blackTeam = game.blackTeam;
      result = game.result;
      date = game.date;
      eco = game.eco;
      round = game.round;
      subRound = game.subRound;
      board = game.board;
      lineEvaluation = game.lineEvaluation;
      timeControl = game.timeControl;
      tournament = game.tournament;
      source = game.source;
      annotator = game.annotator;
      gameTag = game.gameTag;
      medals = game.medals;
      deleted = game.deleted;
      topGame = game.topGame;
      setupPosition = game.setupPosition;
      variant = game.variant;
      noMoves = game.noMoves;
      notation = game.notation;
      variationMoves = game.variationMoves;
      ait = game.ait;
      vcs = game.vcs;
      finalMaterial = game.finalMaterial;
      gameVersion = game.gameVersion;
      creationTimestamp = game.creationTimestamp;
      lastChanged = game.lastChanged;
      moves = game.moves;
      text = game.text;
      extraTags = game.extraTags;
    }

    public @NotNull Builder id(Long id) {
      this.id = id;
      return this;
    }

    public @NotNull Builder type(String type) {
      this.type = type;
      return this;
    }

    public @NotNull Builder textTitle(String textTitle) {
      this.textTitle = textTitle;
      return this;
    }

    public @NotNull Builder whitePlayer(PlayerDto whitePlayer) {
      this.whitePlayer = whitePlayer;
      return this;
    }

    public @NotNull Builder whiteElo(Integer whiteElo) {
      this.whiteElo = whiteElo;
      return this;
    }

    public @NotNull Builder whiteEloType(EloType whiteEloType) {
      this.whiteEloType = whiteEloType;
      return this;
    }

    public @NotNull Builder blackPlayer(PlayerDto blackPlayer) {
      this.blackPlayer = blackPlayer;
      return this;
    }

    public @NotNull Builder blackElo(Integer blackElo) {
      this.blackElo = blackElo;
      return this;
    }

    public @NotNull Builder blackEloType(EloType blackEloType) {
      this.blackEloType = blackEloType;
      return this;
    }

    public @NotNull Builder whiteTeam(TeamDto whiteTeam) {
      this.whiteTeam = whiteTeam;
      return this;
    }

    public @NotNull Builder blackTeam(TeamDto blackTeam) {
      this.blackTeam = blackTeam;
      return this;
    }

    public @NotNull Builder result(GameResult result) {
      this.result = result;
      return this;
    }

    public @NotNull Builder date(Date date) {
      this.date = date;
      return this;
    }

    public @NotNull Builder eco(String eco) {
      this.eco = eco;
      return this;
    }

    public @NotNull Builder round(Integer round) {
      this.round = round;
      return this;
    }

    public @NotNull Builder subRound(Integer subRound) {
      this.subRound = subRound;
      return this;
    }

    public @NotNull Builder board(Integer board) {
      this.board = board;
      return this;
    }

    public @NotNull Builder lineEvaluation(NAG lineEvaluation) {
      this.lineEvaluation = lineEvaluation;
      return this;
    }

    public @NotNull Builder timeControl(TimeControlDto timeControl) {
      this.timeControl = timeControl;
      return this;
    }

    public @NotNull Builder tournament(TournamentDto tournament) {
      this.tournament = tournament;
      return this;
    }

    public @NotNull Builder source(SourceDto source) {
      this.source = source;
      return this;
    }

    public @NotNull Builder annotator(AnnotatorDto annotator) {
      this.annotator = annotator;
      return this;
    }

    public @NotNull Builder gameTag(GameTagDto gameTag) {
      this.gameTag = gameTag;
      return this;
    }

    public @NotNull Builder medals(List<String> medals) {
      this.medals = medals;
      return this;
    }

    public @NotNull Builder deleted(Boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public @NotNull Builder topGame(Boolean topGame) {
      this.topGame = topGame;
      return this;
    }

    public @NotNull Builder setupPosition(Boolean setupPosition) {
      this.setupPosition = setupPosition;
      return this;
    }

    public @NotNull Builder variant(String variant) {
      this.variant = variant;
      return this;
    }

    public @NotNull Builder noMoves(Integer noMoves) {
      this.noMoves = noMoves;
      return this;
    }

    public @NotNull Builder notation(String notation) {
      this.notation = notation;
      return this;
    }

    public @NotNull Builder variationMoves(Integer variationMoves) {
      this.variationMoves = variationMoves;
      return this;
    }

    public @NotNull Builder ait(String ait) {
      this.ait = ait;
      return this;
    }

    public @NotNull Builder vcs(String vcs) {
      this.vcs = vcs;
      return this;
    }

    public @NotNull Builder finalMaterial(String finalMaterial) {
      this.finalMaterial = finalMaterial;
      return this;
    }

    public @NotNull Builder gameVersion(Integer gameVersion) {
      this.gameVersion = gameVersion;
      return this;
    }

    public @NotNull Builder creationTimestamp(Long creationTimestamp) {
      this.creationTimestamp = creationTimestamp;
      return this;
    }

    public @NotNull Builder lastChanged(String lastChanged) {
      this.lastChanged = lastChanged;
      return this;
    }

    public @NotNull Builder moves(GameMovesDto moves) {
      this.moves = moves;
      return this;
    }

    public @NotNull Builder text(GameTextDto text) {
      this.text = text;
      return this;
    }

    public @NotNull Builder extraTags(Map<String, String> extraTags) {
      this.extraTags = extraTags;
      return this;
    }

    public @NotNull GameDto build() {
      return new GameDto(
          id, type, textTitle, whitePlayer, whiteElo, whiteEloType, blackPlayer, blackElo,
          blackEloType, whiteTeam, blackTeam, result, date, eco, round, subRound, board,
          lineEvaluation, timeControl, tournament, source, annotator, gameTag, medals, deleted,
          topGame, setupPosition, variant, noMoves, notation, variationMoves, ait, vcs,
          finalMaterial, gameVersion, creationTimestamp, lastChanged, moves, text, extraTags);
    }
  }
}
