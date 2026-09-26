package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
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
 * into it: the games, the entities that exist because games refer to them, and the search.
 */
final class Verifier {
  private static final int MAX_REPORTED = 25;

  private Verifier() {}

  static void verify(Session session, String when) {
    Database db = session.db;
    List<String> problems = new ArrayList<>();

    if (db.gameCount() != session.expected.size()) {
      problems.add("game count is " + db.gameCount() + ", expected " + session.expected.size());
    } else {
      for (int id = 1; id <= session.expected.size(); id++) {
        GameDto actual = db.getGame(id, GameFetchOptions.full());
        compareGame(id, session.expected.get(id - 1), actual, problems);
      }
    }

    if (db.capabilities().hasEntities()) {
      compareEntities(session, problems);
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
  }

  // ── Games ────────────────────────────────────────────────────────────────

  private static void compareGame(int id, GameDto expected, @Nullable GameDto actual, List<String> problems) {
    if (actual == null) {
      problems.add("game " + id + " is missing");
      return;
    }
    Map<String, String> e = essence(expected);
    Map<String, String> a = essence(actual);
    for (Map.Entry<String, String> field : e.entrySet()) {
      String got = a.get(field.getKey());
      if (!field.getValue().equals(got)) {
        problems.add(
            "game " + id + " " + field.getKey() + ": expected [" + field.getValue() + "] but got [" + got + "]");
      }
    }
  }

  /** The values of a game that every format keeps, as text. Missing values are empty. */
  static Map<String, String> essence(GameDto g) {
    Map<String, String> m = new LinkedHashMap<>();
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
    TournamentDto t = g.tournament();
    m.put("event", t == null ? "" : str(t.title()));
    m.put("place", t == null ? "" : str(t.place()));
    m.put("nation", t == null ? "" : str(t.nation()));
    m.put("eventDate", t == null ? "" : str(t.startDate()));
    m.put("category", t == null ? "" : str(t.category()));
    m.put("rounds", t == null ? "" : str(t.rounds()));
    m.put("type", t == null ? "" : str(t.type()));
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
    m.put("moves", g.moves() == null ? "" : str(g.moves().pgn()));
    m.put("fen", g.moves() == null ? "" : str(g.moves().fen()));
    return m;
  }

  private static String str(@Nullable Object o) {
    return o == null ? "" : o.toString();
  }

  private static String name(@Nullable PlayerDto p) {
    if (p == null || p.lastName() == null) {
      return "";
    }
    return p.firstName() == null || p.firstName().isEmpty() ? p.lastName() : p.lastName() + ", " + p.firstName();
  }

  private static String title(@Nullable TeamDto t) {
    return t == null ? "" : str(t.title());
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  /**
   * Compares the entities that exist with the ones the games refer to.
   *
   * <p>A game that has no source, annotator, event or team refers to an entity with an empty name
   * instead, in the ChessBase formats, and that entity exists like any other. The player of a game
   * without a player is not an entity of its own: there the game holds no player at all.
   */
  private static void compareEntities(Session session, List<String> problems) {
    Set<String> players = new LinkedHashSet<>();
    Set<String> events = new LinkedHashSet<>();
    Set<String> sources = new LinkedHashSet<>();
    Set<String> annotators = new LinkedHashSet<>();
    Set<String> teams = new LinkedHashSet<>();
    for (GameDto g : session.expected) {
      Map<String, String> e = essence(g);
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

  // ── Search ───────────────────────────────────────────────────────────────

  /**
   * Every player is found by name, and in as many games as the expected games have them.
   *
   * <p>A name with a comma is searched as {@code player.name}. One without is a last name only, and
   * is searched as {@code player.lastname}: in the v1 search a {@code player.name} with a space in
   * it, but no comma, is read as a last name followed by a first name, so "Duke of Brunswick" finds
   * nothing there.
   */
  private static void checkSearch(Session session, List<String> problems) {
    Set<String> players = new LinkedHashSet<>();
    for (GameDto g : session.expected) {
      Map<String, String> e = essence(g);
      add(players, e.get("white"));
      add(players, e.get("black"));
    }
    for (String player : players) {
      boolean lastNameOnly = player.indexOf(',') < 0;
      String query = (lastNameOnly ? "player.lastname:\"" : "player.name:\"") + player + "\"";
      long expected = 0;
      for (GameDto g : session.expected) {
        Map<String, String> e = essence(g);
        if (startsWith(e.get("white"), player, lastNameOnly)
            || startsWith(e.get("black"), player, lastNameOnly)) {
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
