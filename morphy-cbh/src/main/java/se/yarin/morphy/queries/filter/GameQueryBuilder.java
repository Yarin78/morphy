package se.yarin.morphy.queries.filter;

import java.util.*;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.games.Medal;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.*;

/**
 * Builds a {@link GameQuery} from a filter expression string or a list of {@link FilterCondition}s.
 *
 * <p>Supports game property filters (result, date, rating, eco, round, type), entity ID filters
 * (playerId, tournamentId, etc.), and entity property filters (player.name, tournament.title,
 * etc.).
 *
 * <p>Multiple conditions on the same entity type are combined into a single {@link GameEntityJoin}
 * with all filters, avoiding the bug where separate joins could match different entities.
 */
public class GameQueryBuilder {
  private static final Logger log = LoggerFactory.getLogger(GameQueryBuilder.class);

  private static final Map<String, Function<FilterCondition, GameFilter>> GAME_FILTERS =
      Map.ofEntries(
          Map.entry("result", c -> new ResultsFilter(c.value())),
          Map.entry("date", GameQueryBuilder::buildDateFilter),
          Map.entry("rating", GameQueryBuilder::buildRatingFilter),
          Map.entry("eco", c -> new EcoFilter(c.value())),
          Map.entry("round", GameQueryBuilder::buildRoundFilter),
          Map.entry("type", GameQueryBuilder::buildTypeFilter),
          Map.entry(
              "setup", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.SETUP_POSITION)),
          Map.entry(
              "variations", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.VARIATIONS)),
          Map.entry(
              "commentary", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.COMMENTARY)),
          Map.entry("symbols", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.SYMBOLS)),
          Map.entry(
              "training", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.TRAINING)),
          Map.entry(
              "annotations", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.ANNOTATIONS)),
          Map.entry("deleted", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.DELETED)),
          Map.entry(
              "chess960", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.CHESS960)),
          Map.entry(
              "correspondence",
              c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.CORRESPONDENCE)),
          Map.entry("media", c -> buildBooleanFlagFilter(c, GameFlagFilter.Flag.MEDIA)),
          Map.entry("medals", GameQueryBuilder::buildMedalFilter),
          Map.entry("medal", GameQueryBuilder::buildSpecificMedalFilter),
          Map.entry("moves", GameQueryBuilder::buildMovesFilter));

  /** Fields that require a numeric entity ID and produce a direct game filter. */
  private static final Set<String> ENTITY_ID_FIELDS =
      Set.of("playerid", "tournamentid", "annotatorid", "sourceid", "teamid", "gametagid");

  /**
   * Fields that act as shorthand for entity property filters using the entity's default field. For
   * example, "player:Carlsen" is rewritten to "player.name:Carlsen".
   */
  private static final Map<String, String> ENTITY_SHORTHAND_FIELDS =
      Map.ofEntries(
          Map.entry("player", "player"),
          Map.entry("white", "player"),
          Map.entry("black", "player"),
          Map.entry("winner", "player"),
          Map.entry("loser", "player"),
          Map.entry("tournament", "tournament"),
          Map.entry("annotator", "annotator"),
          Map.entry("source", "source"),
          Map.entry("team", "team"),
          Map.entry("gametag", "gametag"));

  /** Aliases that map to "player" with a fixed position. */
  private static final Map<String, GameEntityJoinCondition> PLAYER_POSITION_ALIASES =
      Map.of(
          "white", GameEntityJoinCondition.WHITE,
          "black", GameEntityJoinCondition.BLACK,
          "winner", GameEntityJoinCondition.WINNER,
          "loser", GameEntityJoinCondition.LOSER);

  private static final Map<String, AbstractEntityQueryBuilder<?>> ENTITY_BUILDERS =
      Map.of(
          "player", new PlayerQueryBuilder(),
          "tournament", new TournamentQueryBuilder(),
          "annotator", new AnnotatorQueryBuilder(),
          "source", new SourceQueryBuilder(),
          "team", new TeamQueryBuilder(),
          "gametag", new GameTagQueryBuilder());

  private final FilterQueryParser filterQueryParser = new FilterQueryParser("player.name");

  /**
   * Builds a GameQuery from a filter expression string.
   *
   * @param database the database to query
   * @param filterExpression the filter expression (e.g., "result:1-0 AND player.name:Carlsen")
   * @return GameQuery ready for execution via QueryPlanner
   */
  public @NotNull GameQuery buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new GameQuery(database, List.of(), List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  /**
   * Builds a GameQuery from a list of FilterConditions.
   *
   * @param database the database to query
   * @param conditions the filter conditions
   * @return GameQuery ready for execution via QueryPlanner
   */
  public @NotNull GameQuery buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<GameFilter> gameFilters = new ArrayList<>();

    // Group entity property conditions by entity type to combine them into single joins
    Map<String, List<FilterCondition>> entityConditions = new LinkedHashMap<>();

    for (FilterCondition condition : conditions) {
      if (isGamePropertyFilter(condition)) {
        gameFilters.add(buildGameFilter(condition));
      } else if (isEntityShorthandFilter(condition)) {
        FilterCondition rewritten = rewriteEntityNameShorthand(condition);
        String entityType = rewritten.field().split("\\.", 2)[0].toLowerCase();
        entityConditions.computeIfAbsent(entityType, k -> new ArrayList<>()).add(rewritten);
      } else if (isEntityIdFilter(condition)) {
        gameFilters.add(buildEntityIdFilter(condition));
      } else if (isEntityPropertyFilter(condition)) {
        String entityType = condition.field().split("\\.", 2)[0].toLowerCase();
        entityConditions.computeIfAbsent(entityType, k -> new ArrayList<>()).add(condition);
      } else {
        throw new IllegalArgumentException(
            "Unknown filter field: '"
                + condition.field()
                + "'. Available fields: "
                + availableFieldsSummary());
      }
    }

    // Build combined entity joins
    List<GameEntityJoin<?>> entityJoins = new ArrayList<>();
    for (var entry : entityConditions.entrySet()) {
      entityJoins.add(buildCombinedEntityJoin(database, entry.getKey(), entry.getValue()));
    }

    return new GameQuery(database, gameFilters, entityJoins);
  }

  private boolean isGamePropertyFilter(@NotNull FilterCondition condition) {
    return GAME_FILTERS.containsKey(condition.field().toLowerCase());
  }

  private boolean isEntityShorthandFilter(@NotNull FilterCondition condition) {
    if (condition.field().contains(".")) return false;
    return ENTITY_SHORTHAND_FIELDS.containsKey(condition.field().toLowerCase());
  }

  private boolean isEntityIdFilter(@NotNull FilterCondition condition) {
    if (condition.field().contains(".")) return false;
    return ENTITY_ID_FIELDS.contains(condition.field().toLowerCase());
  }

  private boolean isEntityPropertyFilter(@NotNull FilterCondition condition) {
    return condition.field().contains(".");
  }

  /**
   * Rewrites an entity shorthand condition to an entity property condition using the entity's
   * default field. For example, "player:Carlsen" becomes "player.name:Carlsen".
   */
  private @NotNull FilterCondition rewriteEntityNameShorthand(
      @NotNull FilterCondition condition) {
    String field = condition.field().toLowerCase();
    String builderKey = ENTITY_SHORTHAND_FIELDS.get(field);
    String defaultProp = ENTITY_BUILDERS.get(builderKey).defaultField();
    return new FilterCondition(
        field + "." + defaultProp, condition.operator(), condition.value(), condition.modifiers());
  }

  /** Returns the default field used when no field name is specified in a filter expression. */
  public @NotNull String defaultField() {
    return "player.name";
  }

  private static final Set<String> HIDDEN_FIELDS =
      Set.of("playerid", "tournamentid", "annotatorid", "sourceid", "teamid", "gametagid");

  /** Returns fields that are valid for filtering but should be hidden from the UI by default. */
  public @NotNull Set<String> hiddenFields() {
    return HIDDEN_FIELDS;
  }

  /** Returns a sorted list of all available field names, including entity sub-fields. */
  public @NotNull List<String> availableFields() {
    Set<String> fields = new TreeSet<>(GAME_FILTERS.keySet());
    fields.addAll(ENTITY_ID_FIELDS);
    fields.addAll(ENTITY_SHORTHAND_FIELDS.keySet());
    for (var entry : ENTITY_BUILDERS.entrySet()) {
      for (String prop : entry.getValue().availableFields()) {
        fields.add(entry.getKey() + "." + prop);
      }
    }
    // Add alias sub-fields (e.g., white.name, black.firstname)
    AbstractEntityQueryBuilder<?> playerBuilder = ENTITY_BUILDERS.get("player");
    for (String alias : PLAYER_POSITION_ALIASES.keySet()) {
      for (String prop : playerBuilder.availableFields()) {
        fields.add(alias + "." + prop);
      }
    }
    return List.copyOf(fields);
  }

  private static String availableFieldsSummary() {
    List<String> fields = new ArrayList<>(new TreeSet<>(GAME_FILTERS.keySet()));
    fields.addAll(new TreeSet<>(ENTITY_ID_FIELDS));
    fields.addAll(new TreeSet<>(ENTITY_SHORTHAND_FIELDS.keySet()));
    for (var entry : new TreeMap<>(ENTITY_BUILDERS).entrySet()) {
      for (String prop : new TreeSet<>(entry.getValue().availableFields())) {
        fields.add(entry.getKey() + "." + prop);
      }
    }
    return String.join(", ", fields);
  }

  private @NotNull GameFilter buildGameFilter(@NotNull FilterCondition condition) {
    var builder = GAME_FILTERS.get(condition.field().toLowerCase());
    if (builder == null) {
      throw new IllegalArgumentException(
          "Unknown game filter field: '"
              + condition.field()
              + "'. Available fields: "
              + String.join(", ", new TreeSet<>(GAME_FILTERS.keySet())));
    }
    return builder.apply(condition);
  }

  private @NotNull GameFilter buildEntityIdFilter(@NotNull FilterCondition condition) {
    int entityId = Integer.parseInt(condition.value());
    String field = condition.field().toLowerCase();

    return switch (field) {
      case "playerid" -> {
        String position = condition.modifiers().getOrDefault("position", "any");
        yield new PlayerFilter(entityId, parsePlayerPosition(position));
      }
      case "tournamentid" -> new TournamentFilter(entityId);
      case "annotatorid" -> new AnnotatorFilter(entityId);
      case "sourceid" -> new SourceFilter(entityId);
      case "teamid" -> {
        String position = condition.modifiers().getOrDefault("position", "any");
        yield new TeamFilter(entityId, parseTeamPosition(position));
      }
      case "gametagid" -> new GameTagFilter(entityId);
      default ->
          throw new IllegalArgumentException("Unknown entity ID filter: " + condition.field());
    };
  }

  /**
   * Builds a combined GameEntityJoin for all conditions targeting the same entity type. Delegates
   * filter building to the corresponding entity query builder, avoiding code duplication.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private @NotNull GameEntityJoin<?> buildCombinedEntityJoin(
      @NotNull Database database,
      @NotNull String entityType,
      @NotNull List<FilterCondition> conditions) {
    // Resolve player position aliases (white, black, winner, loser -> player)
    String resolvedEntityType =
        PLAYER_POSITION_ALIASES.containsKey(entityType) ? "player" : entityType;

    AbstractEntityQueryBuilder builder = ENTITY_BUILDERS.get(resolvedEntityType);
    if (builder == null) {
      throw new IllegalArgumentException(
          "Unknown entity type: '"
              + entityType
              + "'. Available entity types: "
              + String.join(", ", new TreeSet<>(ENTITY_BUILDERS.keySet())));
    }

    // Strip entity prefix from conditions (e.g., "tournament.title" -> "title")
    List<FilterCondition> strippedConditions =
        conditions.stream()
            .map(
                c ->
                    new FilterCondition(
                        c.field().split("\\.", 2)[1], c.operator(), c.value(), c.modifiers()))
            .toList();

    GameEntityJoinCondition joinCondition =
        extractJoinCondition(entityType, resolvedEntityType, conditions);

    EntityQuery entityQuery = builder.buildQuery(database, strippedConditions);
    return new GameEntityJoin<>(entityQuery, joinCondition);
  }

  private @Nullable GameEntityJoinCondition extractJoinCondition(
      @NotNull String entityType,
      @NotNull String resolvedEntityType,
      @NotNull List<FilterCondition> conditions) {
    // Check for alias-based position first (e.g., "white" -> WHITE)
    GameEntityJoinCondition aliasPosition = PLAYER_POSITION_ALIASES.get(entityType);
    if (aliasPosition != null) {
      return aliasPosition;
    }
    if (!"player".equals(resolvedEntityType) && !"team".equals(resolvedEntityType)) {
      return null;
    }
    GameEntityJoinCondition joinCondition = null;
    for (FilterCondition condition : conditions) {
      String position = condition.modifiers().getOrDefault("position", null);
      if (position != null) {
        joinCondition =
            "player".equals(resolvedEntityType)
                ? parsePlayerPosition(position)
                : parseTeamPosition(position);
      }
    }
    return joinCondition;
  }

  private static @NotNull GameFilter buildDateFilter(@NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      // Range operator: date..2020..2025
      String[] parts = condition.value().split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
      Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
      return new DateRangeFilter(from, to);
    } else {
      // Colon operator: may contain embedded range
      String value = condition.value();
      if (value.contains("..")) {
        // "2020..2025" or "2020.." or "..2025"
        String[] parts = value.split("\\.\\.", 2);
        Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
        Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
        return new DateRangeFilter(from, to);
      }
      // Single value: partial date expansion
      PartialDateParser.DateRange range = PartialDateParser.parse(value);
      return new DateRangeFilter(range.from(), range.to());
    }
  }

  private static @NotNull GameFilter buildRatingFilter(@NotNull FilterCondition condition) {
    String mode = condition.modifiers().getOrDefault("mode", "any");
    RatingRangeFilter.RatingColor ratingColor = parseRatingMode(mode);

    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 9999;
      return new RatingRangeFilter(min, max, ratingColor);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 9999;
        return new RatingRangeFilter(min, max, ratingColor);
      }
      int rating = Integer.parseInt(value);
      return new RatingRangeFilter(rating, rating, ratingColor);
    }
  }

  private static @NotNull GameFilter buildRoundFilter(@NotNull FilterCondition condition) {
    String value = condition.value();

    String subRoundStr = condition.modifiers().get("subround");
    if (subRoundStr != null) {
      int round = Integer.parseInt(value);
      int subRound = Integer.parseInt(subRoundStr);
      return new RoundFilter(round, subRound);
    }

    if (value.contains(".")) {
      String[] parts = value.split("\\.", 2);
      int round = Integer.parseInt(parts[0]);
      int subRound = Integer.parseInt(parts[1]);
      return new RoundFilter(round, subRound);
    }

    int round = Integer.parseInt(value);
    return new RoundFilter(round);
  }

  private static @NotNull GameFilter buildTypeFilter(@NotNull FilterCondition condition) {
    return switch (condition.value().toLowerCase()) {
      case "game" -> new IsGameFilter();
      case "text" -> new TextStorageFilter();
      default ->
          throw new IllegalArgumentException(
              "Unknown game type: " + condition.value() + ". Expected 'game' or 'text'");
    };
  }

  private static @NotNull GameEntityJoinCondition parsePlayerPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "both" -> GameEntityJoinCondition.BOTH;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default -> throw new IllegalArgumentException("Invalid player position: " + position);
    };
  }

  private static @NotNull GameEntityJoinCondition parseTeamPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default -> throw new IllegalArgumentException("Invalid team position: " + position);
    };
  }

  private static @NotNull RatingRangeFilter.RatingColor parseRatingMode(@NotNull String mode) {
    return switch (mode.toLowerCase()) {
      case "any" -> RatingRangeFilter.RatingColor.ANY;
      case "both" -> RatingRangeFilter.RatingColor.BOTH;
      case "white" -> RatingRangeFilter.RatingColor.WHITE;
      case "black" -> RatingRangeFilter.RatingColor.BLACK;
      case "average" -> RatingRangeFilter.RatingColor.AVERAGE;
      case "difference" -> RatingRangeFilter.RatingColor.DIFFERENCE;
      default -> throw new IllegalArgumentException("Invalid rating mode: " + mode);
    };
  }

  /** Parses a date string and returns the start of the range. */
  private static @NotNull Date parseDateStart(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).from();
  }

  /** Parses a date string and returns the end of the range. */
  private static @NotNull Date parseDateEnd(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).to();
  }

  private static @NotNull GameFilter buildBooleanFlagFilter(
      @NotNull FilterCondition condition, @NotNull GameFlagFilter.Flag flag) {
    boolean expected =
        switch (condition.value().toLowerCase()) {
          case "true" -> true;
          case "false" -> false;
          default ->
              throw new IllegalArgumentException(
                  "Invalid boolean value for "
                      + condition.field()
                      + ": "
                      + condition.value()
                      + ". Expected 'true' or 'false'");
        };
    return new GameFlagFilter(flag, expected);
  }

  private static @NotNull GameFilter buildMedalFilter(@NotNull FilterCondition condition) {
    boolean expected =
        switch (condition.value().toLowerCase()) {
          case "true" -> true;
          case "false" -> false;
          default ->
              throw new IllegalArgumentException(
                  "Invalid value for medals: "
                      + condition.value()
                      + ". Expected 'true' or 'false'. Use 'medal' for specific medals");
        };
    if (!expected) {
      throw new IllegalArgumentException(
          "medals:false is not supported. Use medal:<name> to match specific medals");
    }
    return new MedalFilter();
  }

  private static @NotNull GameFilter buildSpecificMedalFilter(
      @NotNull FilterCondition condition) {
    try {
      Medal medal = Medal.valueOf(condition.value().toUpperCase());
      return new MedalFilter(medal);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown medal: "
              + condition.value()
              + ". Available medals: "
              + String.join(
                  ", ",
                  java.util.Arrays.stream(Medal.values())
                      .map(m -> m.name().toLowerCase())
                      .toList()));
    }
  }

  private static @NotNull GameFilter buildMovesFilter(@NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty()
              ? Integer.parseInt(parts[1])
              : Integer.MAX_VALUE;
      return new MovesRangeFilter(min, max);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max =
            parts.length > 1 && !parts[1].isEmpty()
                ? Integer.parseInt(parts[1])
                : Integer.MAX_VALUE;
        return new MovesRangeFilter(min, max);
      }
      int moves = Integer.parseInt(value);
      return new MovesRangeFilter(moves, moves);
    }
  }
}
