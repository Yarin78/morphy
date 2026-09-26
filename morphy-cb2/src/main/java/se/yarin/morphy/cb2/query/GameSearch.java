package se.yarin.morphy.cb2.query;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.IntRange;
import se.yarin.morphy.api.query.PartialDateParser;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.cb2.DatabaseTransaction;
import se.yarin.morphy.cb2.entities.Entity;
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
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.morphy.chessbase.Medal;

/**
 * Finds the games of a v2 database matching filter conditions, with the same fields as a v1
 * database.
 *
 * <p>Conditions on entities ({@code player:Carlsen}, {@code tournament.title:Wijk}) are resolved
 * by finding the matching entities in their sort order and taking the games from their game
 * lists; the other conditions are tested on the game records. Without entity conditions every
 * record is scanned. Results are sorted in memory.
 */
public final class GameSearch {

  public static final String DEFAULT_FIELD = "player.name";

  /** The positions an entity can have in a game, for players and teams. */
  private enum Position {
    ANY,
    BOTH,
    WHITE,
    BLACK,
    WINNER,
    LOSER
  }

  private static final Map<String, EntityKind<?>> ENTITY_FIELDS = new LinkedHashMap<>();
  private static final Map<String, Position> PLAYER_ALIASES =
      Map.of(
          "white", Position.WHITE,
          "black", Position.BLACK,
          "winner", Position.WINNER,
          "loser", Position.LOSER);
  private static final Map<String, EntityKind<?>> ID_FIELDS = new LinkedHashMap<>();

  static {
    ENTITY_FIELDS.put("player", EntityKind.PLAYER);
    for (String alias : PLAYER_ALIASES.keySet()) {
      ENTITY_FIELDS.put(alias, EntityKind.PLAYER);
    }
    ENTITY_FIELDS.put("tournament", EntityKind.TOURNAMENT);
    ENTITY_FIELDS.put("annotator", EntityKind.ANNOTATOR);
    ENTITY_FIELDS.put("source", EntityKind.SOURCE);
    ENTITY_FIELDS.put("team", EntityKind.TEAM);
    ENTITY_FIELDS.put("gametag", EntityKind.GAME_TAG);
    ID_FIELDS.put("playerid", EntityKind.PLAYER);
    ID_FIELDS.put("tournamentid", EntityKind.TOURNAMENT);
    ID_FIELDS.put("annotatorid", EntityKind.ANNOTATOR);
    ID_FIELDS.put("sourceid", EntityKind.SOURCE);
    ID_FIELDS.put("teamid", EntityKind.TEAM);
    ID_FIELDS.put("gametagid", EntityKind.GAME_TAG);
  }

  private static final Map<String, Function<FilterCondition, Predicate<GameRecord>>> GAME_FILTERS =
      new LinkedHashMap<>();

