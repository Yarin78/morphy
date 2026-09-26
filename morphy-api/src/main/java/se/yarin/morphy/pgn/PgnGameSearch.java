package se.yarin.morphy.pgn;

import java.util.ArrayList;
import java.util.Comparator;
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
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.IntRange;
import se.yarin.morphy.api.query.PartialDateParser;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;

/**
 * Filters and sorts games of a PGN file, which have no entities to search but only the values in
 * their headers. The fields and their meaning are those of the ChessBase databases, as far as a
 * PGN game has them, so that a query means the same whichever format it runs against.
 *
 * <p>Works on the {@link GameDto} of a game read without its moves.
 */
final class PgnGameSearch {

  static final String DEFAULT_FIELD = "player.name";

  /** The sides a player or team can have in a game. */
  private enum Position {
    ANY,
    BOTH,
    WHITE,
    BLACK,
    WINNER,
    LOSER
  }

  private static final Map<String, Position> PLAYER_ALIASES =
      Map.of(
          "white", Position.WHITE,
          "black", Position.BLACK,
          "winner", Position.WINNER,
          "loser", Position.LOSER);

  private static final Map<String, Function<FilterCondition, Predicate<GameDto>>> FILTERS =
      new LinkedHashMap<>();

  static {
    for (String prefix : List.of("player", "white", "black", "winner", "loser")) {
      Position fixed = PLAYER_ALIASES.get(prefix);
      FILTERS.put(prefix, c -> player(c, fixed, PgnGameSearch::playerName));
      FILTERS.put(prefix + ".name", c -> player(c, fixed, PgnGameSearch::playerName));
      FILTERS.put(prefix + ".firstname", c -> player(c, fixed, PgnGameSearch::firstName));
      FILTERS.put(prefix + ".lastname", c -> player(c, fixed, PgnGameSearch::lastName));
    }
    FILTERS.put("team", PgnGameSearch::team);
    FILTERS.put("team.name", PgnGameSearch::team);
    FILTERS.put("tournament", c -> tournamentTitle(c));
    FILTERS.put("tournament.title", c -> tournamentTitle(c));
    FILTERS.put("tournament.place", c -> prefixAny(c, g -> g.tournament() == null ? null : g.tournament().place()));
    FILTERS.put(
        "tournament.nation",
        c -> g -> g.tournament() != null && c.value().equalsIgnoreCase(g.tournament().nation()));
    FILTERS.put(
        "tournament.type",
        c -> g -> g.tournament() != null && c.value().equalsIgnoreCase(g.tournament().type()));
    FILTERS.put(
        "tournament.time",
        c -> g -> g.tournament() != null && c.value().equalsIgnoreCase(g.tournament().timeControl()));
    FILTERS.put(
        "tournament.category",
        c -> intFilter(c, g -> g.tournament() == null ? null : g.tournament().category(), 99));
    FILTERS.put(
        "tournament.rounds",
        c -> intFilter(c, g -> g.tournament() == null ? null : g.tournament().rounds(), 999));
    FILTERS.put(
        "tournament.date",
        c -> dateFilter(c, g -> g.tournament() == null ? null : g.tournament().startDate()));
    FILTERS.put(
        "tournament.year",
        c ->
            intFilter(
                c,
                g ->
                    g.tournament() == null || g.tournament().startDate() == null
                            || g.tournament().startDate().isUnset()
                        ? null
                        : g.tournament().startDate().year(),
                9999));
    FILTERS.put("annotator", c -> prefixAny(c, g -> g.annotator() == null ? null : g.annotator().name()));
    FILTERS.put("annotator.name", c -> prefixAny(c, g -> g.annotator() == null ? null : g.annotator().name()));
    FILTERS.put("source", c -> prefixAny(c, g -> g.source() == null ? null : g.source().title()));
    FILTERS.put("source.title", c -> prefixAny(c, g -> g.source() == null ? null : g.source().title()));
    FILTERS.put(
        "source.publisher", c -> prefixAny(c, g -> g.source() == null ? null : g.source().publisher()));
    FILTERS.put("gametag", c -> prefixAny(c, g -> g.gameTag() == null ? null : g.gameTag().englishTitle()));

    FILTERS.put("result", PgnGameSearch::resultFilter);
    FILTERS.put("date", c -> dateFilter(c, GameDto::date));
    FILTERS.put("rating", PgnGameSearch::ratingFilter);
    FILTERS.put("eco", PgnGameSearch::ecoFilter);
    FILTERS.put("round", PgnGameSearch::roundFilter);
    FILTERS.put(
        "type",
        c ->
            switch (c.value().toLowerCase(Locale.ROOT)) {
              case "game" -> g -> true;
              case "text", "analysis" -> g -> false;
              default ->
                  throw new IllegalArgumentException(
                      "Unknown game type: " + c.value() + ". Expected 'game', 'text' or 'analysis'");
            });
    FILTERS.put("deleted", c -> parseBoolean(c) ? g -> false : g -> true);
    FILTERS.put(
        "setup",
        c -> {
          boolean expected = parseBoolean(c);
          return g -> Boolean.TRUE.equals(g.setupPosition()) == expected;
        });
  }

