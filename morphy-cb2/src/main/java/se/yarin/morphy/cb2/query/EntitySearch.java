package se.yarin.morphy.cb2.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.IntRange;
import se.yarin.morphy.api.query.PartialDateParser;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.cb2.convert.DtoConverter;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.cb2.indexes.EntityOrder;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;

/**
 * How the entities of one kind are searched: the fields they can be filtered and sorted on, with
 * the same names as for a v1 database. Text fields match a prefix, ignoring case, and a name or
 * title may list alternatives separated by {@code |}.
 */
public final class EntitySearch {

  /**
   * An entity found by a search.
   *
   * @param id the entity id
   * @param entity the entity
   * @param count the number of games referring to it in the kind's role
   */
  public record Row(long id, @NotNull Entity entity, int count) {}

  private record SortField(
      @NotNull String name, @NotNull Comparator<Row> comparator, @NotNull Sort.Direction direction) {}

  private final @NotNull EntityKind<?> kind;
  private final @NotNull EntityType type;
  private final @NotNull Role role;
  private final @NotNull StandardIndex index;
  private final @NotNull String defaultField;
  private final Map<String, Function<FilterCondition, Predicate<Entity>>> filters;
  private final Map<String, SortField> sortFields;

  private EntitySearch(
      EntityKind<?> kind,
      EntityType type,
      Role role,
      StandardIndex index,
      String defaultField,
      Map<String, Function<FilterCondition, Predicate<Entity>>> filters,
      List<SortField> sortFields) {
    this.kind = kind;
    this.type = type;
    this.role = role;
    this.index = index;
    this.defaultField = defaultField;
    this.filters = filters;
    this.sortFields = new LinkedHashMap<>();
    for (SortField field : sortFields) {
      this.sortFields.put(field.name().toLowerCase(Locale.ROOT), field);
    }
  }

  private static final Map<EntityKind<?>, EntitySearch> SEARCHES = new LinkedHashMap<>();

  static {
    SEARCHES.put(EntityKind.PLAYER, players(EntityKind.PLAYER, Role.PLAYER, StandardIndex.PLAYERS));
    SEARCHES.put(
        EntityKind.ANNOTATOR, players(EntityKind.ANNOTATOR, Role.ANNOTATOR, StandardIndex.ANNOTATORS));
    SEARCHES.put(EntityKind.TOURNAMENT, tournaments());
    SEARCHES.put(EntityKind.SOURCE, sources());
    SEARCHES.put(EntityKind.TEAM, teams());
    SEARCHES.put(EntityKind.GAME_TAG, gameTags());
  }

  /**
   * The search of an entity kind.
   *
   * @throws IllegalArgumentException if the kind is not supported
   */
  public static @NotNull EntitySearch of(@NotNull EntityKind<?> kind) {
    EntitySearch search = SEARCHES.get(kind);
    if (search == null) {
      throw new IllegalArgumentException("Unsupported entity kind: " + kind);
    }
    return search;
  }

  public @NotNull EntityKind<?> kind() {
    return kind;
  }

  public @NotNull EntityType type() {
    return type;
  }

  /** The role in which games refer to entities of this kind. */
  public @NotNull Role role() {
    return role;
  }

  /** The sort order holding the entities of this kind that games refer to. */
  public @NotNull StandardIndex index() {
    return index;
  }

  public @NotNull String defaultField() {
    return defaultField;
  }

  /** The fields the entities can be filtered on. */
  public @NotNull List<String> fields() {
    return List.copyOf(filters.keySet());
  }

  /** The schema of this kind. */
  public @NotNull SearchSchema schema() {
    List<SearchSchema.SortField> sorts = new ArrayList<>();
    for (SortField field : sortFields.values()) {
      sorts.add(new SearchSchema.SortField(field.name(), field.direction()));
    }
    return new SearchSchema(defaultField, fields(), Set.of(), sorts);
  }