  static {
    GAME_FILTERS.put("result", GameSearch::resultFilter);
    GAME_FILTERS.put(
        "date",
        c -> {
          PartialDateParser.DateRange range = PartialDateParser.parseRange(c);
          return r -> r instanceof GameHeader g && EntitySearch.inRange(Dates.decode(g.playedDate()), range);
        });
    GAME_FILTERS.put("rating", GameSearch::ratingFilter);
    GAME_FILTERS.put("eco", GameSearch::ecoFilter);
    GAME_FILTERS.put("round", GameSearch::roundFilter);
    GAME_FILTERS.put(
        "type",
        c ->
            switch (c.value().toLowerCase(Locale.ROOT)) {
              case "game" -> r -> r instanceof GameHeader;
              case "text" -> r -> r instanceof TextHeader;
              case "analysis" -> r -> r instanceof AnalysisHeader;
              default ->
                  throw new IllegalArgumentException(
                      "Unknown game type: " + c.value() + ". Expected 'game', 'text' or 'analysis'");
            });
    flag("setup", EnumSet.of(GameHeaderFlags.SETUP_POSITION));
    flag("variations", EnumSet.of(GameHeaderFlags.VARIATIONS));
    flag("commentary", EnumSet.of(GameHeaderFlags.COMMENTARY));
    flag("symbols", EnumSet.of(GameHeaderFlags.SYMBOLS));
    flag("training", EnumSet.of(GameHeaderFlags.TRAINING));
    flag(
        "annotations",
        EnumSet.complementOf(
            EnumSet.of(GameHeaderFlags.SETUP_POSITION, GameHeaderFlags.VARIATIONS, GameHeaderFlags.UNORTHODOX)));
    flag("correspondence", EnumSet.of(GameHeaderFlags.CORRESPONDENCE_HEADER));
    flag(
        "media",
        EnumSet.of(
            GameHeaderFlags.EMBEDDED_AUDIO,
            GameHeaderFlags.EMBEDDED_PICTURE,
            GameHeaderFlags.EMBEDDED_VIDEO,
            GameHeaderFlags.ANNO_TYPE_1A));
    GAME_FILTERS.put(
        "deleted", c -> r -> r.deleted() == parseBoolean(c));
    GAME_FILTERS.put(
        "medals",
        c -> {
          boolean expected = parseBoolean(c);
          return r -> (r instanceof GameHeader g && g.medals() != 0) == expected;
        });
    GAME_FILTERS.put(
        "medal",
        c -> {
          Medal medal = medal(c.value());
          return r -> r instanceof GameHeader g && Medal.decode(g.medals()).contains(medal);
        });
    GAME_FILTERS.put(
        "moves",
        c -> {
          IntRange range = IntRange.parse(c, 9999);
          return r -> r instanceof GameHeader g && g.moveCount() >= range.min() && g.moveCount() <= range.max();
        });
  }

  private static void flag(String name, EnumSet<GameHeaderFlags> flags) {
    int mask = GameHeaderFlags.encodeFlags(flags);
    GAME_FILTERS.put(
        name,
        c -> {
          boolean expected = parseBoolean(c);
          return r -> (r instanceof GameHeader g && (g.flags() & mask) != 0) == expected;
        });
  }

  private GameSearch() {}

  // ── Schema ───────────────────────────────────────────────────────────────

  private static final Set<String> HIDDEN_FIELDS = ID_FIELDS.keySet();

  /** The fields games can be filtered and sorted on. */
  public static @NotNull SearchSchema schema() {
    Set<String> fields = new TreeSet<>(GAME_FILTERS.keySet());
    fields.addAll(ID_FIELDS.keySet());
    for (Map.Entry<String, EntityKind<?>> e : ENTITY_FIELDS.entrySet()) {
      fields.add(e.getKey());
      for (String field : EntitySearch.of(e.getValue()).fields()) {
        fields.add(e.getKey() + "." + field);
      }
    }
    List<SearchSchema.SortField> sorts = new ArrayList<>();
    for (String name : SORT_NAMES) {
      sorts.add(new SearchSchema.SortField(name, defaultDirection(name)));
    }
    return new SearchSchema(DEFAULT_FIELD, List.copyOf(fields), Set.copyOf(HIDDEN_FIELDS), sorts);
  }

  // ── Finding ──────────────────────────────────────────────────────────────