  private PgnGameSearch() {}

  // ── Schema ───────────────────────────────────────────────────────────────

  private static final List<String> SORT_NAMES =
      List.of(
          "id", "playedDate", "whitePlayerName", "blackPlayerName", "result", "eco", "round",
          "tournament", "source", "annotator", "gameTag", "whiteElo", "blackElo", "whiteTeam",
          "blackTeam", "setupPosition", "playedYear", "eloMax");

  // The fields sorted descending unless a direction is given, as for the ChessBase formats
  private static final Set<String> DESCENDING =
      Set.of("playeddate", "date", "whiteelo", "blackelo", "setupposition", "playedyear", "elomax");

  /** The fields games can be filtered and sorted on. */
  static @NotNull SearchSchema schema() {
    List<SearchSchema.SortField> sorts = new ArrayList<>();
    for (String name : SORT_NAMES) {
      sorts.add(new SearchSchema.SortField(name, defaultDirection(name)));
    }
    return new SearchSchema(DEFAULT_FIELD, List.copyOf(new TreeSet<>(FILTERS.keySet())), Set.of(), sorts);
  }

  // ── Filtering ────────────────────────────────────────────────────────────

  /**
   * Builds the test for conditions, all of which must hold.
   *
   * @throws IllegalArgumentException if a field is unknown or a value invalid
   */
  static @NotNull Predicate<GameDto> filter(@NotNull List<FilterCondition> conditions) {
    Predicate<GameDto> filter = g -> true;
    for (FilterCondition condition : conditions) {
      Function<FilterCondition, Predicate<GameDto>> factory =
          FILTERS.get(condition.field().toLowerCase(Locale.ROOT));
      if (factory == null) {
        throw new IllegalArgumentException(
            "Unknown filter field: '" + condition.field() + "'. Available fields: "
                + String.join(", ", schema().fields()));
      }
      try {
        filter = filter.and(factory.apply(condition));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid value in " + condition, e);
      }
    }
    return filter;
  }

  private static Predicate<GameDto> player(
      FilterCondition c, @Nullable Position fixed, Function<String, Predicate<PlayerDto>> match) {
    Position position = fixed != null ? fixed : position(c);
    Predicate<PlayerDto> anyOf = alternatives(c.value(), match);
    return g -> {
      boolean white = g.whitePlayer() != null && anyOf.test(g.whitePlayer());
      boolean black = g.blackPlayer() != null && anyOf.test(g.blackPlayer());
      return sides(g, white, black, position);
    };
  }

  private static Predicate<GameDto> team(FilterCondition c) {
    Position position = position(c);
    if (position == Position.BOTH) {
      throw new IllegalArgumentException("Invalid team position: both");
    }
    Predicate<String> anyOf = alternatives(c.value(), v -> title -> prefix(title, v));
    return g -> {
      boolean white = g.whiteTeam() != null && anyOf.test(g.whiteTeam().title());
      boolean black = g.blackTeam() != null && anyOf.test(g.blackTeam().title());
      return sides(g, white, black, position);
    };
  }

  private static Position position(FilterCondition c) {
    String value = c.modifiers().get("position");
    if (value == null) {
      return Position.ANY;
    }
    try {
      return Position.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid position: " + value, e);
    }
  }

  private static boolean sides(GameDto g, boolean white, boolean black, Position position) {
    boolean whiteWon =
        g.result() == GameResult.WHITE_WINS || g.result() == GameResult.WHITE_WINS_ON_FORFEIT;
    boolean blackWon =
        g.result() == GameResult.BLACK_WINS || g.result() == GameResult.BLACK_WINS_ON_FORFEIT;
    return switch (position) {
      case ANY -> white || black;
      case BOTH -> white && black;
      case WHITE -> white;
      case BLACK -> black;
      case WINNER -> (white && whiteWon) || (black && blackWon);
      case LOSER -> (white && blackWon) || (black && whiteWon);
    };
  }