  /**
   * A filter of entities from conditions on this kind's fields, without the entity prefix.
   *
   * @throws IllegalArgumentException if a field is unknown or a value invalid
   */
  public @NotNull Predicate<Entity> filter(@NotNull List<FilterCondition> conditions) {
    Predicate<Entity> result = e -> true;
    for (FilterCondition condition : conditions) {
      Function<FilterCondition, Predicate<Entity>> builder =
          filters.get(condition.field().toLowerCase(Locale.ROOT));
      if (builder == null) {
        throw new IllegalArgumentException(
            "Unknown "
                + kind.name().toLowerCase(Locale.ROOT)
                + " filter field: '"
                + condition.field()
                + "'. Available fields: "
                + String.join(", ", filters.keySet()));
      }
      try {
        result = result.and(builder.apply(condition));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid value in " + condition, e);
      }
    }
    return result;
  }

  /**
   * The order of a sort; the natural order is the database's own sort order of the kind.
   *
   * @return the comparator, or null for the natural order
   * @throws IllegalArgumentException if a field is unknown
   */
  public Comparator<Row> comparator(@NotNull Sort sort) {
    if (sort.isNatural()) {
      return null;
    }
    Comparator<Row> result = null;
    for (Sort.Key key : sort.keys()) {
      SortField field = sortFields.get(key.field().toLowerCase(Locale.ROOT));
      if (field == null) {
        throw new IllegalArgumentException(
            "Unknown sort field: '"
                + key.field()
                + "'. Available fields: "
                + String.join(", ", sortFields.values().stream().map(SortField::name).toList()));
      }
      Sort.Direction direction = key.direction() == null ? field.direction() : key.direction();
      Comparator<Row> c =
          direction == Sort.Direction.DESCENDING ? field.comparator().reversed() : field.comparator();
      result = result == null ? c : result.thenComparing(c);
    }
    return result.thenComparingLong(Row::id);
  }

  // ── The kinds ────────────────────────────────────────────────────────────

  private static EntitySearch players(EntityKind<?> kind, Role role, StandardIndex index) {
    Map<String, Function<FilterCondition, Predicate<Entity>>> filters = new LinkedHashMap<>();
    filters.put("name", c -> alternatives(c.value(), EntitySearch::playerName));
    if (kind == EntityKind.PLAYER) {
      filters.put("firstname", c -> e -> prefix(((Player) e).firstName(), c.value()));
      filters.put("lastname", c -> e -> prefix(((Player) e).lastName(), c.value()));
    }
    List<SortField> sorts = new ArrayList<>();
    sorts.add(
        new SortField(
            "name",
            (a, b) -> EntityOrder.compareFields(a.entity(), b.entity()),
            Sort.Direction.ASCENDING));
    if (kind == EntityKind.PLAYER) {
      sorts.add(ascending("firstName", r -> ((Player) r.entity()).firstName()));
      sorts.add(ascending("lastName", r -> ((Player) r.entity()).lastName()));
    }
    sorts.add(countField());
    return new EntitySearch(kind, EntityType.PLAYER, role, index, "name", filters, sorts);
  }

  /** Matches "Last, First" or just a last name, each part as a prefix. */
  private static Predicate<Entity> playerName(String value) {
    int comma = value.indexOf(',');
    String last = (comma < 0 ? value : value.substring(0, comma)).strip();
    String first = comma < 0 ? "" : value.substring(comma + 1).strip();
    return e -> {
      Player p = (Player) e;
      return prefix(p.lastName(), last) && prefix(p.firstName(), first);
    };
  }