  /**
   * Finds the games matching conditions.
   *
   * @param txn the transaction to read through
   * @param conditions the conditions, all of which must hold
   * @param sort the order; natural is by id
   * @return the ids of the matching games, in order
   * @throws IllegalArgumentException if a field is unknown or a value invalid
   */
  public static @NotNull List<Integer> find(
      @NotNull DatabaseTransaction txn, @NotNull List<FilterCondition> conditions, @NotNull Sort sort) {
    EntityCache cache = new EntityCache(txn);
    Predicate<GameRecord> filter = r -> true;
    BitSet candidates = null;
    Map<String, List<FilterCondition>> groups = new LinkedHashMap<>();

    for (FilterCondition condition : conditions) {
      String field = condition.field().toLowerCase(Locale.ROOT);
      try {
        if (GAME_FILTERS.containsKey(field)) {
          filter = filter.and(GAME_FILTERS.get(field).apply(condition));
        } else if (ID_FIELDS.containsKey(field)) {
          EntityKind<?> kind = ID_FIELDS.get(field);
          long id = Long.parseLong(condition.value());
          Position position = position(kind, condition, null);
          filter = filter.and(r -> refers(r, kind, Set.of(id), position));
          candidates = intersect(candidates, gamesOf(txn, kind, Set.of(id)));
        } else {
          String prefix = field.contains(".") ? field.substring(0, field.indexOf('.')) : field;
          if (!ENTITY_FIELDS.containsKey(prefix)) {
            throw new IllegalArgumentException(
                "Unknown filter field: '" + condition.field() + "'. Available fields: "
                    + String.join(", ", schema().fields()));
          }
          String property =
              field.contains(".")
                  ? condition.field().substring(field.indexOf('.') + 1)
                  : EntitySearch.of(ENTITY_FIELDS.get(prefix)).defaultField();
          groups
              .computeIfAbsent(prefix, k -> new ArrayList<>())
              .add(new FilterCondition(property, condition.operator(), condition.value(), condition.modifiers()));
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid value in " + condition, e);
      }
    }

    for (Map.Entry<String, List<FilterCondition>> group : groups.entrySet()) {
      EntityKind<?> kind = ENTITY_FIELDS.get(group.getKey());
      EntitySearch search = EntitySearch.of(kind);
      Predicate<Entity> entityFilter = search.filter(group.getValue());
      Set<Long> ids = new HashSet<>();
      for (long id : txn.sortedIds(search.index())) {
        Entity entity = cache.entity(search.type(), id);
        if (entity != null && entityFilter.test(entity)) {
          ids.add(id);
        }
      }
      Position alias = PLAYER_ALIASES.get(group.getKey());
      Position position = alias != null ? alias : position(kind, null, group.getValue());
      filter = filter.and(r -> refers(r, kind, ids, position));
      candidates = intersect(candidates, gamesOf(txn, kind, ids));
    }

    List<GameRecord> matches = new ArrayList<>();
    if (candidates == null) {
      for (int id = 1; id <= txn.count(); id++) {
        GameRecord record = txn.record(id);
        if (filter.test(record)) {
          matches.add(record);
        }
      }
    } else {
      for (int id = candidates.nextSetBit(1); id >= 0 && id <= txn.count(); id = candidates.nextSetBit(id + 1)) {
        GameRecord record = txn.record(id);
        if (filter.test(record)) {
          matches.add(record);
        }
      }
    }
    Comparator<GameRecord> comparator = comparator(sort, cache);
    if (comparator != null) {
      matches.sort(comparator);
    }
    return matches.stream().map(GameRecord::id).toList();
  }

  private static BitSet intersect(@Nullable BitSet a, BitSet b) {
    if (a == null) {
      return b;
    }
    a.and(b);
    return a;
  }

  private static BitSet gamesOf(DatabaseTransaction txn, EntityKind<?> kind, Set<Long> ids) {
    EntitySearch search = EntitySearch.of(kind);
    BitSet games = new BitSet();
    for (long id : ids) {
      for (int game : txn.gameIds(id, search.role())) {
        games.set(game);
      }
    }
    return games;
  }

  private static Position position(
      EntityKind<?> kind, @Nullable FilterCondition condition, @Nullable List<FilterCondition> group) {
    String value = null;
    if (condition != null) {
      value = condition.modifiers().get("position");
    } else if (group != null) {
      for (FilterCondition c : group) {
        value = c.modifiers().getOrDefault("position", value);
      }
    }
    if (value == null) {
      return Position.ANY;
    }
    if (kind != EntityKind.PLAYER && kind != EntityKind.TEAM) {
      throw new IllegalArgumentException("A position applies to players and teams only");
    }
    try {
      Position position = Position.valueOf(value.toUpperCase(Locale.ROOT));
      if (position == Position.BOTH && kind == EntityKind.TEAM) {
        throw new IllegalArgumentException("Invalid team position: " + value);
      }
      return position;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid position: " + value, e);
    }
  }

  /** Whether a record refers to one of the entities in the given position. */
  private static boolean refers(GameRecord r, EntityKind<?> kind, Set<Long> ids, Position position) {
    if (kind == EntityKind.PLAYER || kind == EntityKind.TEAM) {
      if (!(r instanceof GameHeader g)) {
        return false;
      }
      boolean team = kind == EntityKind.TEAM;
      boolean white = ids.contains(team ? g.whiteTeamId() : g.whiteId());
      boolean black = ids.contains(team ? g.blackTeamId() : g.blackId());
      boolean whiteWon =
          g.result() == GameResult.WHITE_WINS.ordinal()
              || g.result() == GameResult.WHITE_WINS_ON_FORFEIT.ordinal();
      boolean blackWon =
          g.result() == GameResult.BLACK_WINS.ordinal()
              || g.result() == GameResult.BLACK_WINS_ON_FORFEIT.ordinal();
      return switch (position) {
        case ANY -> white || black;
        case BOTH -> white && black;
        case WHITE -> white;
        case BLACK -> black;
        case WINNER -> (white && whiteWon) || (black && blackWon);
        case LOSER -> (white && blackWon) || (black && whiteWon);
      };
    }
    long id =
        switch (r) {
          case GameHeader g ->
              kind == EntityKind.TOURNAMENT ? g.tournamentId()
                  : kind == EntityKind.ANNOTATOR ? g.annotatorId()
                  : kind == EntityKind.SOURCE ? g.sourceId()
                  : g.gameTagId();
          case TextHeader t ->
              kind == EntityKind.TOURNAMENT ? t.tournamentId()
                  : kind == EntityKind.ANNOTATOR ? t.annotatorId()
                  : kind == EntityKind.SOURCE ? t.sourceId()
                  : -1;
          case AnalysisHeader a ->
              kind == EntityKind.ANNOTATOR ? a.annotatorId()
                  : kind == EntityKind.SOURCE ? a.sourceId()
                  : -1;
        };
    return ids.contains(id);
  }

  // ── Game filters ─────────────────────────────────────────────────────────

  private static Predicate<GameRecord> resultFilter(FilterCondition c) {
    GameResult result =
        switch (c.value().toLowerCase(Locale.ROOT)) {
          case "1-0", "white" -> GameResult.WHITE_WINS;
          case "i-o", "+:-" -> GameResult.WHITE_WINS_ON_FORFEIT;
          case "0-1", "black" -> GameResult.BLACK_WINS;
          case "o-i", "-:+" -> GameResult.BLACK_WINS_ON_FORFEIT;
          case "draw", "1/2", "1/2-1/2", "½-½" -> GameResult.DRAW;
          case "=:=" -> GameResult.DRAW_ON_FORFEIT;
          case "0-0", "o-o" -> GameResult.BOTH_LOST;
          case "*", "line" -> GameResult.NOT_FINISHED;
          default -> throw new IllegalArgumentException("Invalid result: " + c.value());
        };
    return r -> r instanceof GameHeader g && g.result() == result.ordinal();
  }

  private static Predicate<GameRecord> ratingFilter(FilterCondition c) {
    IntRange range = IntRange.parse(c, 9999);
    String mode = c.modifiers().getOrDefault("mode", "any").toLowerCase(Locale.ROOT);
    Predicate<Integer> in = elo -> elo >= range.min() && elo <= range.max();
    return switch (mode) {
      case "any" -> r -> r instanceof GameHeader g && (in.test(g.whiteElo()) || in.test(g.blackElo()));
      case "both" -> r -> r instanceof GameHeader g && in.test(g.whiteElo()) && in.test(g.blackElo());
      case "white" -> r -> r instanceof GameHeader g && in.test(g.whiteElo());
      case "black" -> r -> r instanceof GameHeader g && in.test(g.blackElo());
      case "average" ->
          r -> r instanceof GameHeader g && in.test((g.whiteElo() + g.blackElo()) / 2);
      default -> throw new IllegalArgumentException("Invalid rating mode: " + mode);
    };
  }

  private static Predicate<GameRecord> ecoFilter(FilterCondition c) {
    String value = c.value().toUpperCase(Locale.ROOT);
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int from = parts[0].isEmpty() ? 0 : new Eco(parts[0]).getInt();
      int to = parts[1].isEmpty() ? 499 : new Eco(parts[1]).getInt();
      return r -> {
        Eco eco = ecoOf(r);
        return eco.isSet() && eco.getInt() >= from && eco.getInt() <= to;
      };
    }
    if (value.endsWith("*")) {
      String start = value.substring(0, value.length() - 1);
      return r -> {
        Eco eco = ecoOf(r);
        return eco.isSet() && eco.toString().startsWith(start);
      };
    }
    Eco target = new Eco(value);
    return r -> {
      Eco eco = ecoOf(r);
      return eco.isSet() && eco.getInt() == target.getInt();
    };
  }

