package se.yarin.morphy.tools.testdata;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * The people, events and games the test databases are made of. Real names where there are real
 * ones; the data around them (which player met which, when, with what rating) is made up.
 *
 * <p>The entity objects are constants, so that every game that refers to one refers to exactly the
 * same entity.
 */
final class Corpus {

  private Corpus() {}

  /** A player; the rating is the one used for games without a rating of their own. */
  record Person(String last, String first, int elo) {
    String full() {
      return first == null || first.isEmpty() ? last : last + ", " + first;
    }

    PlayerDto dto() {
      return new PlayerDto(null, last, first == null || first.isEmpty() ? null : first, null, null, null);
    }
  }

  private static Person p(String last, String first, int elo) {
    return new Person(last, first, elo);
  }

  // ── Players ──────────────────────────────────────────────────────────────

  static final Person CARLSEN = p("Carlsen", "Magnus", 2853);
  static final Person NEPOMNIACHTCHI = p("Nepomniachtchi", "Ian", 2793);
  static final Person CARUANA = p("Caruana", "Fabiano", 2782);
  static final Person NAKAMURA = p("Nakamura", "Hikaru", 2768);
  static final Person DING = p("Ding", "Liren", 2788);
  static final Person FIROUZJA = p("Firouzja", "Alireza", 2785);
  static final Person GUKESH = p("Gukesh", "D", 2750);
  static final Person ERIGAISI = p("Erigaisi", "Arjun", 2735);
  static final Person PRAGG = p("Praggnanandhaa", "R", 2727);
  static final Person VACHIER = p("Vachier-Lagrave", "Maxime", 2760);
  static final Person RAPPORT = p("Rapport", "Richard", 2764);
  static final Person ANAND = p("Anand", "Viswanathan", 2750);
  static final Person ARONIAN = p("Aronian", "Levon", 2765);
  static final Person GIRI = p("Giri", "Anish", 2764);
  static final Person DUDA = p("Duda", "Jan-Krzysztof", 2750);
  static final Person DUBOV = p("Dubov", "Daniil", 2720);
  static final Person SO = p("So", "Wesley", 2770);
  static final Person MAMEDYAROV = p("Mamedyarov", "Shakhriyar", 2760);
  static final Person NOGUEIRAS = p("Nogueiras", "Jesús", 2600);
  static final Person GRUENFELD = p("Grünfeld", "Ernst", 2500);
  static final Person BOENSCH = p("Bönsch", "Uwe", 2560);
  // Same surname, different people
  static final Person IVANOV_A = p("Ivanov", "Alexander", 2540);
  static final Person IVANOV_S = p("Ivanov", "Sergey", 2510);
  // No first name at all
  static final Person NN = p("NN", "", 0);

  /** The players the modern games are drawn from. */
  static final List<Person> MODERN =
      List.of(
          CARLSEN, NEPOMNIACHTCHI, CARUANA, NAKAMURA, DING, FIROUZJA, GUKESH, ERIGAISI, PRAGG,
          VACHIER, RAPPORT, ANAND, ARONIAN, GIRI, DUDA, DUBOV, SO, MAMEDYAROV);

  // ── Tournaments ──────────────────────────────────────────────────────────

  /** A tournament, with every field the games can carry. */
  record Event(
      String title,
      String place,
      String nation,
      Date start,
      @Nullable Integer category,
      @Nullable Integer rounds,
      @Nullable String type,
      @Nullable String timeControl) {

    TournamentDto dto() {
      return new TournamentDto(
          null, title, start, null, place, nation, category, null, rounds, type, timeControl, null,
          null, null, null, null, null, null);
    }
  }

  /** Only what every game has to say about its event. */
  static final Event TATA_STEEL_BASIC =
      new Event("Tata Steel Masters 2023", "Wijk aan Zee", null, new Date(2023, 1, 13), null, null, null, null);

  static final Event NORWAY_CHESS =
      new Event("Norway Chess 2023", "Stavanger", "NOR", new Date(2023, 5, 29), 21, 9, "tourn", null);
  static final Event CANDIDATES =
      new Event("Candidates Tournament 2022", "Madrid", "ESP", new Date(2022, 6, 17), 22, 14, "tourn", null);
  static final Event OLYMPIAD =
      new Event("44th Olympiad 2022", "Chennai", "IND", new Date(2022, 7, 29), null, 11, "team", null);
  static final Event WORLD_RAPID =
      new Event("World Rapid Championship 2022", "Almaty", "KAZ", new Date(2022, 12, 25), null, 13, "swiss", "rapid");
  static final Event WORLD_BLITZ =
      new Event("World Blitz Championship 2022", "Almaty", "KAZ", new Date(2022, 12, 28), null, 21, "swiss", "blitz");
  static final Event SINQUEFIELD =
      new Event("Sinquefield Cup 2022", "Saint Louis", "USA", new Date(2022, 8, 20), 23, 9, "tourn", null);
  static final Event WCH_2021 =
      new Event("World Championship 2021", "Dubai", "UAE", new Date(2021, 11, 26), null, 14, "match", null);
  static final Event WIJK_1999 =
      new Event("Hoogovens Wijk aan Zee 1999", "Wijk aan Zee", "NED", new Date(1999, 1, 16), 18, 13, "tourn", null);
  static final Event ROSENWALD =
      new Event("Rosenwald Memorial 1956", "New York", "USA", new Date(1956, 10, 7), null, 12, "tourn", null);
  static final Event REYKJAVIK_1972 =
      new Event("World Championship 1972", "Reykjavik", "ISL", new Date(1972, 7, 11), null, 21, "match", null);
  static final Event PARIS_1858 =
      new Event("Casual game Paris 1858", "Paris", "FRA", new Date(1858), null, null, null, null);
  static final Event LONDON_1851 =
      new Event("Casual game London 1851", "London", "ENG", new Date(1851, 6), null, null, null, null);

