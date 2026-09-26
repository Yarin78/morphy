package se.yarin.morphy.cb2.convert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess960;
import se.yarin.chess.Date;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.cb2.DatabaseTransaction;
import se.yarin.morphy.cb2.Game;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.cb2.games.EcoField;
import se.yarin.morphy.cb2.games.FinalMaterial;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.Timestamps;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.TournamentTimeControl;
import se.yarin.morphy.chessbase.TournamentType;
import se.yarin.morphy.chessbase.convert.GameMovesPgn;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.GameTextDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * Converts v2 games and entities to the neutral DTOs, filling in the fields the way the v1
 * converters do, so a client sees no difference between the formats but in what each stores.
 * Entity game counts are the lengths of the entities' game lists.
 */
public final class DtoConverter {
  private static final Logger log = LoggerFactory.getLogger(DtoConverter.class);

  // Annotation magnitude bits of a game record
  private static final int MAG_VARIATIONS = 3, MAG_COMMENTARY = 1 << 2, MAG_SYMBOLS = 1 << 3;
  private static final int MAG_SQUARES = 1 << 4, MAG_ARROWS = 1 << 5, MAG_TRAINING = 1 << 9;

  private static final Map<Integer, String> TIEBREAKS =
      Map.ofEntries(
          Map.entry(0, "Not set"),
          Map.entry(1, "Rating of Buchholz"),
          Map.entry(2, "Feine Buchholz"),
          Map.entry(3, "Median Buchholz"),
          Map.entry(4, "Fortschritt"),
          Map.entry(5, "Sonneborn-Berger"),
          Map.entry(11, "# wins"),
          Map.entry(12, "# black wins"),
          Map.entry(13, "# black games"),
          Map.entry(14, "Point group"),
          Map.entry(16, "Median2 Buchholz"),
          Map.entry(17, "Buchholz cut 1"),
          Map.entry(18, "Buchholz cut 2"),
          Map.entry(19, "Sonneborn-Berger"),
          Map.entry(21, "Koya"));

  private static final String[] QUALITIES = {"UNSET", "HIGH", "MEDIUM", "LOW"};

