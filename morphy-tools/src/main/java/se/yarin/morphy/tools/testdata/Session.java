package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * One version of a database being built: the open database, and what it is expected to contain.
 *
 * <p>Everything a version does goes through here, so that the expectation follows. Changing an
 * entity is a single call whichever the format: a database with entities updates the entity, and
 * one without, a PGN file, rewrites the games that hold it.
 */
final class Session {
  final Format format;
  final Database db;
  final State state;
  final List<String> manifest = new ArrayList<>();

  /** The games that were replaced or changed since the current feature began. */
  private final SortedSet<Integer> changed = new TreeSet<>();

  Session(Format format, Database db, State state) {
    this.format = format;
    this.db = db;
    this.state = state;
  }

  boolean hasEntities() {
    return db.capabilities().hasEntities();
  }

  /** Whether the format has guiding texts. */
  boolean hasTexts() {
    return format != Format.PGN;
  }

  int gameCount() {
    return state.expected.size();
  }

  // ── Adding and replacing ─────────────────────────────────────────────────

  /**
   * Adds a game, which must get the next id. A guiding text is left out of a format that has none.
   *
   * @return the id, or 0 if nothing was added
   */
  int add(GameSpec spec) {
    return add(null, spec);
  }

  /**
   * Adds a game and remembers its id under a name.
   *
   * @return the id, or 0 if nothing was added
   */
  int add(@Nullable String name, GameSpec spec) {
    if (spec.type.equals("text") && !hasTexts()) {
      return 0;
    }
    GameDto game = spec.build();
    long id = db.addGame(game);
    if (id != state.expected.size() + 1) {
      throw new IllegalStateException(
          "Expected the new game to get id " + (state.expected.size() + 1) + " but got " + id);
    }
    state.expected.add(game);
    if (name != null) {
      state.named.put(name, (int) id);
    }
    return (int) id;
  }

  /** The id of a named game. */
  int id(String name) {
    Integer id = state.named.get(name);
    if (id == null) {
      throw new IllegalArgumentException("No game is named " + name);
    }
    return id;
  }

  /** What a named game is expected to be now. */
  GameSpec spec(String name) {
    return spec(id(name));
  }

  /** What a game is expected to be now. */
  GameSpec spec(int id) {
    return GameSpec.from(state.expected.get(id - 1));
  }

  /** Replaces a named game. */
  void replace(String name, GameSpec spec) {
    replace(id(name), spec);
  }

  /** Replaces a game. */
  void replace(int id, GameSpec spec) {
    if (spec.type.equals("text") && !hasTexts()) {
      return;
    }
    GameDto game = spec.build();
    db.replaceGame(id, game);
    state.expected.set(id - 1, game);
    changed.add(id);
  }

  /**
   * Does something to the database and records in the manifest what it is for, and which games it
   * added, replaced or changed.
   *
   * @param what a description of the features the operations exercise
   */
  void feature(String what, Runnable body) {
    int first = state.expected.size() + 1;
    changed.clear();
    body.run();
    int last = state.expected.size();
    List<String> where = new ArrayList<>();
    if (last >= first) {
      where.add((first == last ? "game " + first : "games " + first + "\u2013" + last) + " added");
    }
    changed.removeIf(id -> id >= first);
    if (!changed.isEmpty()) {
      where.add((changed.size() == 1 ? "game " : "games ") + ranges(changed) + " changed");
    }
    manifest.add(
        (where.isEmpty() ? "nothing to do in " + format.dir : String.join(", ", where)) + ": " + what);
  }

  /** Ids as ranges: 1, 5\u20137, 12. */
  private static String ranges(SortedSet<Integer> ids) {
    StringBuilder sb = new StringBuilder();
    Integer start = null;
    Integer previous = null;
    for (int id : ids) {
      if (start == null) {
        start = id;
      } else if (id != previous + 1) {
        appendRange(sb, start, previous);
        start = id;
      }
      previous = id;
    }
    appendRange(sb, start, previous);
    return sb.toString();
  }

  private static void appendRange(StringBuilder sb, int from, int to) {
    if (sb.length() > 0) {
      sb.append(", ");
    }
    sb.append(from == to ? String.valueOf(from) : from == to - 1 ? from + ", " + to : from + "\u2013" + to);
  }

  /** Records something in the manifest that doesn't add games. */
  void note(String what) {
    manifest.add(what);
  }

  // ── Changing entities ────────────────────────────────────────────────────

  /** Changes a player, in every game and in the database's entity if there is one. */
  void updatePlayer(String fullName, UnaryOperator<PlayerDto> change) {
    update(
        EntityKind.PLAYER,
        "name:\"" + fullName + "\"",
        e -> Verifier.name(e).equals(fullName),
        change,
        g -> {
          boolean white = fullName.equals(Verifier.name(g.whitePlayer()));
          boolean black = fullName.equals(Verifier.name(g.blackPlayer()));
          if (!white && !black) {
            return null;
          }
          GameSpec spec = GameSpec.from(g);
          if (white) {
            spec.white = change.apply(g.whitePlayer());
          }
          if (black) {
            spec.black = change.apply(g.blackPlayer());
          }
          return spec;
        });
  }