  /**
   * The events of the guiding texts. A text can say no more about its event than the title and the
   * start date, so these have no more to say, and no game refers to them: a text that named an
   * event of a game would get an event of its own, with the same title and less in it.
   */
  static final Event NOTES_OPENINGS =
      new Event("Opening Notes 2023", null, null, new Date(2023, 1, 1), null, null, null, null);
  static final Event NOTES_ENDGAMES =
      new Event("Endgame Notes 2023", null, null, new Date(2023, 3, 1), null, null, null, null);

  /** The events the made-up modern games are spread over. */
  static final List<Event> MODERN_EVENTS =
      List.of(NORWAY_CHESS, CANDIDATES, WORLD_RAPID, WORLD_BLITZ, SINQUEFIELD, WCH_2021);

  // ── Teams, sources, annotators ───────────────────────────────────────────

  static TeamDto team(String title) {
    return new TeamDto(null, title, null, null, null, null, null);
  }

  static final List<String> OLYMPIAD_TEAMS =
      List.of("India 1", "India 2", "Norway", "USA", "Armenia", "Uzbekistan", "Netherlands", "Germany");

  /** A source; only the title, publisher and date survive a game. */
  static SourceDto source(String title, String publisher, Date date) {
    return new SourceDto(null, title, publisher, null, date, null, null, null);
  }

  static final SourceDto CBM_210 = source("CBM 210", "ChessBase", new Date(2023, 2, 1));
  static final SourceDto CBM_211 = source("CBM 211", "ChessBase", new Date(2023, 4, 1));
  static final SourceDto TWIC_1450 = source("The Week in Chess 1450", "Mark Crowther", new Date(2023, 1, 23));
  static final SourceDto ARCHIVE = source("Chess Archive", null, null);

  /** The source of a guiding text, which can say no more than its title. */
  static final SourceDto NOTES_SOURCE = source("Notes Vol. 1", null, null);

  /**
   * A game tag, of which a game can say no more than the English title. Titles in other languages
   * are given to the tag itself, see {@code Session#updateGameTag}.
   */
  static GameTagDto gameTag(String english) {
    return new GameTagDto(
        null, english, null, null, english, null, null, null, null, null, null, null, null, null);
  }

  /** Game tags in the ways ChessBase databases use them: a theme, or a kind of position. */
  static final List<String> GAME_TAGS =
      List.of("Opening trap", "Endgame technique", "Strat\u00e9gie", "Tactics (pins & forks)", "Model game");

  static AnnotatorDto annotator(String name) {
    return new AnnotatorDto(null, name, null);
  }

  static final AnnotatorDto FTACNIK = annotator("Ftacnik, Lubomir");
  static final AnnotatorDto RIBLI = annotator("Ribli, Zoltan");
  static final AnnotatorDto CHESSBASE = annotator("ChessBase");

  // ── Real games ───────────────────────────────────────────────────────────

  /** A game that was really played, with the header it had. */
  record RealGame(
      String white,
      String black,
      Event event,
      Date date,
      @Nullable Integer round,
      GameResult result,
      String eco,
      @Nullable Integer whiteElo,
      @Nullable Integer blackElo,
      String moves) {}

  static final RealGame OPERA_GAME =
      new RealGame(
          "Morphy, Paul", "Duke of Brunswick and Isouard", PARIS_1858, new Date(1858),
          null, GameResult.WHITE_WINS, "C41", null, null,
          "1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6"
              + " 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6"
              + " 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8#");

  static final RealGame IMMORTAL_GAME =
      new RealGame(
          "Anderssen, Adolf", "Kieseritzky, Lionel", LONDON_1851, new Date(1851, 6, 21),
          null, GameResult.WHITE_WINS, "C33", null, null,
          "1. e4 e5 2. f4 exf4 3. Bc4 Qh4+ 4. Kf1 b5 5. Bxb5 Nf6 6. Nf3 Qh6 7. d3 Nh5 8. Nh4 Qg5"
              + " 9. Nf5 c6 10. g4 Nf6 11. Rg1 cxb5 12. h4 Qg6 13. h5 Qg5 14. Qf3 Ng8 15. Bxf4 Qf6"
              + " 16. Nc3 Bc5 17. Nd5 Qxb2 18. Bd6 Bxg1 19. e5 Qxa1+ 20. Ke2 Na6 21. Nxg7+ Kd8"
              + " 22. Qf6+ Nxf6 23. Be7#");

