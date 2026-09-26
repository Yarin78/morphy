package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.morphy.tools.testdata.Corpus.Event;
import se.yarin.morphy.tools.testdata.Corpus.Person;
import se.yarin.morphy.tools.testdata.Corpus.RealGame;

/**
 * The versions of the test database, each adding to the previous one. A version that fails to
 * open in ChessBase, when the one before it does, is narrowed down by what it added, which the
 * manifest of each version lists.
 */
final class Versions {

  /**
   * One version.
   *
   * @param key the name of the version, used for its directory and files
   * @param title what the version is about
   * @param apply the operations that turn the previous version into this one
   */
  record Version(String key, String title, Consumer<Session> apply) {}

  private Versions() {}

  static List<Version> all() {
    return List.of(
        new Version("v1-basic", "Plain games; almost no features", Versions::basic),
        new Version(
            "v2-headers", "Entity variety and unusual header values", Versions::headers));
  }

  private static final String[] ECOS = {
    "A45", "B12", "B90", "C11", "C65", "D35", "D85", "E04", "E97", "A00", "C50", "B33"
  };

  private static final GameResult[] RESULTS = {
    GameResult.WHITE_WINS, GameResult.DRAW, GameResult.BLACK_WINS
  };

  // ── v1: basic ────────────────────────────────────────────────────────────

  private static void basic(Session s) {
    Random random = new Random(101);
    List<Person> players =
        List.of(
            Corpus.CARLSEN, Corpus.NEPOMNIACHTCHI, Corpus.CARUANA, Corpus.DING, Corpus.FIROUZJA,
            Corpus.NAKAMURA);

    s.feature(
        "Twelve plain games from the standard position: six real players, one event with only"
            + " a name, place and start date, all three results, ratings, ECO codes and round"
            + " numbers. No annotations, variations, sources, annotators or teams.",
        () -> {
          for (int i = 0; i < 12; i++) {
            List<Person> pair = new ArrayList<>(players);
            Collections.shuffle(pair, random);
            s.add(
                GameSpec.game()
                    .players(pair.get(0), pair.get(1))
                    .result(RESULTS[i % 3])
                    .date(new Date(2023, 1, 13 + i / 2))
                    .eco(ECOS[i])
                    .round(1 + i / 2, null)
                    .event(Corpus.TATA_STEEL_BASIC)
                    .moves(Moves.random(random, 30 + random.nextInt(40))));
          }
        });
  }

  // ── v2: headers and entities ─────────────────────────────────────────────

  private static void headers(Session s) {
    Random random = new Random(202);

    s.feature(
        "Five real historical games with their real players, events and dates. Two are casual"
            + " games with no round; only two have ratings. One opponent has no first name"
            + " (Duke of Brunswick and Isouard), and the dates include year-only and"
            + " year-and-month.",
        () -> {
          for (RealGame game : Corpus.REAL_GAMES) {
            s.add(GameSpec.real(game));
          }
        });

    s.feature(
        "Ten made-up modern games spread over six events with full details (nation, category,"
            + " rounds, type, time control), three sources with a publisher and a date, and"
            + " two annotators.",
        () -> {
          for (int i = 0; i < 10; i++) {
            Event event = Corpus.MODERN_EVENTS.get(i % Corpus.MODERN_EVENTS.size());
            List<Person> pair = new ArrayList<>(Corpus.MODERN);
            Collections.shuffle(pair, random);
            Person white = pair.get(0);
            Person black = pair.get(1);
            GameSpec spec =
                GameSpec.game()
                    .players(white, black)
                    .elo(white.elo() + random.nextInt(21) - 10, black.elo() + random.nextInt(21) - 10)
                    .result(RESULTS[random.nextInt(3)])
                    .date(new Date(event.start().year(), event.start().month(), 1 + (i * 3) % 27))
                    .eco(ECOS[random.nextInt(ECOS.length)])
                    .round(1 + random.nextInt(9), null)
                    .event(event)
                    .source(
                        switch (i % 3) {
                          case 0 -> Corpus.CBM_210;
                          case 1 -> Corpus.CBM_211;
                          default -> Corpus.TWIC_1450;
                        })
                    .moves(Moves.random(random, 20 + random.nextInt(60)));
            if (i % 2 == 0) {
              spec.annotator(i % 4 == 0 ? Corpus.FTACNIK : Corpus.RIBLI);
            }
            s.add(spec);
          }
        });

    s.feature(
        "Six team games from an Olympiad: white and black teams, with the board number as the"
            + " sub-round.",
        () -> {
          for (int i = 0; i < 6; i++) {
            List<String> teams = new ArrayList<>(Corpus.OLYMPIAD_TEAMS);
            Collections.shuffle(teams, random);
            List<Person> pair = new ArrayList<>(Corpus.MODERN);
            Collections.shuffle(pair, random);
            s.add(
                GameSpec.game()
                    .players(pair.get(0), pair.get(1))
                    .result(RESULTS[random.nextInt(3)])
                    .date(new Date(2022, 8, 1 + i / 2))
                    .eco(ECOS[random.nextInt(ECOS.length)])
                    .round(1 + i / 2, 1 + i % 4)
                    .event(Corpus.OLYMPIAD)
                    .teams(teams.get(0), teams.get(1))
                    .source(Corpus.ARCHIVE)
                    .moves(Moves.random(random, 20 + random.nextInt(40))));
          }
        });

    s.feature(
        "Header values that are missing or unusual: year-only, year-and-month and unset dates,"
            + " an unfinished result, no ratings, no event, no ECO, and a player with no first"
            + " name. Also two players with the same surname, and names with an umlaut and an"
            + " acute accent, which are in Latin-1.",
        () -> {
          s.add(
              GameSpec.game()
                  .players(Corpus.GRUENFELD, Corpus.BOENSCH)
                  .result(GameResult.DRAW)
                  .date(new Date(1925))
                  .moves(Moves.random(random, 30)));
          s.add(
              GameSpec.game()
                  .players(Corpus.NOGUEIRAS, Corpus.IVANOV_A)
                  .result(GameResult.WHITE_WINS)
                  .date(new Date(2019, 4))
                  .elo(null, null)
                  .moves(Moves.random(random, 30)));
          s.add(
              GameSpec.game()
                  .players(Corpus.IVANOV_S, Corpus.IVANOV_A)
                  .result(GameResult.NOT_FINISHED)
                  .date(Date.unset())
                  .moves(Moves.random(random, 30)));
          s.add(
              GameSpec.game()
                  .players(Corpus.NN, Corpus.CARLSEN)
                  .result(GameResult.BLACK_WINS)
                  .date(new Date(2021, 3, 9))
                  .elo(null, Corpus.CARLSEN.elo())
                  .moves(Moves.random(random, 30)));
          s.add(
              GameSpec.game()
                  .players(Corpus.CARLSEN, Corpus.NN)
                  .result(GameResult.WHITE_WINS)
                  .date(new Date(2021, 3, 10))
                  .moves(Moves.random(random, 30)));
        });

    s.note(
        "Known difference: the date of a source (games 18-33) comes back as its date from cbh but"
            + " as its publication date from 2cbh. Names longer than 30 characters are cut, and"
            + " characters outside Latin-1 become '?', in cbh, so the names here stay within that.");

    s.feature(
        "A game with no moves at all.",
        () ->
            s.add(
                GameSpec.game()
                    .players(Corpus.ANAND, Corpus.ARONIAN)
                    .result(GameResult.DRAW)
                    .date(new Date(2022, 5, 5))
                    .event(Corpus.NORWAY_CHESS)
                    .moves(Moves.none())));
  }
}