  /** Changes an event (tournament), in every game and in the database's entity if there is one. */
  void updateEvent(String title, UnaryOperator<TournamentDto> change) {
    update(
        EntityKind.TOURNAMENT,
        "title:\"" + title + "\"",
        e -> title.equals(e.title()),
        change,
        g -> {
          if (g.tournament() == null || !title.equals(g.tournament().title())) {
            return null;
          }
          GameSpec spec = GameSpec.from(g);
          spec.tournament = change.apply(g.tournament());
          return spec;
        });
  }

  /** Changes a source, in every game and in the database's entity if there is one. */
  void updateSource(String title, UnaryOperator<SourceDto> change) {
    update(
        EntityKind.SOURCE,
        "title:\"" + title + "\"",
        e -> title.equals(e.title()),
        change,
        g -> {
          if (g.source() == null || !title.equals(g.source().title())) {
            return null;
          }
          GameSpec spec = GameSpec.from(g);
          spec.source = change.apply(g.source());
          return spec;
        });
  }

  /** Changes an annotator, in every game and in the database's entity if there is one. */
  void updateAnnotator(String name, UnaryOperator<AnnotatorDto> change) {
    update(
        EntityKind.ANNOTATOR,
        "name:\"" + name + "\"",
        e -> name.equals(e.name()),
        change,
        g -> {
          if (g.annotator() == null || !name.equals(g.annotator().name())) {
            return null;
          }
          GameSpec spec = GameSpec.from(g);
          spec.annotator = change.apply(g.annotator());
          return spec;
        });
  }

  /** Changes a team, in every game and in the database's entity if there is one. */
  void updateTeam(String title, UnaryOperator<TeamDto> change) {
    update(
        EntityKind.TEAM,
        "title:\"" + title + "\"",
        e -> title.equals(e.title()),
        change,
        g -> {
          boolean white = g.whiteTeam() != null && title.equals(g.whiteTeam().title());
          boolean black = g.blackTeam() != null && title.equals(g.blackTeam().title());
          if (!white && !black) {
            return null;
          }
          GameSpec spec = GameSpec.from(g);
          if (white) {
            spec.whiteTeam = change.apply(g.whiteTeam());
          }
          if (black) {
            spec.blackTeam = change.apply(g.blackTeam());
          }
          return spec;
        });
  }

  /**
   * Changes an entity everywhere it is used.
   *
   * @param edit the changed spec of a game, or null if the game doesn't hold the entity
   */
  private <T> void update(
      EntityKind<T> kind,
      String filter,
      java.util.function.Predicate<T> isEntity,
      UnaryOperator<T> change,
      Function<GameDto, GameSpec> edit) {
    List<Integer> changedNow = new ArrayList<>();
    for (int id = 1; id <= state.expected.size(); id++) {
      GameSpec spec = edit.apply(state.expected.get(id - 1));
      if (spec != null) {
        state.expected.set(id - 1, spec.build());
        changedNow.add(id);
      }
    }
    changed.addAll(changedNow);
    if (changedNow.isEmpty()) {
      throw new IllegalArgumentException("No game holds the " + kind + " " + filter);
    }
    if (hasEntities()) {
      T entity = find(kind, filter, isEntity);
      db.updateEntity(kind, idOf(entity), change.apply(entity));
    } else {
      for (int id : changedNow) {
        db.replaceGame(id, state.expected.get(id - 1));
      }
    }
  }

  /**
   * Tries to change a player into what another one is, which a database must refuse. Nothing is
   * changed. There is nothing to try in a database without entities.
   */
  void expectRefusedMerge(String fullName, String intoFullName) {
    if (!hasEntities()) {
      return;
    }
    PlayerDto entity = find(EntityKind.PLAYER, "name:\"" + fullName + "\"", e -> Verifier.name(e).equals(fullName));
    PlayerDto other = find(EntityKind.PLAYER, "name:\"" + intoFullName + "\"", e -> Verifier.name(e).equals(intoFullName));
    PlayerDto merged =
        new PlayerDto(entity.id(), other.lastName(), other.firstName(), null, null, null);
    try {
      db.updateEntity(EntityKind.PLAYER, entity.id(), merged);
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new IllegalStateException(
        "Renaming " + fullName + " to " + intoFullName + " should have been refused");
  }

  private <T> T find(EntityKind<T> kind, String filter, java.util.function.Predicate<T> isEntity) {
    for (T entity : db.findEntities(kind, Query.of(filter, Sort.natural(), 0, 100)).items()) {
      if (isEntity.test(entity)) {
        return entity;
      }
    }
    throw new IllegalStateException("There is no " + kind + " for " + filter);
  }

  private static long idOf(Object entity) {
    return switch (entity) {
      case PlayerDto e -> e.id();
      case TournamentDto e -> e.id();
      case SourceDto e -> e.id();
      case AnnotatorDto e -> e.id();
      case TeamDto e -> e.id();
      default -> throw new IllegalArgumentException("Unsupported entity " + entity);
    };
  }

  /** Checks that the database holds exactly what is expected. */
  void verify(String when) {
    Verifier.verify(this, when);
  }

  /** Reads a game as the database gives it. */
  GameDto read(int id) {
    return db.getGame(id, GameFetchOptions.full());
  }

  @Override
  public String toString() {
    return format.dir.toUpperCase(Locale.ROOT);
  }
}