  private static EntitySearch tournaments() {
    Map<String, Function<FilterCondition, Predicate<Entity>>> f = new LinkedHashMap<>();
    f.put("title", c -> alternatives(c.value(), v -> e -> prefix(((Tournament) e).title(), v)));
    f.put("date", c -> dateFilter(c, e -> ((Tournament) e).startDate()));
    f.put("year", c -> intFilter(c, e -> ((Tournament) e).startDate() >> 9, 9999));
    f.put(
        "type",
        c -> e -> DtoConverter.tournamentType((Tournament) e).getName().equalsIgnoreCase(c.value()));
    f.put(
        "time",
        c -> e -> DtoConverter.timeControl((Tournament) e).getName().equalsIgnoreCase(c.value()));
    f.put("place", c -> e -> prefix(((Tournament) e).place(), c.value()));
    f.put("nation", c -> e -> c.value().equalsIgnoreCase(DtoConverter.nation(((Tournament) e).nation())));
    f.put("category", c -> intFilter(c, e -> ((Tournament) e).category(), 99));
    f.put("rounds", c -> intFilter(c, e -> ((Tournament) e).rounds(), 999));
    f.put("team", c -> e -> ((Tournament) e).teamTournament() == Boolean.parseBoolean(c.value()));
    List<SortField> sorts =
        List.of(
            ascending("title", r -> ((Tournament) r.entity()).title()),
            descendingInt("year", r -> ((Tournament) r.entity()).startDate() >> 9),
            descendingInt("startDate", r -> ((Tournament) r.entity()).startDate()),
            descendingInt("date", r -> ((Tournament) r.entity()).startDate()),
            descendingInt("endDate", r -> ((Tournament) r.entity()).endDate()),
            ascending("place", r -> ((Tournament) r.entity()).place()),
            ascending(
                "nation", r -> String.valueOf(DtoConverter.nation(((Tournament) r.entity()).nation()))),
            descendingInt("category", r -> ((Tournament) r.entity()).category()),
            descendingInt("rounds", r -> ((Tournament) r.entity()).rounds()),
            ascendingInt("complete", r -> ((Tournament) r.entity()).complete() ? 1 : 0),
            countField());
    return new EntitySearch(
        EntityKind.TOURNAMENT, EntityType.TOURNAMENT, Role.TOURNAMENT, StandardIndex.TOURNAMENTS,
        "title", f, sorts);
  }

  private static EntitySearch sources() {
    Map<String, Function<FilterCondition, Predicate<Entity>>> f = new LinkedHashMap<>();
    f.put("title", c -> alternatives(c.value(), v -> e -> prefix(((Source) e).title(), v)));
    f.put("publisher", c -> e -> prefix(((Source) e).publisher(), c.value()));
    f.put("date", c -> dateFilter(c, e -> ((Source) e).date()));
    f.put("publication", c -> dateFilter(c, e -> ((Source) e).publicationDate()));
    f.put("version", c -> intFilter(c, e -> ((Source) e).version(), 99999));
    f.put(
        "quality",
        c -> {
          int quality =
              switch (c.value().toLowerCase(Locale.ROOT)) {
                case "unset" -> 0;
                case "high" -> 1;
                case "medium" -> 2;
                case "low" -> 3;
                default -> throw new IllegalArgumentException("Invalid source quality: " + c.value());
              };
          return e -> ((Source) e).quality() == quality;
        });
    List<SortField> sorts =
        List.of(
            ascending("title", r -> ((Source) r.entity()).title()),
            ascending("publisher", r -> ((Source) r.entity()).publisher()),
            descendingInt("date", r -> ((Source) r.entity()).date()),
            descendingInt("publication", r -> ((Source) r.entity()).publicationDate()),
            ascendingInt("version", r -> ((Source) r.entity()).version()),
            ascendingInt("quality", r -> ((Source) r.entity()).quality()),
            countField());
    return new EntitySearch(
        EntityKind.SOURCE, EntityType.SOURCE, Role.SOURCE, StandardIndex.SOURCES, "title", f, sorts);
  }

