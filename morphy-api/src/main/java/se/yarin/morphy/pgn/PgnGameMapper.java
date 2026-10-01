package se.yarin.morphy.pgn;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess960;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.EloType;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.GameTagLanguage;
import se.yarin.chess.NAG;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TimeControlDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * Converts between {@link GameDto} and {@link GameModel}, for formats where a game is a header of
 * plain values rather than references to entities.
 *
 * <p>Going from a DTO to a model, an entity DTO with an id binds the header to that existing entity;
 * the entity itself is resolved by whoever writes the model. Going from a model to a DTO, all
 * entity ids are null.
 */
public final class PgnGameMapper {
  private static final Logger log = LoggerFactory.getLogger(PgnGameMapper.class);

  /** The PGN value for a name that isn't known. */
  private static final String UNKNOWN = "?";

  /** The PGN tag with the time control; see {@link TimeControlDto#toPgn()}. */
  private static final String TIME_CONTROL_TAG = "TimeControl";

  private final @NotNull PgnMoves pgnMoves;

  /** @param pgnMoves how the moves are turned into and out of movetext */
  public PgnGameMapper(@NotNull PgnMoves pgnMoves) {
    this.pgnMoves = pgnMoves;
  }

  // ── DTO -> model ─────────────────────────────────────────────────────────

  /**
   * Converts a GameDto to a GameModel.
   *
   * @param dto the GameDto to convert (must be a regular game, not guiding text)
   * @throws IllegalArgumentException if the DTO represents guiding text instead of a game, or its
   *     moves can't be read
   */
  public @NotNull GameModel toModel(@NotNull GameDto dto) {
    if ("text".equals(dto.type())) {
      throw new IllegalArgumentException(
          "Cannot convert guiding text to GameModel. Use toTextModel() instead.");
    }

    GameHeaderModel headerModel = toHeader(dto);

    boolean chess960 = dto.variant() != null;
    if (chess960 && !Chess960.isVariant(dto.variant())) {
      throw new IllegalArgumentException("Unsupported variant: " + dto.variant());
    }

    // Moves are required to read back; an unreadable movetext is an invalid game, not an empty one
    String pgn = dto.moves() == null ? null : dto.moves().pgn();
    String fen = dto.moves() == null ? null : dto.moves().fen();
    if ((pgn == null || pgn.isEmpty()) && fen == null) {
      return new GameModel(headerModel, new GameMovesModel());
    }
    GameMovesModel movesModel = pgnMoves.fromPgn(pgn == null ? "" : pgn, fen, chess960);
    return new GameModel(headerModel, movesModel);
  }