  private static Eco ecoOf(GameRecord r) {
    return r instanceof GameHeader g && !g.chess960() ? EcoField.eco(g.eco()) : Eco.unset();
  }

  private static Predicate<GameRecord> roundFilter(FilterCondition c) {
    String value = c.value();
    Integer subRound = null;
    if (c.modifiers().containsKey("subround")) {
      subRound = Integer.parseInt(c.modifiers().get("subround"));
    } else if (value.contains(".")) {
      String[] parts = value.split("\\.", 2);
      value = parts[0];
      subRound = Integer.parseInt(parts[1]);
    }
    int round = Integer.parseInt(value);
    Integer sub = subRound;
    return r -> r instanceof GameHeader g && g.round() == round && (sub == null || g.subRound() == sub);
  }

  private static boolean parseBoolean(FilterCondition c) {
    return switch (c.value().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> throw new IllegalArgumentException("Invalid boolean value: " + c.value());
    };
  }

  private static Medal medal(String name) {
    for (Medal medal : Medal.values()) {
      if (medal.name().equalsIgnoreCase(name.replace('-', '_'))) {
        return medal;
      }
    }
    throw new IllegalArgumentException("Unknown medal: " + name);
  }

  // ── Sorting ──────────────────────────────────────────────────────────────

  private static final List<String> SORT_NAMES =
      List.of(
          "id", "playedDate", "whitePlayerName", "blackPlayerName", "result", "eco", "round",
          "tournament", "source", "annotator", "gameTag", "whiteElo", "blackElo", "noMoves",
          "whiteTeam", "blackTeam", "setupPosition", "medals", "gameVersion", "creationTimestamp",
          "lastChanged", "playedYear", "eloMax");