  private static EntitySearch teams() {
    Map<String, Function<FilterCondition, Predicate<Entity>>> f = new LinkedHashMap<>();
    f.put("title", c -> alternatives(c.value(), v -> e -> prefix(((Team) e).title(), v)));
    f.put("name", c -> alternatives(c.value(), v -> e -> prefix(((Team) e).title(), v)));
    f.put("number", c -> intFilter(c, e -> ((Team) e).number(), 255));
    f.put("year", c -> intFilter(c, e -> ((Team) e).year(), 9999));
    f.put("nation", c -> e -> c.value().equalsIgnoreCase(DtoConverter.nation(((Team) e).nation())));
    List<SortField> sorts =
        List.of(
            ascending("title", r -> ((Team) r.entity()).title()),
            ascendingInt("number", r -> ((Team) r.entity()).number()),
            ascendingInt("season", r -> ((Team) r.entity()).season() ? 1 : 0),
            ascendingInt("year", r -> ((Team) r.entity()).year()),
            ascending("nation", r -> String.valueOf(DtoConverter.nation(((Team) r.entity()).nation()))),
            countField());
    return new EntitySearch(
        EntityKind.TEAM, EntityType.TEAM, Role.TEAM, StandardIndex.TEAMS, "title", f, sorts);
  }

  private static EntitySearch gameTags() {
    Map<String, Function<FilterCondition, Predicate<Entity>>> f = new LinkedHashMap<>();
    f.put(
        "title",
        c ->
            alternatives(
                c.value(),
                v ->
                    e -> ((GameTag) e).titles().stream().anyMatch(t -> prefix(t.text(), v))));
    f.put(
        "languages",
        c -> e -> ((GameTag) e).titles().stream()
            .anyMatch(t -> !t.text().isEmpty() && c.value().equalsIgnoreCase(DtoConverter.nation(t.language()))));
    List<SortField> sorts =
        List.of(ascending("title", r -> ((GameTag) r.entity()).title()), countField());
    return new EntitySearch(
        EntityKind.GAME_TAG, EntityType.GAME_TAG, Role.GAME_TAG, StandardIndex.GAME_TAGS, "title", f,
        sorts);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static SortField countField() {
    return new SortField("count", Comparator.comparingInt(Row::count), Sort.Direction.DESCENDING);
  }

  private static SortField ascending(String name, Function<Row, String> key) {
    return new SortField(
        name,
        Comparator.comparing(key, String.CASE_INSENSITIVE_ORDER),
        Sort.Direction.ASCENDING);
  }

  private static SortField ascendingInt(String name, ToIntFunction<Row> key) {
    return new SortField(name, Comparator.comparingInt(key), Sort.Direction.ASCENDING);
  }

  private static SortField descendingInt(String name, ToIntFunction<Row> key) {
    return new SortField(name, Comparator.comparingInt(key), Sort.Direction.DESCENDING);
  }

  /** Whether a text starts with a prefix, ignoring case. */
  static boolean prefix(@NotNull String text, @NotNull String prefix) {
    return text.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  /** A filter matching any of the alternatives of a value separated by {@code |}. */
  static Predicate<Entity> alternatives(String value, Function<String, Predicate<Entity>> one) {
    Predicate<Entity> result = e -> false;
    for (String alternative : value.split("\\|")) {
      result = result.or(one.apply(alternative.strip()));
    }
    return result;
  }

  private static Predicate<Entity> intFilter(
      FilterCondition condition, ToIntFunction<Entity> value, int max) {
    IntRange range = IntRange.parse(condition, max);
    return e -> {
      int v = value.applyAsInt(e);
      return v >= range.min() && v <= range.max();
    };
  }

  private static Predicate<Entity> dateFilter(
      FilterCondition condition, ToIntFunction<Entity> value) {
    PartialDateParser.DateRange range = PartialDateParser.parseRange(condition);
    return e -> inRange(Dates.decode(value.applyAsInt(e)), range);
  }

  /** Whether a date lies in a range. */
  static boolean inRange(Date date, PartialDateParser.DateRange range) {
    return date.compareTo(range.from()) >= 0 && date.compareTo(range.to()) <= 0;
  }
}