  /**
   * Builds a GameHeaderModel from a GameDto. An entity DTO with an id binds the header to that
   * existing entity; one without is resolved by name when the game is written.
   */
  public @NotNull GameHeaderModel toHeader(@NotNull GameDto dto) {
    GameHeaderModel headerModel = new GameHeaderModel();

    boolean isText = "text".equals(dto.type());

    if (!isText) {
      headerModel.setWhite(fullName(dto.whitePlayer()));
      headerModel.setBlack(fullName(dto.blackPlayer()));

      headerModel.setWhiteFideId(dto.whitePlayer() == null ? null : dto.whitePlayer().fideId());
      headerModel.setBlackFideId(dto.blackPlayer() == null ? null : dto.blackPlayer().fideId());
      if (dto.whitePlayer() != null && dto.whitePlayer().id() != null) {
        headerModel.setWhiteId(dto.whitePlayer().id());
      }
      if (dto.blackPlayer() != null && dto.blackPlayer().id() != null) {
        headerModel.setBlackId(dto.blackPlayer().id());
      }

      if (dto.whiteElo() != null) {
        headerModel.setWhiteElo(dto.whiteElo());
        headerModel.setWhiteEloType(dto.whiteEloType());
      }
      if (dto.blackElo() != null) {
        headerModel.setBlackElo(dto.blackElo());
        headerModel.setBlackEloType(dto.blackEloType());
      }

      if (dto.whiteTeam() != null) {
        TeamDto team = dto.whiteTeam();
        headerModel.setWhiteTeam(team.title());
        headerModel.setWhiteTeamId(team.id());
        headerModel.setWhiteTeamNumber(team.teamNumber());
        headerModel.setWhiteTeamSeason(team.season());
        headerModel.setWhiteTeamYear(team.year());
        headerModel.setWhiteTeamNation(team.nation());
      }
      if (dto.blackTeam() != null) {
        TeamDto team = dto.blackTeam();
        headerModel.setBlackTeam(team.title());
        headerModel.setBlackTeamId(team.id());
        headerModel.setBlackTeamNumber(team.teamNumber());
        headerModel.setBlackTeamSeason(team.season());
        headerModel.setBlackTeamYear(team.year());
        headerModel.setBlackTeamNation(team.nation());
      }
    }

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
    if (dto.board() != null) {
      headerModel.setBoard(dto.board());
    }
    if (dto.lineEvaluation() != null) {
      headerModel.setLineEvaluation(dto.lineEvaluation());
    }

    if (dto.tournament() != null) {
      TournamentDto tournament = dto.tournament();
      if (tournament.title() != null) {
        headerModel.setEvent(tournament.title());
      }
      if (tournament.id() != null) {
        headerModel.setEventId(tournament.id());
      }
      if (tournament.startDate() != null) {
        headerModel.setEventDate(tournament.startDate());
      }
      if (tournament.endDate() != null) {
        headerModel.setEventEndDate(tournament.endDate());
      }
      if (tournament.place() != null) {
        headerModel.setEventSite(tournament.place());
      }
      if (tournament.nation() != null) {
        headerModel.setEventCountry(tournament.nation());
      }
      if (tournament.category() != null) {
        headerModel.setEventCategory(tournament.category());
      }
      if (tournament.rounds() != null) {
        headerModel.setEventRounds(tournament.rounds());
      }
      if (tournament.type() != null) {
        headerModel.setEventType(tournament.type());
      }
      if (tournament.timeControl() != null) {
        headerModel.setEventTimeControl(tournament.timeControl());
      }
      if (tournament.complete() != null) {
        headerModel.setEventComplete(tournament.complete());
      }
      if (tournament.teamTournament() != null) {
        headerModel.setEventTeamTournament(tournament.teamTournament());
      }
    }

    if (dto.source() != null) {
      SourceDto source = dto.source();
      if (source.id() != null) {
        headerModel.setSourceId(source.id());
      }
      if (source.title() != null) {
        headerModel.setSourceTitle(source.title());
      }
      if (source.publisher() != null) {
        headerModel.setSource(source.publisher());
      }
      if (source.date() != null) {
        headerModel.setSourceDate(source.date());
      }
      if (source.publication() != null) {
        headerModel.setSourcePublication(source.publication());
      }
      if (source.version() != null) {
        headerModel.setSourceVersion(source.version());
      }
      if (source.quality() != null) {
        headerModel.setSourceQuality(source.quality());
      }
    }

    if (dto.annotator() != null) {
      if (dto.annotator().id() != null) {
        headerModel.setAnnotatorId(dto.annotator().id());
      }
      if (dto.annotator().name() != null) {
        headerModel.setAnnotator(dto.annotator().name());
      }
    }

    if (dto.gameTag() != null) {
      GameTagDto tag = dto.gameTag();
      if (tag.id() != null) {
        headerModel.setGameTagId(tag.id());
      }
      for (GameTagLanguage language : GameTagLanguage.values()) {
        headerModel.setGameTagTitle(language, known(tag.title(language)));
      }
      // The title shown, which is the English one if a new tag has no titles by language
      String title = known(tag.title());
      headerModel.setGameTag(title != null ? title : known(tag.englishTitle()));
    }

    if (dto.extraTags() != null) {
      dto.extraTags()
          .forEach(
              (name, value) -> {
                // A tag named like a header field can't be an extra tag
                if (!GameHeaderModel.STANDARD_FIELDS.contains(name)) {
                  headerModel.setExtraTag(name, value);
                }
              });
    }
    if (dto.timeControl() != null) {
      headerModel.setExtraTag(TIME_CONTROL_TAG, dto.timeControl().toPgn());
    }

    return headerModel;
  }