  /**
   * Converts a game, text or analysis.
   *
   * @param game the game
   * @param fetch how much of it to include
   */
  public @NotNull GameDto toDto(@NotNull Game game, @NotNull GameFetchOptions fetch) {
    DatabaseTransaction txn = game.transaction();
    boolean details = fetch.includeEntityDetails();
    String type = game.isText() ? "text" : game.isAnalysis() ? "analysis" : "game";

    GameTag tag = game.gameTag();
    String textTitle = null;
    if (!game.isGame() && tag != null && !tag.isEmpty()) {
      textTitle = tag.title();
    }

    PlayerDto white = null, black = null;
    Integer whiteElo = null, blackElo = null;
    TeamDto whiteTeam = null, blackTeam = null;
    GameResult result = GameResult.NOT_FINISHED;
    Date date = Date.unset();
    String eco = null;
    Integer round = null, subRound = null;
    NAG lineEvaluation = null;
    List<String> medals = null;
    Boolean setupPosition = null;
    String variant = null;
    Integer noMoves = null;
    String ait = null, vcs = null, finalMaterial = null;
    Integer gameVersion = null;
    Long creationTimestamp = null;
    String lastChanged = null;

    if (game.record() instanceof GameHeader g) {
      white = player(txn, g.whiteId(), game.white());
      black = player(txn, g.blackId(), game.black());
      whiteElo = g.whiteElo() > 0 ? g.whiteElo() : null;
      blackElo = g.blackElo() > 0 ? g.blackElo() : null;
      whiteTeam = team(txn, g.whiteTeamId(), game.whiteTeam(), details);
      blackTeam = team(txn, g.blackTeamId(), game.blackTeam(), details);
      result = result(g.result());
      date = Dates.decode(g.playedDate());
      if (!g.chess960() && EcoField.eco(g.eco()).isSet()) {
        eco = EcoField.eco(g.eco()).toString();
      }
      round = g.round() > 0 ? g.round() : null;
      subRound = g.subRound() > 0 ? g.subRound() : null;
      lineEvaluation = g.result() == GameResult.NOT_FINISHED.ordinal() ? nag(g.lineEvaluation()) : null;
      EnumSet<Medal> medalSet = Medal.decode(g.medals());
      medals = medalSet.isEmpty() ? null : medalSet.stream().map(Medal::name).toList();
      EnumSet<GameHeaderFlags> flags = GameHeaderFlags.decodeFlags(g.flags());
      setupPosition = flags.contains(GameHeaderFlags.SETUP_POSITION) ? true : null;
      variant =
          g.chess960() || flags.contains(GameHeaderFlags.UNORTHODOX) ? Chess960.VARIANT : null;
      noMoves = g.moveCount() != 0 ? g.moveCount() : null;
      ait = emptyToNull(ait(flags, g.annotationMagnitudes()));
      vcs = emptyToNull(vcs(flags, g.annotationMagnitudes()));
      if (g.finalMaterialGreater() != 0 || g.finalMaterialLesser() != 0) {
        finalMaterial =
            FinalMaterial.toString(g.finalMaterialGreater())
                + " - "
                + FinalMaterial.toString(g.finalMaterialLesser());
      }
      gameVersion = g.version() != 0 ? g.version() : null;
      creationTimestamp = g.creationTimestamp() != 0 ? g.creationTimestamp() : null;
      Instant changed = Timestamps.lastChangedInstant(g.lastChangedTimestamp());
      lastChanged = changed == null ? null : changed.toString();
    }

    Tournament tournament = game.tournament();
    if (game.isText() && tournament != null) {
      date = Dates.decode(tournament.startDate());
    }

    GameMovesDto moves = null;
    String notation = game.isText() ? "Text" : null;
    Integer variationMoves = null;
    if (fetch.includeMoves() && !game.isText()) {
      try {
        GameMovesModel model = game.moves();
        moves = new GameMovesDto(GameMovesPgn.toPgn(model), GameMovesPgn.toFen(model));
        String built = model.getNotation(20);
        notation = built != null ? built : "--";
        int variationPly = model.countPly(true) - model.countPly(false);
        variationMoves = variationPly > 0 ? variationPly : null;
      } catch (RuntimeException e) {
        log.error("Failed to export the moves of game {}", game.id(), e);
      }
    }

    GameTextDto text = null;
    if (fetch.includeText() && game.isText()) {
      try {
        text = new GameTextDto(game.textContents().getContents());
      } catch (RuntimeException e) {
        log.error("Failed to read the text of game {}", game.id(), e);
      }
    }

    long tournamentId = game.tournamentId();
    TournamentDto tournamentDto = null;
    if (tournamentId >= 0 && tournament != null) {
      tournamentDto =
          details
              ? toDto(txn, tournamentId, tournament, false)
              : new TournamentDto(
                  tournamentId, tournament.title(), null, null, null, null, null, null, null, null,
                  null, null, null, null, null, null, null, null);
    }
    Source source = game.source();
    SourceDto sourceDto = null;
    if (game.sourceId() >= 0 && source != null) {
      sourceDto =
          details
              ? toDto(txn, game.sourceId(), source, false)
              : new SourceDto(
                  game.sourceId(), emptyToNull(source.title()), null, null, null, null, null, null);
    }
    Player annotator = game.annotator();
    AnnotatorDto annotatorDto =
        game.annotatorId() >= 0 && annotator != null
            ? toAnnotatorDto(txn, game.annotatorId(), annotator)
            : null;
    GameTagDto gameTagDto =
        game.isGame() && game.gameTagId() >= 0 && tag != null
            ? toDto(txn, game.gameTagId(), tag)
            : null;

    return new GameDto(
        (long) game.id(),
        type,
        textTitle,
        white,
        whiteElo,
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
        tournamentDto,
        sourceDto,
        annotatorDto,
        gameTagDto,
        medals,
        game.deleted() ? true : null,
        null,
        setupPosition,
        variant,
        noMoves,
        notation,
        variationMoves,
        ait,
        vcs,
        finalMaterial,
        gameVersion,
        creationTimestamp,
        lastChanged,
        moves,
        text,
        null);
  }

  private @Nullable PlayerDto player(DatabaseTransaction txn, long id, @Nullable Player player) {
    return id >= 0 && player != null ? toDto(txn, id, player) : null;
  }

