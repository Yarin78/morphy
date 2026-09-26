package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.NAG;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * Reads a database back through the {@link Database} facade and compares it with what was put
 * into it: the games, the entities that exist because games refer to them (and that the ones
 * that no game refers to any more don't), and the search.
 */
final class Verifier {
  private static final int MAX_REPORTED = 25;

  private Verifier() {}

  static void verify(Session session, String when) {
    Database db = session.db;
    State state = session.state;
    List<String> problems = new ArrayList<>();

    if (db.gameCount() != state.expected.size()) {
      problems.add("game count is " + db.gameCount() + ", expected " + state.expected.size());
    } else {
      for (int id = 1; id <= state.expected.size(); id++) {
        GameDto actual = db.getGame(id, GameFetchOptions.full());
        compareGame(session.format, id, state.expected.get(id - 1), actual, problems);
      }
    }

    if (db.capabilities().hasEntities()) {
      compareEntities(session, problems);
      checkEntitySearch(session, problems);
    }
    checkSearch(session, problems);

    if (!problems.isEmpty()) {
      StringBuilder message =
          new StringBuilder(
              session.format.dir + " " + when + ": " + problems.size() + " problem(s)");
      for (int i = 0; i < Math.min(problems.size(), MAX_REPORTED); i++) {
        message.append("\n  ").append(problems.get(i));
      }
      throw new IllegalStateException(message.toString());
    }

    state.seenPlayers.addAll(players(state.expected));
    state.seenEvents.addAll(events(state.expected));
  }

  // ── Games ────────────────────────────────────────────────────────────────

  private static void compareGame(
      Format format, int id, GameDto expected, @Nullable GameDto actual, List<String> problems) {
    if (actual == null) {
      problems.add("game " + id + " is missing");
      return;
    }
    Map<String, String> e = essence(format, expected);
    Map<String, String> a = essence(format, actual);
    for (Map.Entry<String, String> field : e.entrySet()) {
      String got = a.get(field.getKey());
      if (!field.getValue().equals(got)) {
        problems.add(
            "game " + id + " " + field.getKey() + ": expected [" + abbreviate(field.getValue())
                + "] but got [" + abbreviate(got) + "]");
      }
    }
  }

  private static String abbreviate(@Nullable String s) {
    return s != null && s.length() > 300 ? s.substring(0, 300) + "… (" + s.length() + " chars)" : s;
  }

  /**
   * The values of a game that the format keeps, as text. Missing values are empty.
   *
   * <p>A PGN file has no line evaluation, and it is left out for it. The movetext is compared in
   * standard form, see {@link Canon}.
   */
  static Map<String, String> essence(Format format, GameDto g) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("type", str(g.type()));
    m.put("white", name(g.whitePlayer()));
    m.put("black", name(g.blackPlayer()));
    m.put("whiteElo", str(g.whiteElo()));
    m.put("blackElo", str(g.blackElo()));
    m.put("whiteTeam", title(g.whiteTeam()));
    m.put("blackTeam", title(g.blackTeam()));
    m.put("result", str(g.result()));
    m.put("date", str(g.date()));
    m.put("eco", str(g.eco()));
    m.put("round", str(g.round()));
    m.put("subRound", str(g.subRound()));
    if (format != Format.PGN) {
      m.put("lineEvaluation", g.lineEvaluation() == null || g.lineEvaluation() == NAG.NONE ? "" : str(g.lineEvaluation()));
    }
    TournamentDto t = g.tournament();
    m.put("event", t == null ? "" : str(t.title()));
    m.put("place", t == null ? "" : str(t.place()));
    m.put("nation", t == null ? "" : str(t.nation()));
    m.put("eventDate", t == null ? "" : str(t.startDate()));
    m.put("category", t == null ? "" : str(t.category()));
    m.put("rounds", t == null ? "" : str(t.rounds()));
    m.put("eventType", t == null ? "" : str(t.type()));
    m.put("timeControl", t == null ? "" : str(t.timeControl()));
    SourceDto s = g.source();
    m.put("source", s == null ? "" : str(s.title()));
    m.put("publisher", s == null ? "" : str(s.publisher()));
    // A source date given as "date" comes back as "date" from cbh but as "publication" from 2cbh
    // (noted in the manifest of v2); what counts here is that it is stored at all
    m.put("sourceDate", s == null ? "" : str(s.date() != null ? s.date() : s.publication()));
    AnnotatorDto an = g.annotator();
    m.put("annotator", an == null ? "" : str(an.name()));
    m.put("variant", str(g.variant()));
    m.put("moves", Canon.moves(g.moves(), g.variant() != null));
    m.put("fen", g.moves() == null ? "" : str(g.moves().fen()));
    m.put("text", g.text() == null ? "" : str(g.text().contents()));
    return m;
  }

  private static String str(@Nullable Object o) {
    return o == null ? "" : o.toString();
  }

  static String name(@Nullable PlayerDto p) {
    if (p == null || p.lastName() == null) {
      return "";
    }
    return p.firstName() == null || p.firstName().isEmpty() ? p.lastName() : p.lastName() + ", " + p.firstName();
  }

  private static String title(@Nullable TeamDto t) {
    return t == null ? "" : str(t.title());
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  static Set<String> players(List<GameDto> games) {
    Set<String> players = new LinkedHashSet<>();
    for (GameDto g : games) {
      add(players, name(g.whitePlayer()));
      add(players, name(g.blackPlayer()));
    }
    return players;
  }

  static Set<String> events(List<GameDto> games) {
    Set<String> events = new LinkedHashSet<>();
    for (GameDto g : games) {
      add(events, g.tournament() == null ? "" : str(g.tournament().title()));
    }
    return events;
  }

  /**
   * Compares the entities that exist with the ones the games refer to.
   *
   * <p>A game that has no source, annotator or event refers to an entity with an empty name
   * instead, in the ChessBase formats, and that entity exists like any other. The player of a game
   * without a player is not an entity of its own: there the game holds no player at all.
   */
  private static void compareEntities(Session session, List<String> problems) {
    Set<String> players = new LinkedHashSet<>();
    Set<String> events = new LinkedHashSet<>();
    Set<String> sources = new LinkedHashSet<>();
    Set<String> annotators = new LinkedHashSet<>();
    Set<String> teams = new LinkedHashSet<>();
    for (GameDto g : session.state.expected) {
      Map<String, String> e = essence(session.format, g);
      add(players, e.get("white"));
      add(players, e.get("black"));
      events.add(e.get("event"));
      sources.add(e.get("source"));
      annotators.add(e.get("annotator"));
      add(teams, e.get("whiteTeam"));
      add(teams, e.get("blackTeam"));
    }
    expectCount(session, problems, EntityKind.PLAYER, "players", players);
    expectCount(session, problems, EntityKind.TOURNAMENT, "tournaments", events);
    expectCount(session, problems, EntityKind.SOURCE, "sources", sources);
    expectCount(session, problems, EntityKind.ANNOTATOR, "annotators", annotators);
    expectCount(session, problems, EntityKind.TEAM, "teams", teams);
  }

  private static void add(Set<String> set, String value) {
    if (!value.isEmpty()) {
      set.add(value);
    }
  }

  private static void expectCount(
      Session session, List<String> problems, EntityKind<?> kind, String what, Set<String> expected) {
    long actual = session.db.entityCount(kind);
    if (actual != expected.size()) {
      problems.add(what + ": " + actual + " exist, expected " + expected.size() + " " + expected);
    }
  }

  /**
   * Every player and event is found in the entity index, and the ones that existed in an earlier
   * version but that no game refers to now are not: they were removed with their last game.
   */
  private static void checkEntitySearch(Session session, List<String> problems) {
    Set<String> players = players(session.state.expected);
    Set<String> events = events(session.state.expected);
    checkNames(session, problems, EntityKind.PLAYER, "player", players, session.state.seenPlayers);
    checkNames(session, problems, EntityKind.TOURNAMENT, "tournament", events, session.state.seenEvents);
  }

  private static void checkNames(
      Session session,
      List<String> problems,
      EntityKind<?> kind,
      String what,
      Set<String> current,
      Set<String> seen) {
    Set<String> all = new LinkedHashSet<>(seen);
    all.addAll(current);
    for (String name : all) {
      boolean exists = current.contains(name);
      long expected = 0;
      for (String other : current) {
        if (startsWith(other, name, false)) {
          expected++;
        }
      }
      String field = kind == EntityKind.PLAYER ? (name.indexOf(',') < 0 ? "lastname" : "name") : "title";
      String query = field + ":\"" + name + "\"";
      try {
        ResultPage<?> page =
            session.db.findEntities(kind, Query.of(query, Sort.natural(), 0, 1));
        long found = page.total() == null ? -1 : page.total();
        if (exists ? found < 1 : found != expected) {
          problems.add(
              what + " search " + query + ": found " + found + ", expected "
                  + (exists ? "at least 1" : expected + " (removed with its last game)"));
        }
      } catch (RuntimeException e) {
        problems.add(what + " search " + query + " failed: " + e);
      }
    }
  }

  // ── Search ───────────────────────────────────────────────────────────────

  /**
   * Every player is found by name, and in as many games as the expected games have them.
   *
   * <p>A name with a comma is searched as {@code player.name}. One without is a last name only, and
   * is searched as {@code player.lastname}: in the v1 search a {@code player.name} with a space in
   * it, but no comma, is read as a last name followed by a first name, so "Duke of Brunswick"
   * finds nothing there.
   */
  private static void checkSearch(Session session, List<String> problems) {
    Set<String> players = players(session.state.expected);
    for (String player : players) {
      boolean lastNameOnly = player.indexOf(',') < 0;
      String query = (lastNameOnly ? "player.lastname:\"" : "player.name:\"") + player + "\"";
      long expected = 0;
      for (GameDto g : session.state.expected) {
        if (startsWith(name(g.whitePlayer()), player, lastNameOnly)
            || startsWith(name(g.blackPlayer()), player, lastNameOnly)) {
          expected++;
        }
      }
      try {
        ResultPage<GameDto> page =
            session.db.findGames(
                Query.of(query, Sort.natural(), 0, 1), GameFetchOptions.headersOnly());
        if (page.total() == null || page.total() != expected) {
          problems.add("search " + query + ": found " + page.total() + ", expected " + expected);
        }
      } catch (RuntimeException e) {
        problems.add("search " + query + " failed: " + e);
      }
    }
  }

  /** Whether a name starts with a prefix, or with the prefix as a last name, ignoring case. */
  private static boolean startsWith(String name, String prefix, boolean lastNameOnly) {
    if (lastNameOnly) {
      int comma = name.indexOf(',');
      name = comma < 0 ? name : name.substring(0, comma);
    }
    return name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
  }
}