  private static @NotNull String fullName(@Nullable PlayerDto player) {
    if (player == null || player.lastName() == null) {
      return UNKNOWN;
    }
    String name = player.lastName();
    if (player.firstName() != null && !player.firstName().isEmpty()) {
      name += ", " + player.firstName();
    }
    return name;
  }

  // ── Model -> DTO ─────────────────────────────────────────────────────────

  /**
   * Converts a GameModel to a GameDto. The entity DTOs get only what the header says: no ids and
   * no game counts.
   *
   * @param id the id of the game in its database, or null
   * @param includeMoves whether to include the moves; the move-derived fields are then set too
   */
  public @NotNull GameDto toDto(
      @NotNull GameModel model, @Nullable Long id, boolean includeMoves) {
    GameHeaderModel header = model.header();
    GameMovesModel moves = model.moves();

    Eco eco = header.getEco();
    NAG lineEvaluation = header.getLineEvaluation();
    // The TimeControl tag is the time control, if it can be read
    LinkedHashMap<String, String> extraTags = new LinkedHashMap<>(header.getExtraTags());
    TimeControlDto timeControl = TimeControlDto.fromPgn(extraTags.get(TIME_CONTROL_TAG));
    if (timeControl != null) {
      extraTags.remove(TIME_CONTROL_TAG);
    }

    GameMovesDto movesDto = null;
    String notation = null;
    Integer variationMoves = null;
    if (includeMoves) {
      movesDto = new GameMovesDto(pgnMoves.toPgn(moves), pgnMoves.toFen(moves));
      notation = moves.getNotation(20);
      int varPly = moves.countPly(true) - moves.countPly(false);
      variationMoves = varPly > 0 ? varPly : null;
    }

    return new GameDto(
        id,
        "game",
        null,
        player(header.getWhite(), header.getWhiteFideId()),
        header.getWhiteElo(),
        eloType(header.getWhiteElo(), header.getWhiteEloType()),
        player(header.getBlack(), header.getBlackFideId()),
        header.getBlackElo(),
        eloType(header.getBlackElo(), header.getBlackEloType()),
        team(
            header.getWhiteTeam(),
            header.getWhiteTeamNumber(),
            header.getWhiteTeamSeason(),
            header.getWhiteTeamYear(),
            header.getWhiteTeamNation()),
        team(
            header.getBlackTeam(),
            header.getBlackTeamNumber(),
            header.getBlackTeamSeason(),
            header.getBlackTeamYear(),
            header.getBlackTeamNation()),
        header.getResult() != null ? header.getResult() : GameResult.NOT_FINISHED,
        header.getDate() != null ? header.getDate() : Date.unset(),
        eco != null && eco.isSet() ? eco.toString() : null,
        header.getRound(),
        header.getSubRound(),
        header.getBoard(),
        lineEvaluation == null || lineEvaluation == NAG.NONE ? null : lineEvaluation,
        timeControl,
        tournament(header),
        source(header),
        header.getAnnotator() == null ? null : new AnnotatorDto(null, header.getAnnotator(), null),
        gameTag(header),
        null,
        null,
        null,
        moves.isSetupPosition() ? true : null,
        moves.root().position().isRegularChess() ? null : Chess960.VARIANT,
        null,
        notation,
        variationMoves,
        null,
        null,
        null,
        null,
        null,
        null,
        movesDto,
        null,
        extraTags.isEmpty() ? null : extraTags);
  }