  private @Nullable TeamDto team(
      DatabaseTransaction txn, long id, @Nullable Team team, boolean details) {
    if (id < 0 || team == null) {
      return null;
    }
    return details
        ? toDto(txn, id, team, false)
        : new TeamDto(id, emptyToNull(team.title()), null, null, null, null, null);
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  private static @Nullable Integer count(DatabaseTransaction txn, long id, Role role) {
    int count = txn.gameCount(id, role);
    return count > 0 ? count : null;
  }

  public @NotNull PlayerDto toDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull Player player) {
    return new PlayerDto(
        id,
        emptyToNull(player.lastName()),
        emptyToNull(player.firstName()),
        count(txn, id, Role.PLAYER),
        player.fideId() > 0 ? player.fideId() : null,
        player.chessBaseId() > 0 ? (long) player.chessBaseId() : null);
  }

  public @NotNull AnnotatorDto toAnnotatorDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull Player annotator) {
    return new AnnotatorDto(
        id, emptyToNull(annotator.fullName()), count(txn, id, Role.ANNOTATOR));
  }

  public @NotNull TournamentDto toDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull Tournament t, boolean withCount) {
    TournamentType type = tournamentType(t);
    TournamentTimeControl timeControl = timeControl(t);
    List<String> tiebreaks = new ArrayList<>();
    for (int rule : t.tiebreaks()) {
      tiebreaks.add(TIEBREAKS.getOrDefault(rule, "Rule " + rule));
    }
    Date start = Dates.decode(t.startDate()), end = Dates.decode(t.endDate());
    return new TournamentDto(
        id,
        t.title(),
        start.isUnset() ? null : start,
        end.isUnset() ? null : end,
        emptyToNull(t.place()),
        nation(t.nation()),
        t.category() == 0 ? null : t.category(),
        t.category() == 0 ? null : categoryRoman(t.category()),
        t.rounds() == 0 ? null : t.rounds(),
        type == TournamentType.NONE ? null : type.getName(),
        timeControl == TournamentTimeControl.NORMAL ? null : timeControl.getName(),
        emptyToNull(typeCombined(type, timeControl, t.teamTournament())),
        t.complete(),
        t.teamTournament() ? true : null,
        tiebreaks.isEmpty() ? null : tiebreaks,
        t.latitude() != 0 ? (double) t.latitude() : null,
        t.longitude() != 0 ? (double) t.longitude() : null,
        withCount ? count(txn, id, Role.TOURNAMENT) : null);
  }

  public @NotNull SourceDto toDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull Source s, boolean withCount) {
    Date publication = Dates.decode(s.publicationDate()), date = Dates.decode(s.date());
    return new SourceDto(
        id,
        emptyToNull(s.title()),
        emptyToNull(s.publisher()),
        publication.isUnset() ? null : publication,
        date.isUnset() ? null : date,
        s.version() == 0 ? null : s.version(),
        s.quality() > 0 && s.quality() < QUALITIES.length ? QUALITIES[s.quality()] : null,
        withCount ? count(txn, id, Role.SOURCE) : null);
  }

  public @NotNull TeamDto toDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull Team t, boolean withCount) {
    return new TeamDto(
        id,
        emptyToNull(t.title()),
        t.number() == 0 ? null : t.number(),
        t.season() ? true : null,
        t.year() == 0 ? null : t.year(),
        nation(t.nation()),
        withCount ? count(txn, id, Role.TEAM) : null);
  }

  public @NotNull GameTagDto toDto(
      @NotNull DatabaseTransaction txn, long id, @NotNull GameTag tag) {
    String english = title(tag, 42), german = title(tag, 53), french = title(tag, 49);
    String spanish = title(tag, 43), italian = title(tag, 70), dutch = title(tag, 103);
    List<String> languages = new ArrayList<>();
    int count = 0;
    for (GameTag.Title t : tag.titles()) {
      if (!t.text().isEmpty()) {
        count++;
        String code = nation(t.language());
        languages.add(code == null ? Integer.toString(t.language()) : code);
      }
    }
    return new GameTagDto(
        id,
        emptyToNull(tag.title()),
        languages.isEmpty() ? null : String.join(" ", languages),
        count,
        emptyToNull(english),
        emptyToNull(german),
        emptyToNull(french),
        emptyToNull(spanish),
        emptyToNull(italian),
        emptyToNull(dutch),
        null,
        null,
        count(txn, id, Role.GAME_TAG));
  }

  private static String title(GameTag tag, int language) {
    for (GameTag.Title t : tag.titles()) {
      if (t.language() == language) {
        return t.text();
      }
    }
    return "";
  }

  // ── From DTOs ────────────────────────────────────────────────────────────

  /** A player with the names of a DTO, keeping the rest of an existing one. */
  public @NotNull Player toPlayer(@NotNull PlayerDto dto, @NotNull Player existing) {
    return new Player(
        text(dto.lastName()),
        text(dto.firstName()),
        existing.unknown1(),
        existing.unknown2(),
        dto.chessBaseId() == null ? existing.chessBaseId() : dto.chessBaseId().intValue(),
        existing.fideIdSize(),
        dto.fideId() == null ? existing.fideId() : dto.fideId());
  }

  /** An annotator, a player, with the name of a DTO, keeping the rest of an existing one. */
  public @NotNull Player toAnnotator(@NotNull AnnotatorDto dto, @NotNull Player existing) {
    Player named = Player.ofFullName(text(dto.name()));
    return existing.withNames(named.lastName(), named.firstName());
  }

  /** A tournament with the fields of a DTO, keeping those the DTO lacks from an existing one. */
  public @NotNull Tournament toTournament(@NotNull TournamentDto dto, @NotNull Tournament existing) {
    int type = dto.type() == null ? 0 : TournamentType.fromName(dto.type()).ordinal();
    if (dto.timeControl() != null) {
      type |=
          switch (TournamentTimeControl.fromName(dto.timeControl())) {
            case BLITZ -> 0x20;
            case RAPID -> 0x40;
            case CORRESPONDENCE -> 0x80;
            case NORMAL -> 0;
          };
    }
    int flags = existing.flags() & ~3;
    if (Boolean.TRUE.equals(dto.complete())) {
      flags |= 3;
    }
    return new Tournament(
        text(dto.place()),
        text(dto.title()),
        dto.startDate() == null ? 0 : Dates.encode(dto.startDate()),
        type,
        Boolean.TRUE.equals(dto.teamTournament()) ? existing.teamFlags() | 1 : existing.teamFlags() & ~1,
        nationCode(dto.nation()),
        existing.unknown7(),
        dto.category() == null ? 0 : dto.category(),
        flags,
        dto.rounds() == null ? 0 : dto.rounds(),
        existing.unknown11(),
        dto.latitude() == null ? 0f : dto.latitude().floatValue(),
        dto.longitude() == null ? 0f : dto.longitude().floatValue(),
        existing.placeNation(),
        existing.unknown21(),
        existing.tiebreaks(),
        dto.endDate() == null ? 0 : Dates.encode(dto.endDate()),
        existing.trailing());
  }

  /** A source with the fields of a DTO. */
  public @NotNull Source toSource(@NotNull SourceDto dto) {
    int quality = 0;
    for (int i = 0; i < QUALITIES.length; i++) {
      if (QUALITIES[i].equalsIgnoreCase(text(dto.quality()))) {
        quality = i;
      }
    }
    return new Source(
        text(dto.title()),
        text(dto.publisher()),
        dto.publication() == null ? 0 : Dates.encode(dto.publication()),
        dto.date() == null ? 0 : Dates.encode(dto.date()),
        dto.version() == null ? 0 : dto.version(),
        quality);
  }

  /** A team with the fields of a DTO. */
  public @NotNull Team toTeam(@NotNull TeamDto dto) {
    return new Team(
        text(dto.title()),
        dto.teamNumber() == null ? 0 : dto.teamNumber(),
        Boolean.TRUE.equals(dto.season()) ? 1 : 0,
        dto.year() == null ? 0 : dto.year(),
        nationCode(dto.nation()));
  }

  /**
   * A game tag with the titles of a DTO, in the languages it has; a DTO with only a title gives
   * the English one.
   */
  public @NotNull GameTag toGameTag(@NotNull GameTagDto dto, @NotNull GameTag existing) {
    Map<Integer, String> titles = new TreeMap<>();
    for (GameTag.Title t : existing.titles()) {
      titles.put(t.language(), "");
    }
    for (int language : GameTag.OFFERED_LANGUAGES) {
      titles.putIfAbsent(language, "");
    }
    titles.put(42, text(dto.englishTitle() != null ? dto.englishTitle() : dto.title()));
    titles.put(53, text(dto.germanTitle()));
    titles.put(49, text(dto.frenchTitle()));
    titles.put(43, text(dto.spanishTitle()));
    titles.put(70, text(dto.italianTitle()));
    titles.put(103, text(dto.dutchTitle()));
    List<GameTag.Title> list = new ArrayList<>();
    titles.forEach((language, title) -> list.add(new GameTag.Title(language, title)));
    return new GameTag(list);
  }

  private static int nationCode(@Nullable String ioc) {
    return ioc == null || ioc.isBlank() ? 0 : Nation.fromIOC(ioc).ordinal();
  }

  private static String text(@Nullable String s) {
    return s == null ? "" : s;
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  public static @NotNull TournamentType tournamentType(@NotNull Tournament t) {
    int type = t.tournamentType();
    return type < TournamentType.values().length ? TournamentType.values()[type] : TournamentType.NONE;
  }

  public static @NotNull TournamentTimeControl timeControl(@NotNull Tournament t) {
    if (t.correspondence()) return TournamentTimeControl.CORRESPONDENCE;
    if (t.rapid()) return TournamentTimeControl.RAPID;
    if (t.blitz()) return TournamentTimeControl.BLITZ;
    return TournamentTimeControl.NORMAL;
  }

  private static String typeCombined(
      TournamentType type, TournamentTimeControl timeControl, boolean team) {
    String name = type.getLongName();
    if (timeControl != TournamentTimeControl.NORMAL) {
      String tc = timeControl.getName();
      name += " (" + Character.toUpperCase(tc.charAt(0)) + tc.substring(1) + ")";
    }
    if (team) {
      name = name.isEmpty() ? "Team" : "Team-" + name;
    }
    return name;
  }

  private static String categoryRoman(int category) {
    String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
    return category > 39 ? Integer.toString(category) : "X".repeat(category / 10) + roman[category % 10];
  }

  /** The IOC code of a nation, or null for none. */
  public static @Nullable String nation(int code) {
    return code > 0 && code < Nation.values().length ? Nation.values()[code].getIocCode() : null;
  }

  private static GameResult result(int value) {
    GameResult[] results = GameResult.values();
    return value >= 0 && value < results.length ? results[value] : GameResult.NOT_FINISHED;
  }

  private static @Nullable NAG nag(int value) {
    NAG[] nags = NAG.values();
    return value > 0 && value < nags.length ? nags[value] : null;
  }

  /** The annotation magnitudes of a game in the {@code vcs} column: variations, commentary, symbols, training. */
  static @NotNull String vcs(EnumSet<GameHeaderFlags> flags, int magnitudes) {
    StringBuilder sb = new StringBuilder();
    if (flags.contains(GameHeaderFlags.VARIATIONS)) {
      sb.append("vVrR".charAt(Math.min(3, magnitudes & MAG_VARIATIONS)));
    }
    if (flags.contains(GameHeaderFlags.COMMENTARY)) {
      sb.append((magnitudes & MAG_COMMENTARY) != 0 ? 'C' : 'c');
    }
    if (flags.contains(GameHeaderFlags.SYMBOLS)) {
      sb.append((magnitudes & MAG_SYMBOLS) != 0 ? 'S' : 's');
    }
    if (flags.contains(GameHeaderFlags.TRAINING)) {
      sb.append((magnitudes & MAG_TRAINING) != 0 ? 'T' : 't');
    }
    return sb.toString();
  }

  /** The other annotations of a game in the {@code ait} column: graphics, training, and more. */
  static @NotNull String ait(EnumSet<GameHeaderFlags> flags, int magnitudes) {
    StringBuilder sb = new StringBuilder();
    if (flags.contains(GameHeaderFlags.GRAPHICAL_ARROWS)
        || flags.contains(GameHeaderFlags.GRAPHICAL_SQUARES)) {
      sb.append((magnitudes & (MAG_ARROWS | MAG_SQUARES)) != 0 ? 'A' : 'a');
    }
    if (flags.contains(GameHeaderFlags.TRAINING)) {
      sb.append((magnitudes & MAG_TRAINING) != 0 ? 'T' : 't');
    }
    if (flags.contains(GameHeaderFlags.CRITICAL_POSITION)) {
      sb.append('I');
    }
    if (flags.contains(GameHeaderFlags.GAME_QUOTATION)) {
      sb.append('G');
    }
    if (flags.contains(GameHeaderFlags.ANNO_TYPE_1A)
        || flags.contains(GameHeaderFlags.EMBEDDED_AUDIO)
        || flags.contains(GameHeaderFlags.EMBEDDED_PICTURE)
        || flags.contains(GameHeaderFlags.EMBEDDED_VIDEO)) {
      sb.append('M');
    }
    return sb.toString();
  }

  private static @Nullable String emptyToNull(@Nullable String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