  /** Matches "Last, First" or just a last name, each part as a prefix. */
  private static Predicate<PlayerDto> playerName(String value) {
    int comma = value.indexOf(',');
    String last = (comma < 0 ? value : value.substring(0, comma)).strip();
    String first = comma < 0 ? "" : value.substring(comma + 1).strip();
    return p -> prefix(p.lastName(), last) && prefix(p.firstName(), first);
  }

  private static Predicate<PlayerDto> firstName(String value) {
    return p -> prefix(p.firstName(), value);
  }

  private static Predicate<PlayerDto> lastName(String value) {
    return p -> prefix(p.lastName(), value);
  }

  private static Predicate<GameDto> tournamentTitle(FilterCondition c) {
    return prefixAny(c, g -> g.tournament() == null ? null : g.tournament().title());
  }

  /** A filter matching the start of a text, any of the alternatives separated by {@code |}. */
  private static Predicate<GameDto> prefixAny(
      FilterCondition c, Function<GameDto, @Nullable String> text) {
    Predicate<String> anyOf = alternatives(c.value(), v -> t -> prefix(t, v));
    return g -> anyOf.test(text.apply(g));
  }

  private static <T> Predicate<T> alternatives(String value, Function<String, Predicate<T>> one) {
    Predicate<T> result = x -> false;
    for (String alternative : value.split("\\|")) {
      result = result.or(one.apply(alternative.strip()));
    }
    return result;
  }

  /** Whether a text starts with a prefix, ignoring case. */
  private static boolean prefix(@Nullable String text, String prefix) {
    return prefix.isEmpty() || (text != null && text.regionMatches(true, 0, prefix, 0, prefix.length()));
  }

  private static Predicate<GameDto> resultFilter(FilterCondition c) {
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
    return g -> g.result() == result;
  }

  private static Predicate<GameDto> ratingFilter(FilterCondition c) {
    IntRange range = IntRange.parse(c, 9999);
    String mode = c.modifiers().getOrDefault("mode", "any").toLowerCase(Locale.ROOT);
    Predicate<Integer> in = elo -> elo != null && elo >= range.min() && elo <= range.max();
    return switch (mode) {
      case "any" -> g -> in.test(g.whiteElo()) || in.test(g.blackElo());
      case "both" -> g -> in.test(g.whiteElo()) && in.test(g.blackElo());
      case "white" -> g -> in.test(g.whiteElo());
      case "black" -> g -> in.test(g.blackElo());
      case "average" ->
          g ->
              g.whiteElo() != null
                  && g.blackElo() != null
                  && in.test((g.whiteElo() + g.blackElo()) / 2);
      default -> throw new IllegalArgumentException("Invalid rating mode: " + mode);
    };
  }