  /** A value that stands for something known, or null for a missing or "?" value. */
  private static @Nullable String known(@Nullable String value) {
    return value == null || value.isBlank() || value.equals(UNKNOWN) ? null : value;
  }

  private static @Nullable PlayerDto player(@Nullable String name, @Nullable Long fideId) {
    if (known(name) == null) {
      return null;
    }
    int comma = name.indexOf(',');
    String lastName = (comma < 0 ? name : name.substring(0, comma)).strip();
    String firstName = comma < 0 ? null : name.substring(comma + 1).strip();
    return new PlayerDto(
        null, lastName, firstName == null || firstName.isEmpty() ? null : firstName, null, fideId, null);
  }

  private static @Nullable TeamDto team(
      @Nullable String title,
      @Nullable Integer number,
      @Nullable Boolean season,
      @Nullable Integer year,
      @Nullable String nation) {
    return known(title) == null ? null : new TeamDto(null, title, number, season, year, nation, null);
  }

  private static @Nullable GameTagDto gameTag(@NotNull GameHeaderModel header) {
    String title = known(header.getGameTag());
    Map<GameTagLanguage, String> titles = header.getGameTagTitles();
    if (title == null && titles.isEmpty()) {
      return null;
    }
    // A tag known only by its title has it in English
    String english = titles.isEmpty() ? title : titles.get(GameTagLanguage.ENGLISH);
    return new GameTagDto(
        null,
        title,
        null,
        null,
        english,
        titles.get(GameTagLanguage.GERMAN),
        titles.get(GameTagLanguage.FRENCH),
        titles.get(GameTagLanguage.SPANISH),
        titles.get(GameTagLanguage.ITALIAN),
        titles.get(GameTagLanguage.DUTCH),
        titles.get(GameTagLanguage.SLOVENIAN),
        titles.get(GameTagLanguage.PORTUGUESE),
        null,
        null);
  }

  /** The type of an elo, which only means something when there is one. */
  private static @Nullable EloType eloType(@Nullable Integer elo, @Nullable EloType type) {
    return elo == null || elo == 0 ? null : type;
  }

  private static @Nullable TournamentDto tournament(@NotNull GameHeaderModel header) {
    String title = known(header.getEvent());
    String place = known(header.getEventSite());
    Date startDate = header.getEventDate() == null || header.getEventDate().isUnset() ? null : header.getEventDate();
    Date endDate = header.getEventEndDate() == null || header.getEventEndDate().isUnset() ? null : header.getEventEndDate();
    if (title == null
        && place == null
        && startDate == null
        && endDate == null
        && header.getEventCountry() == null
        && header.getEventCategory() == null
        && header.getEventRounds() == null
        && header.getEventType() == null
        && header.getEventTimeControl() == null
        && header.getEventComplete() == null
        && header.getEventTeamTournament() == null) {
      return null;
    }
    return new TournamentDto(
        null,
        title == null ? "" : title,
        startDate,
        endDate,
        place,
        header.getEventCountry(),
        header.getEventCategory(),
        null,
        header.getEventRounds(),
        header.getEventType(),
        header.getEventTimeControl(),
        null,
        header.getEventComplete(),
        header.getEventTeamTournament(),
        null,
        null,
        null,
        null);
  }

  private static @Nullable SourceDto source(@NotNull GameHeaderModel header) {
    Date date = known(header.getSourceDate());
    Date publication = known(header.getSourcePublication());
    if (header.getSourceTitle() == null
        && header.getSource() == null
        && date == null
        && publication == null
        && header.getSourceVersion() == null
        && header.getSourceQuality() == null) {
      return null;
    }
    return new SourceDto(
        null,
        header.getSourceTitle(),
        header.getSource(),
        publication,
        date,
        header.getSourceVersion(),
        header.getSourceQuality(),
        null);
  }

  private static @Nullable Date known(@Nullable Date date) {
    return date == null || date.isUnset() ? null : date;
  }
}