  private static @Nullable Comparator<GameRecord> comparator(Sort sort, EntityCache cache) {
    if (sort.isNatural()) {
      return null;
    }
    Comparator<GameRecord> result = null;
    for (Sort.Key key : sort.keys()) {
      Comparator<GameRecord> c = sortField(key.field(), cache);
      Sort.Direction direction =
          key.direction() == null ? defaultDirection(key.field()) : key.direction();
      if (direction == Sort.Direction.DESCENDING) {
        c = c.reversed();
      }
      result = result == null ? c : result.thenComparing(c);
    }
    return result.thenComparingInt(GameRecord::id);
  }

  // The fields sorted descending unless a direction is given, as for a v1 database
  private static final Set<String> DESCENDING =
      Set.of(
          "playeddate", "date", "whiteelo", "blackelo", "nomoves", "setupposition", "medals",
          "gameversion", "creationtimestamp", "lastchanged", "playedyear", "elomax");

  private static Sort.Direction defaultDirection(String name) {
    return DESCENDING.contains(name.toLowerCase(Locale.ROOT))
        ? Sort.Direction.DESCENDING
        : Sort.Direction.ASCENDING;
  }

  private static Comparator<GameRecord> sortField(String name, EntityCache cache) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "id" -> Comparator.comparingInt(GameRecord::id);
      case "playeddate", "date" -> byGameInt(GameHeader::playedDate);
      case "playedyear" -> byGameInt(g -> g.playedDate() >> 9);
      case "whiteplayername" -> byName(r -> r instanceof GameHeader g ? cache.name(EntityType.PLAYER, g.whiteId()) : "");
      case "blackplayername" -> byName(r -> r instanceof GameHeader g ? cache.name(EntityType.PLAYER, g.blackId()) : "");
      case "result" -> byGameInt(GameHeader::result);
      case "eco" -> byGameInt(g -> g.chess960() ? -1 : g.eco());
      case "round" -> byGameInt(g -> g.round() * 1000 + g.subRound());
      case "tournament" -> byName(r -> cache.name(EntityType.TOURNAMENT, tournamentId(r)));
      case "source" -> byName(r -> cache.name(EntityType.SOURCE, sourceId(r)));
      case "annotator" -> byName(r -> cache.name(EntityType.PLAYER, annotatorId(r)));
      case "gametag" -> byName(r -> cache.name(EntityType.GAME_TAG, gameTagId(r)));
      case "whiteelo" -> byGameInt(GameHeader::whiteElo);
      case "blackelo" -> byGameInt(GameHeader::blackElo);
      case "elomax" -> byGameInt(g -> Math.max(g.whiteElo(), g.blackElo()));
      case "nomoves" -> byGameInt(GameHeader::moveCount);
      case "whiteteam" -> byName(r -> r instanceof GameHeader g ? cache.name(EntityType.TEAM, g.whiteTeamId()) : "");
      case "blackteam" -> byName(r -> r instanceof GameHeader g ? cache.name(EntityType.TEAM, g.blackTeamId()) : "");
      case "setupposition" -> byGameInt(g -> g.flags() & 1);
      case "medals" -> byGameInt(g -> Integer.bitCount(g.medals()));
      case "gameversion" -> byGameInt(GameHeader::version);
      case "creationtimestamp" -> Comparator.comparingLong(r -> r instanceof GameHeader g ? g.creationTimestamp() : 0);
      case "lastchanged" -> Comparator.comparingLong(r -> r instanceof GameHeader g ? g.lastChangedTimestamp() : 0);
      default ->
          throw new IllegalArgumentException(
              "Unknown sort field: '" + name + "'. Available fields: " + String.join(", ", SORT_NAMES));
    };
  }

  private static Comparator<GameRecord> byGameInt(ToIntFunction<GameHeader> key) {
    return Comparator.comparingInt(r -> r instanceof GameHeader g ? key.applyAsInt(g) : Integer.MIN_VALUE);
  }

  private static Comparator<GameRecord> byName(Function<GameRecord, String> key) {
    return Comparator.comparing(key, String.CASE_INSENSITIVE_ORDER);
  }

  private static long tournamentId(GameRecord r) {
    return switch (r) {
      case GameHeader g -> g.tournamentId();
      case TextHeader t -> t.tournamentId();
      case AnalysisHeader a -> -1;
    };
  }

  private static long sourceId(GameRecord r) {
    return switch (r) {
      case GameHeader g -> g.sourceId();
      case TextHeader t -> t.sourceId();
      case AnalysisHeader a -> a.sourceId();
    };
  }

  private static long annotatorId(GameRecord r) {
    return switch (r) {
      case GameHeader g -> g.annotatorId();
      case TextHeader t -> t.annotatorId();
      case AnalysisHeader a -> a.annotatorId();
    };
  }

  private static long gameTagId(GameRecord r) {
    return switch (r) {
      case GameHeader g -> g.gameTagId();
      case TextHeader t -> t.titleId();
      case AnalysisHeader a -> a.titleId();
    };
  }

  /** Entities read during one search, each read once. */
  private static final class EntityCache {
    private final DatabaseTransaction txn;
    private final Map<Long, Entity> entities = new HashMap<>();

    EntityCache(DatabaseTransaction txn) {
      this.txn = txn;
    }

    @Nullable
    Entity entity(EntityType type, long id) {
      if (id < 0) {
        return null;
      }
      long key = ((long) type.ordinal() << 48) | id;
      return entities.computeIfAbsent(key, k -> txn.entity(type, id));
    }

    String name(EntityType type, long id) {
      return switch (entity(type, id)) {
        case Player p -> p.fullName();
        case Tournament t -> t.title();
        case Source s -> s.title();
        case Team t -> t.title();
        case GameTag g -> g.title();
        case null -> "";
      };
    }
  }
}