  static final RealGame GAME_OF_THE_CENTURY =
      new RealGame(
          "Byrne, Donald", "Fischer, Robert J.", ROSENWALD, new Date(1956, 10, 17),
          8, GameResult.BLACK_WINS, "D97", null, null,
          "1. Nf3 Nf6 2. c4 g6 3. Nc3 Bg7 4. d4 O-O 5. Bf4 d5 6. Qb3 dxc4 7. Qxc4 c6 8. e4 Nbd7"
              + " 9. Rd1 Nb6 10. Qc5 Bg4 11. Bg5 Na4 12. Qa3 Nxc3 13. bxc3 Nxe4 14. Bxe7 Qb6"
              + " 15. Bc4 Nxc3 16. Bc5 Rfe8+ 17. Kf1 Be6 18. Bxb6 Bxc4+ 19. Kg1 Ne2+ 20. Kf1 Nxd4+"
              + " 21. Kg1 Ne2+ 22. Kf1 Nc3+ 23. Kg1 axb6 24. Qb4 Ra4 25. Qxb6 Nxd1 26. h3 Rxa2"
              + " 27. Kh2 Nxf2 28. Re1 Rxe1 29. Qd8+ Bf8 30. Nxe1 Bd5 31. Nf3 Ne4 32. Qb8 b5 33. h4 h5"
              + " 34. Ne5 Kg7 35. Kg1 Bc5+ 36. Kf1 Ng3+ 37. Ke1 Bb4+ 38. Kd1 Bb3+ 39. Kc1 Ne2+"
              + " 40. Kb1 Nc3+ 41. Kc1 Rc2#");

  static final RealGame KASPAROV_TOPALOV =
      new RealGame(
          "Kasparov, Garry", "Topalov, Veselin", WIJK_1999, new Date(1999, 1, 20),
          4, GameResult.WHITE_WINS, "B07", 2812, 2700,
          "1. e4 d6 2. d4 Nf6 3. Nc3 g6 4. Be3 Bg7 5. Qd2 c6 6. f3 b5 7. Nge2 Nbd7 8. Bh6 Bxh6"
              + " 9. Qxh6 Bb7 10. a3 e5 11. O-O-O Qe7 12. Kb1 a6 13. Nc1 O-O-O 14. Nb3 exd4"
              + " 15. Rxd4 c5 16. Rd1 Nb6 17. g3 Kb8 18. Na5 Ba8 19. Bh3 d5 20. Qf4+ Ka7 21. Rhe1 d4"
              + " 22. Nd5 Nbxd5 23. exd5 Qd6 24. Rxd4 cxd4 25. Re7+ Kb6 26. Qxd4+ Kxa5 27. b4+ Ka4"
              + " 28. Qc3 Qxd5 29. Ra7 Bb7 30. Rxb7 Qc4 31. Qxf6 Kxa3 32. Qxa6+ Kxb4 33. c3+ Kxc3"
              + " 34. Qa1+ Kd2 35. Qb2+ Kd1 36. Bf1 Rd2 37. Rd7 Rxd7 38. Bxc4 bxc4 39. Qxh8 Rd3"
              + " 40. Qa8 c3 41. Qa4+ Ke1 42. f4 f5 43. Kc1 Rd2 44. Qa7");

  static final RealGame FISCHER_SPASSKY_6 =
      new RealGame(
          "Fischer, Robert J.", "Spassky, Boris V", REYKJAVIK_1972, new Date(1972, 7, 23),
          6, GameResult.WHITE_WINS, "D59", 2785, 2660,
          "1. c4 e6 2. Nf3 d5 3. d4 Nf6 4. Nc3 Be7 5. Bg5 O-O 6. e3 h6 7. Bh4 b6 8. cxd5 Nxd5"
              + " 9. Bxe7 Qxe7 10. Nxd5 exd5 11. Rc1 Be6 12. Qa4 c5 13. Qa3 Rc8 14. Bb5 a6"
              + " 15. dxc5 bxc5 16. O-O Ra7 17. Be2 Nd7 18. Nd4 Qf8 19. Nxe6 fxe6 20. e4 d4 21. f4 Qe7"
              + " 22. e5 Rb8 23. Bc4 Kh8 24. Qh3 Nf8 25. b3 a5 26. f5 exf5 27. Rxf5 Nh7 28. Rcf1 Qd8"
              + " 29. Qg3 Re7 30. h4 Rbb7 31. e6 Rbc7 32. Qe5 Qe8 33. a4 Qd8 34. R1f2 Qe8 35. R2f3 Qd8"
              + " 36. Bd3 Qe8 37. Qe4 Nf6 38. Rxf6 gxf6 39. Rxf6 Kg8 40. Bc4 Kh8 41. Qf4");

  static final List<RealGame> REAL_GAMES =
      List.of(OPERA_GAME, IMMORTAL_GAME, GAME_OF_THE_CENTURY, KASPAROV_TOPALOV, FISCHER_SPASSKY_6);
}