  private static Predicate<GameDto> ecoFilter(FilterCondition c) {
    String value = c.value().toUpperCase(Locale.ROOT);
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int from = parts[0].isEmpty() ? 0 : new Eco(parts[0]).getInt();
      int to = parts[1].isEmpty() ? 499 : new Eco(parts[1]).getInt();
      return g -> {
        Eco eco = ecoOf(g);
        return eco.isSet() && eco.getInt() >= from && eco.getInt() <= to;
      };
    }
    if (value.endsWith("*")) {
      String start = value.substring(0, value.length() - 1);
      return g -> {
        Eco eco = ecoOf(g);
        return eco.isSet() && eco.toString().startsWith(start);
      };
    }
    Eco target = new Eco(value);
    return g -> {
      Eco eco = ecoOf(g);
      return eco.isSet() && eco.getInt() == target.getInt();
    };
  }

  private static Eco ecoOf(GameDto g) {
    if (g.eco() == null) {
      return Eco.unset();
    }
    try {
      return new Eco(g.eco());
    } catch (IllegalArgumentException e) {
      return Eco.unset();
    }
  }

  private static Predicate<GameDto> roundFilter(FilterCondition c) {
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
    return g ->
        g.round() != null && g.round() == round
            && (sub == null || (g.subRound() != null && g.subRound().equals(sub)));
  }

  private static Predicate<GameDto> intFilter(
      FilterCondition c, Function<GameDto, @Nullable Integer> value, int max) {
    IntRange range = IntRange.parse(c, max);
    return g -> {
      Integer v = value.apply(g);
      return v != null && v >= range.min() && v <= range.max();
    };
  }

  private static Predicate<GameDto> dateFilter(
      FilterCondition c, Function<GameDto, @Nullable Date> value) {
    PartialDateParser.DateRange range = PartialDateParser.parseRange(c);
    return g -> {
      Date date = value.apply(g);
      return date != null
          && !date.isUnset()
          && date.compareTo(range.from()) >= 0
          && date.compareTo(range.to()) <= 0;
    };
  }

  private static boolean parseBoolean(FilterCondition c) {
    return switch (c.value().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> throw new IllegalArgumentException("Invalid boolean value: " + c.value());
    };
  }

  // ── Sorting ──────────────────────────────────────────────────────────────

  static @NotNull Sort.Direction defaultDirection(@NotNull String name) {
    return DESCENDING.contains(name.toLowerCase(Locale.ROOT))
        ? Sort.Direction.DESCENDING
        : Sort.Direction.ASCENDING;
  }

  /**
   * Builds the order of a sort, ties broken by game id.
   *
   * @return the comparator, or null if the sort is the natural order, which is by id
   * @throws IllegalArgumentException if a field is unknown
   */
  static @Nullable Comparator<GameDto> comparator(@NotNull Sort sort) {
    if (sort.isNatural()) {
      return null;
    }
    Comparator<GameDto> result = null;
    for (Sort.Key key : sort.keys()) {
      Comparator<GameDto> c = sortField(key.field());
      Sort.Direction direction =
          key.direction() == null ? defaultDirection(key.field()) : key.direction();
      if (direction == Sort.Direction.DESCENDING) {
        c = c.reversed();
      }
      result = result == null ? c : result.thenComparing(c);
    }
    return result.thenComparing(GameDto::id);
  }

  private static Comparator<GameDto> sortField(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "id" -> Comparator.comparing(GameDto::id);
      case "playeddate", "date" -> Comparator.comparing(GameDto::date);
      case "playedyear" -> byInt(g -> g.date().year());
      case "whiteplayername" -> byName(g -> fullName(g.whitePlayer()));
      case "blackplayername" -> byName(g -> fullName(g.blackPlayer()));
      case "result" -> byInt(g -> g.result().ordinal());
      case "eco" -> byInt(g -> ecoOf(g).isSet() ? ecoOf(g).getInt() : -1);
      case "round" -> byInt(g -> g.round() == null ? 0 : g.round() * 1000 + (g.subRound() == null ? 0 : g.subRound()));
      case "tournament" -> byName(g -> g.tournament() == null ? null : g.tournament().title());
      case "source" -> byName(g -> g.source() == null ? null : g.source().title());
      case "annotator" -> byName(g -> g.annotator() == null ? null : g.annotator().name());
      case "gametag" -> byName(g -> g.gameTag() == null ? null : g.gameTag().englishTitle());
      case "whiteelo" -> byInt(g -> g.whiteElo() == null ? 0 : g.whiteElo());
      case "blackelo" -> byInt(g -> g.blackElo() == null ? 0 : g.blackElo());
      case "elomax" ->
          byInt(g -> Math.max(g.whiteElo() == null ? 0 : g.whiteElo(), g.blackElo() == null ? 0 : g.blackElo()));
      case "whiteteam" -> byName(g -> g.whiteTeam() == null ? null : g.whiteTeam().title());
      case "blackteam" -> byName(g -> g.blackTeam() == null ? null : g.blackTeam().title());
      case "setupposition" -> byInt(g -> Boolean.TRUE.equals(g.setupPosition()) ? 1 : 0);
      default ->
          throw new IllegalArgumentException(
              "Unknown sort field: '" + name + "'. Available fields: " + String.join(", ", SORT_NAMES));
    };
  }

  private static Comparator<GameDto> byInt(ToIntFunction<GameDto> key) {
    return Comparator.comparingInt(key);
  }

  private static Comparator<GameDto> byName(Function<GameDto, @Nullable String> key) {
    return Comparator.comparing(g -> {
      String name = key.apply(g);
      return name == null ? "" : name;
    }, String.CASE_INSENSITIVE_ORDER);
  }

  private static @Nullable String fullName(@Nullable PlayerDto player) {
    if (player == null || player.lastName() == null) {
      return null;
    }
    return player.firstName() == null ? player.lastName() : player.lastName() + ", " + player.firstName();
  }
}
