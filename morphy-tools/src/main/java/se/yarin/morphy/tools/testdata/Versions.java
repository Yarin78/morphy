package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;
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
            "v2-headers", "Entity variety and unusual header values", Versions::headers),
        new Version(
            "v3-moves", "Everything the move tree can hold, but no annotations", Versions::moveTree),
        new Version(
            "v4-annotations",
            "Simple annotations, guiding texts and many players and events",
            Versions::annotations),
        new Version(
            "v5-edits",
            "Replaced games, changed and removed entities",
            Versions::edits),
        new Version(
            "v6-everything",
            "Every kind of annotation, and a second round of edits",
            Versions::everything));
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
                i == 0 ? "first" : null,
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
            + " rounds, type, time control), three sources with a publisher and a date, two"
            + " annotators, and game tags on four of them (four different tags; a tag has only an"
            + " English title here, and one of them has an accent and one has punctuation).",
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
            if (i % 3 == 0) {
              spec.gameTag(Corpus.GAME_TAGS.get(i / 3 % Corpus.GAME_TAGS.size()));
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
            + " acute accent, which are in Latin-1. The first of them has a game tag that no other game"
            + " has.",
        () -> {
          s.add(
              GameSpec.game()
                  .players(Corpus.GRUENFELD, Corpus.BOENSCH)
                  .result(GameResult.DRAW)
                  .date(new Date(1925))
                  .gameTag("Puzzle of the day")
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
            + " as its publication date from 2cbh. In cbh, a name is cut at 30"
            + " characters (a first name at 20), a publisher at 16, and characters outside Latin-1 become"
            + " '?', so the names here stay within that.");

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

  // ── v3: the move tree ────────────────────────────────────────────────────

  private static final String STANDARD_960 =
      "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1";

  private static GameSpec between(Person white, Person black, GameResult result, Event event, int day) {
    return GameSpec.game()
        .players(white, black)
        .result(result)
        .date(new Date(event.start().year(), event.start().month(), day))
        .event(event);
  }

  private static void moveTree(Session s) {
    Random random = new Random(303);
    Event event = Corpus.NORWAY_CHESS;

    s.feature(
        "Variations: one nested three deep (Italian game), one nested six deep, and one move with"
            + " five alternatives.",
        () -> {
          s.add(
              between(Corpus.CARLSEN, Corpus.CARUANA, GameResult.DRAW, event, 1)
                  .eco("C54")
                  .moves(
                      Moves.of(
                          "1. e4 e5 2. Nf3 Nc6 3. Bc4 (3. Bb5 a6 (3... Nf6 4. O-O Nxe4 (4... Bc5)) 4. Ba4 Nf6)"
                              + " 3... Bc5 4. c3 (4. b4 Bxb4 5. c3 Ba5) 4... Nf6 5. d4 exd4 6. cxd4 Bb4+")));
          s.add(
              between(Corpus.DING, Corpus.GUKESH, GameResult.WHITE_WINS, event, 2)
                  .moves(
                      Moves.of(
                          "1. e4 (1. d4 (1. c4 (1. Nf3 (1. b3 (1. f4 (1. g3 g6 (1... d5)))))))"
                              + " 1... e5 2. Nf3 Nc6")));
          s.add(
              between(Corpus.NAKAMURA, Corpus.FIROUZJA, GameResult.BLACK_WINS, event, 3)
                  .moves(
                      Moves.of(
                          "1. e4 e5 2. Nf3 (2. Nc3) (2. Bc4) (2. f4) (2. d4) (2. Qh5) 2... Nc6 3. Bb5")));
          s.add(
              between(Corpus.ANAND, Corpus.ARONIAN, GameResult.DRAW, event, 4)
                  .moves(Moves.randomWithVariations(random, 40, 40)));
        });

    s.feature(
        "Promotions: to all four pieces as variations from a setup position (white), and black"
            + " capturing while underpromoting to a knight, with checks.",
        () -> {
          s.add(
              between(Corpus.GIRI, Corpus.DUDA, GameResult.WHITE_WINS, event, 5)
                  .moves(
                      Moves.of(
                          "1. a8=Q+ (1. a8=R+) (1. a8=B) (1. a8=N) 1... Kg7 2. Qa7+ Kg6 3. Qb6+ Kf5",
                          "7k/P7/8/8/8/8/8/K7 w - - 0 1", false)));
          s.add(
              between(Corpus.SO, Corpus.DUBOV, GameResult.BLACK_WINS, event, 6)
                  .moves(
                      Moves.of(
                          "1... axb1=N+ (1... axb1=Q) (1... axb1=R) (1... axb1=B) 2. Kc2 Na3+ 3. Kb3 Nb5",
                          "4k3/8/8/8/8/8/p2K4/1R6 b - - 0 1", false)));
        });

    s.feature(
        "Castling: both sides for both colours, from a position with the rooks at home; and three"
            + " en passant captures (two by white, one by black) in one game.",
        () -> {
          s.add(
              between(Corpus.VACHIER, Corpus.RAPPORT, GameResult.DRAW, event, 7)
                  .moves(
                      Moves.of(
                          "1. O-O O-O-O 2. Rfe1 Kb8", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", false)));
          s.add(
              between(Corpus.MAMEDYAROV, Corpus.ERIGAISI, GameResult.DRAW, event, 8)
                  .moves(
                      Moves.of(
                          "1. O-O-O O-O 2. Kb1 Kg7", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", false)));
          s.add(
              between(Corpus.PRAGG, Corpus.NEPOMNIACHTCHI, GameResult.DRAW, event, 9)
                  .moves(
                      Moves.of(
                          "1. e4 a6 2. e5 d5 3. exd6 cxd6 4. a4 h5 5. a5 h4 6. g4 hxg3 7. Nf3 b5 8. axb6 Nc6")));
        });

    s.feature(
        "Endings: stalemate from a setup position, and mate in two (fool's mate).",
        () -> {
          s.add(
              between(Corpus.CARLSEN, Corpus.DING, GameResult.DRAW, event, 10)
                  .moves(Moves.of("1. Qf7", "7k/8/6K1/8/8/8/8/5Q2 w - - 0 1", false)));
          s.add(
              between(Corpus.GUKESH, Corpus.CARUANA, GameResult.BLACK_WINS, event, 11)
                  .moves(Moves.of("1. f3 e5 2. g4 Qh4#")));
        });

    s.feature(
        "Start positions: a middlegame position with black to move at move 5 (a setup position),"
            + " and a Chess960 game from position 'bbqnnrkr' with 40 random plies.",
        () -> {
          s.add(
              "setup",
              between(Corpus.FIROUZJA, Corpus.NAKAMURA, GameResult.WHITE_WINS, event, 12)
                  .moves(
                      Moves.of(
                          "5... a6 6. Be3 e5 7. Nb3 Be6 8. f3 Be7 9. Qd2 O-O 10. O-O-O Nbd7",
                          "rnbqkb1r/pp2pppp/3p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R b KQkq - 2 5",
                          false)));
          s.add(
              "chess960",
              between(Corpus.ARONIAN, Corpus.SO, GameResult.DRAW, event, 13)
                  .chess960()
                  .moves(Moves.randomFrom(random, STANDARD_960, true, 40)));
        });

    s.feature(
        "A long game: 300 plies. And a game of a single move.",
        () -> {
          s.add(
              "long",
              between(Corpus.GIRI, Corpus.RAPPORT, GameResult.DRAW, event, 14)
                  .moves(Moves.random(random, 300)));
          s.add(
              between(Corpus.DUDA, Corpus.ANAND, GameResult.NOT_FINISHED, event, 15)
                  .moves(Moves.of("1. e4")));
        });

    s.feature(
        "Symbols: a Ruy Lopez with one move symbol, evaluation or other NAG on most moves, and"
            + " moves with one NAG of each of the three kinds ChessBase has (move symbol, move prefix,"
            + " line evaluation); it holds no more than one of each kind.",
        () -> {
          s.add(
              "symbols",
              between(Corpus.NEPOMNIACHTCHI, Corpus.CARLSEN, GameResult.BLACK_WINS, event, 16)
                  .eco("C92")
                  .moves(
                      Moves.of(
                          "1. e4 $1 e5 $2 2. Nf3 $3 Nc6 $4 3. Bb5 $5 a6 $6 4. Ba4 $10 Nf6 $11 5. O-O $13 Be7 $14"
                              + " 6. Re1 $15 b5 $16 7. Bb3 $17 d6 $18 8. c3 $19 O-O $22 9. h3 $32 Nb8 $36"
                              + " 10. d4 $40 Nbd7 $132 11. Nbd2 $138 Bb7 $140 12. Bc2 $1 $14 $140 Re8 $2 $15 $142"
                              + " 13. Nf1 Bf8 14. Ng3 g6")));
        });
  }

  // ── v4: light annotations, guiding texts, bulk ───────────────────────────

  private static final String RUY_LOPEZ =
      "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 8. c3 O-O";

  private static void annotations(Session s) {
    Random random = new Random(404);
    Event event = Corpus.SINQUEFIELD;

    s.feature(
        "Text commentary: after moves, before moves (a [%pre] tag), in German (a [%post:GER] tag),"
            + " and inside variations.",
        () -> {
          s.add(
              "commentary",
              between(Corpus.CARLSEN, Corpus.NAKAMURA, GameResult.WHITE_WINS, event, 1)
                  .annotator(Corpus.FTACNIK)
                  .moves(
                      Moves.annotated(
                          "1. e4 { The most popular first move. } e5 { Symmetrical. } 2. Nf3 Nc6 3. Bb5"
                              + " { [%post:GER Die Spanische Partie] } (3. Bc4 { The Italian Game. } Bc5)"
                              + " 3... a6 { [%pre The Morphy Defence] } 4. Ba4 Nf6 5. O-O Be7",
                          "TextAfterMove", "TextBeforeMove")));
          s.add(
              between(Corpus.CARUANA, Corpus.DING, GameResult.DRAW, event, 2)
                  .annotator(Corpus.RIBLI)
                  .moves(
                      Moves.annotated(
                          "1. d4 { Queen's pawn. } d5 2. c4 { [%pre The Queen's Gambit] } e6",
                          "TextAfterMove", "TextBeforeMove")));
        });

    s.feature(
        "Graphics: coloured squares and arrows in green, red and yellow, with and without text.",
        () -> {
          s.add(
              "graphics",
              between(Corpus.GIRI, Corpus.ARONIAN, GameResult.DRAW, event, 3)
                  .moves(
                      Moves.annotated(
                          "1. e4 { [%csl Ge4,Ye5] [%cal Ge2e4,Rd1h5] } e5 2. Nf3 { [%cal Gg1f3,Gb1c3] } Nc6"
                              + " 3. Bb5 { [%csl Rb5] The pin. } a6 4. Ba4 { [%cal Yb5a4] } Nf6",
                          "GraphicalSquares", "GraphicalArrows", "TextAfterMove")));
        });

    s.feature(
        "Symbols, text and graphics on the same move, in a game with variations.",
        () -> {
          s.add(
              between(Corpus.GUKESH, Corpus.ERIGAISI, GameResult.BLACK_WINS, event, 4)
                  .annotator(Corpus.CHESSBASE)
                  .source(Corpus.CBM_211)
                  .moves(
                      Moves.annotated(
                          "1. e4 $1 { [%csl Ge4] Best by test. } c5 $10 2. Nf3 (2. Nc3 $14 { [%cal Gc3d5] }"
                              + " Nc6 3. g3 $5) 2... d6 $15 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 $2",
                          "Symbol", "GraphicalSquares", "GraphicalArrows", "TextAfterMove")));
        });

    s.feature(
        "Games with a line evaluation and no result (the game is a position with a verdict).",
        () -> {
          String moves = "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Nxe4 6. d4 b5 7. Bb3 d5 8. dxe5 Be6";
          s.add(
              between(Corpus.SO, Corpus.DUBOV, GameResult.NOT_FINISHED, event, 5)
                  .lineEvaluation(se.yarin.chess.NAG.WHITE_SLIGHT_ADVANTAGE)
                  .moves(Moves.of(moves)));
          s.add(
              between(Corpus.SO, Corpus.DUBOV, GameResult.NOT_FINISHED, event, 5)
                  .lineEvaluation(se.yarin.chess.NAG.UNCLEAR)
                  .moves(Moves.of(moves + " 9. c3")));
          s.add(
              between(Corpus.SO, Corpus.DUBOV, GameResult.NOT_FINISHED, event, 5)
                  .lineEvaluation(se.yarin.chess.NAG.BLACK_MODERATE_ADVANTAGE)
                  .moves(Moves.of(moves + " 9. c3 Bc5")));
        });

    s.feature(
        "Guiding texts. The first has an event, an annotator and a source; the second an event and"
            + " an annotator; the third nothing. An event of a text has only a title and a start"
            + " date, and no game refers to it.",
        () -> {
          s.add(
              GameSpec.text(
                      "<p>The Ruy Lopez: one of the oldest openings, still played at the top.</p>",
                      Corpus.NOTES_OPENINGS)
                  .annotator(Corpus.FTACNIK)
                  .source(Corpus.NOTES_SOURCE));
          s.add(
              "text-sicilian",
              GameSpec.text(
                      "<p>A short text about the Sicilian Defence.</p><p>A second paragraph.</p>",
                      Corpus.NOTES_ENDGAMES)
                  .annotator(Corpus.RIBLI));
          s.add(GameSpec.text("<p>A text with nothing else set.</p>"));
        });

    s.feature(
        "About 150 more games between 120 made-up players in 30 made-up events, so that the"
            + " entity trees get some depth. Some players and events have a single game. Every"
            + " fifth game has a game tag, from a set of five.",
        () -> {
          List<Person> players = Bulk.players(new Random(4040), 120);
          List<Event> events = Bulk.events(new Random(4041), 30);
          for (int i = 0; i < 150; i++) {
            Event bulkEvent = events.get(random.nextInt(events.size()));
            Person white = players.get(random.nextInt(players.size()));
            Person black = players.get(random.nextInt(players.size()));
            while (black.equals(white)) {
              black = players.get(random.nextInt(players.size()));
            }
            GameSpec spec =
                GameSpec.game()
                    .players(white, black)
                    .elo(white.elo() + random.nextInt(21) - 10, black.elo() + random.nextInt(21) - 10)
                    .result(RESULTS[random.nextInt(3)])
                    .date(new Date(2021, bulkEvent.start().month(), 1 + random.nextInt(27)))
                    .eco(ECOS[random.nextInt(ECOS.length)])
                    .round(1 + random.nextInt(bulkEvent.rounds()), null)
                    .event(bulkEvent)
                    .moves(Moves.random(random, 20 + random.nextInt(60)));
            if (i % 3 == 0) {
              spec.source(i % 2 == 0 ? Corpus.CBM_210 : Corpus.TWIC_1450);
            }
            if (i % 4 == 0) {
              spec.annotator(i % 8 == 0 ? Corpus.FTACNIK : Corpus.RIBLI);
            }
            if (i % 5 == 0) {
              spec.gameTag(Corpus.GAME_TAGS.get(i / 5 % Corpus.GAME_TAGS.size()));
            }
            s.add(i == 75 ? "bulk-middle" : i == 149 ? "bulk-last" : null, spec);
          }
        });
  }

  // ── v5: edits ────────────────────────────────────────────────────────────

  /** Copies of an event with some fields changed. */
  private static TournamentDto event(
      TournamentDto t, String title, Integer category, Integer rounds, Boolean complete) {
    return new TournamentDto(
        t.id(), title, t.startDate(), t.endDate(), t.place(), t.nation(), category, null, rounds,
        t.type(), t.timeControl(), t.typeCombined(), complete, t.teamTournament(),
        t.tiebreakRules(), t.latitude(), t.longitude(), t.gameCount());
  }

  private static SourceDto source(SourceDto s, String title, String publisher) {
    return new SourceDto(
        s.id(), title, publisher, s.publication(), s.date(), s.version(), s.quality(), s.gameCount());
  }

  private static TeamDto team(TeamDto t, String title, Integer year, String nation) {
    return new TeamDto(t.id(), title, t.teamNumber(), t.season(), year, nation, t.gameCount());
  }

  private static GameTagDto gameTag(GameTagDto t, String english) {
    return new GameTagDto(
        t.id(), english, t.languages(), t.languageCount(), english, t.germanTitle(), t.frenchTitle(),
        t.spanishTitle(), t.italianTitle(), t.dutchTitle(), t.slovenianTitle(), t.resTitle(),
        t.gameCount());
  }

  private static PlayerDto player(PlayerDto p, String last, String first) {
    return new PlayerDto(p.id(), last, first, p.gameCount(), p.fideId(), p.chessBaseId());
  }

  /** The number of games each name has, by the names that the function gives a game. */
  private static Map<String, Integer> counts(
      List<GameDto> games, java.util.function.Function<GameDto, List<String>> names) {
    Map<String, Integer> counts = new HashMap<>();
    for (GameDto g : games) {
      for (String name : names.apply(g)) {
        if (!name.isEmpty()) {
          counts.merge(name, 1, Integer::sum);
        }
      }
    }
    return counts;
  }

  private static void edits(Session s) {
    Random random = new Random(505);
    List<GameDto> games = s.state.expected;

    s.feature(
        "Replaced games in every position and every way: the first game with longer moves, a"
            + " 300-ply game with a 10-ply one, a game with different moves of the same length,"
            + " and the last game with other players and event.",
        () -> {
          s.replace("first", s.spec("first").moves(Moves.random(random, 90)));
          s.replace("long", s.spec("long").moves(Moves.random(random, 10)));
          GameDto middle = games.get(s.id("bulk-middle") - 1);
          int plies = middle.moves().pgn().split(" ").length;
          s.replace(
              "bulk-middle",
              s.spec("bulk-middle").moves(Moves.random(random, Math.max(20, plies / 3))));
          s.replace(
              "bulk-last",
              s.spec("bulk-last")
                  .players(Corpus.ERIGAISI, Corpus.PRAGG)
                  .event(Corpus.CANDIDATES));
        });

    s.feature(
        "Replaced start positions: a game from the standard position replaced by one from a setup"
            + " position, and a Chess960 game replaced by a regular one.",
        () -> {
          s.replace(
              "first",
              s.spec("first")
                  .moves(
                      Moves.of(
                          "1. Qf7", "7k/8/6K1/8/8/8/8/5Q2 w - - 0 1", false))
                  .result(GameResult.DRAW));
          GameSpec regular = s.spec("chess960").moves(Moves.random(random, 30));
          regular.variant = null;
          s.replace("chess960", regular);
        });

    s.feature(
        "Replaced annotated games: the graphics game replaced by one with no annotations, and the"
            + " symbols game by one with commentary.",
        () -> {
          s.replace("graphics", s.spec("graphics").moves(Moves.of("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6")));
          s.replace(
              "symbols",
              s.spec("symbols")
                  .moves(
                      Moves.annotated(
                          "1. e4 { Once again. } e5 2. Nf3 Nc6 { [%pre Both sides develop] } 3. Bb5 a6",
                          "TextAfterMove", "TextBeforeMove")));
          if (s.hasTexts()) {
            s.replace(
                "text-sicilian",
                GameSpec.text("<p>The text about the Sicilian was rewritten, with a new event.</p>", Corpus.NOTES_OPENINGS));
          }
        });

    s.feature(
        "Entities changed, and every game that holds the entity with it:"
            + " a player's first name completed (sort position kept, "
            + "a last name changed to sort last), a real player's name spelled differently, an event"
            + " renamed with a new category and round count and marked complete, a source renamed with"
            + " a new publisher, an annotator renamed, a team renamed with a year and nation, and a game"
            + " tag renamed. In a PGN file there are no entities, so the games that hold them are"
            + " rewritten (except for the game tag, which a PGN file can't hold).",
        () -> {
        Map<String, Integer> players =
            counts(games, g -> List.of(Verifier.name(g.whitePlayer()), Verifier.name(g.blackPlayer())));
        // A bulk player with a first name that is only an initial, and one with several games
        String initial =
            players.keySet().stream()
                .filter(n -> n.endsWith(", J.") || n.endsWith(", M.") || n.endsWith(", A."))
                .sorted()
                .findFirst()
                .orElseThrow();
        String lastNameChange =
            players.entrySet().stream()
                .filter(e -> e.getValue() >= 2 && e.getKey().contains(", ") && !e.getKey().equals(initial))
                .map(Map.Entry::getKey)
                .sorted()
                .skip(3)
                .findFirst()
                .orElseThrow();
        s.updatePlayer(
            initial,
            p -> player(p, p.lastName(), p.firstName().replace("J.", "Johan").replace("M.", "Maria").replace("A.", "Anders")));
        s.updatePlayer(lastNameChange, p -> player(p, "Zwart", p.firstName()));
        s.updatePlayer("Anand, Viswanathan", p -> player(p, "Anand", "Vishy"));
        s.updateEvent("Norway Chess 2023", t -> event(t, "Altibox Norway Chess 2023", 22, 10, true));
        s.updateSource("CBM 210", t -> source(t, "CBM 210 (revised)", "ChessBase GmbH"));
        s.updateAnnotator("Ribli, Zoltan", t -> new AnnotatorDto(t.id(), "Ribli, Zolt\u00e1n", t.gameCount()));
        s.updateTeam("India 2", t -> team(t, "India B", 2022, "IND"));
        s.updateGameTag("Opening trap", t -> gameTag(t, "Opening traps"));
        });

    s.note(
        "A rename that would make one player equal to another (Giri, Anish into Gukesh, D) is"
            + " refused, and nothing changes. There is nothing to refuse in a PGN file.");
    s.expectRefusedMerge("Giri, Anish", "Gukesh, D");

    s.note(
        "Entities that lose their last game are removed: a player, an event with a single game, a game"
            + " tag, and the source of the six Olympiad games (Chess Archive), which all move to other"
            + " entities. Removed entities are not found by a search any more.");
    Map<String, Integer> events =
        counts(games, g -> List.of(g.tournament() == null ? "" : g.tournament().title()));
    Map<String, Integer> whites = counts(games, g -> List.of(Verifier.name(g.whitePlayer()), Verifier.name(g.blackPlayer())));
    int soleGame = 0;
    for (int id = 1; id <= games.size() && soleGame == 0; id++) {
      GameDto g = games.get(id - 1);
      if (g.type().equals("game") && whites.getOrDefault(Verifier.name(g.whitePlayer()), 0) == 1) {
        soleGame = id;
      }
    }
    int soleEventGame = 0;
    for (int id = 1; id <= games.size() && soleEventGame == 0; id++) {
      GameDto g = games.get(id - 1);
      if (g.type().equals("game") && g.tournament() != null && events.getOrDefault(g.tournament().title(), 0) == 1) {
        soleEventGame = id;
      }
    }
    int soleTagGame = 0;
    for (int id = 1; id <= games.size() && soleTagGame == 0; id++) {
      if ("Puzzle of the day".equals(Verifier.gameTag(games.get(id - 1).gameTag()))) {
        soleTagGame = id;
      }
    }
    final int soleWhite = soleGame;
    final int soleEvent = soleEventGame;
    final int soleTag = soleTagGame;
    s.feature(
        "The only game of a player, replaced so that it has another white player; the only game of an"
            + " event, replaced so that it is played in another event; the only game with a game tag,"
            + " replaced without one; and the six games of the source Chess Archive, replaced so that"
            + " they have another source.",
        () -> {
          s.replace(
              soleWhite,
              s.spec(soleWhite).players(Corpus.NAKAMURA, Corpus.CARLSEN));
          s.replace(soleEvent, s.spec(soleEvent).event(Corpus.CANDIDATES));
          s.replace(soleTag, s.spec(soleTag).gameTag(null));
          for (int id = 1; id <= games.size(); id++) {
            GameDto g = games.get(id - 1);
            if (g.source() != null && "Chess Archive".equals(g.source().title())) {
              s.replace(id, s.spec(id).source(Corpus.TWIC_1450));
            }
          }
        });

    s.feature(
        "Two games with players, an event and a source that are new, added after the removals, so"
            + " that they can take the places of the removed entities.",
        () -> {
          Event event = new Event("Recycling Open 2023", "Odense", "DEN", new Date(2023, 9, 4), null, 7, "swiss", null);
          Person hollander = new Person("Hollander", "Piet", 2100);
          Person idris = new Person("Idris", "Amira", 2050);
          for (int i = 0; i < 2; i++) {
            s.add(
                GameSpec.game()
                    .players(i == 0 ? hollander : idris, i == 0 ? idris : hollander)
                    .result(RESULTS[i])
                    .date(new Date(2023, 9, 4 + i))
                    .round(1 + i, null)
                    .event(event)
                    .source(Corpus.source("Recycling Bulletin", "Odense Club", new Date(2023, 9, 10)))
                    .annotator(Corpus.annotator("Hollander, Piet"))
                    .moves(Moves.random(random, 40)));
          }
        });
  }

  // ── v6: everything ───────────────────────────────────────────────────────

  /** The moves of {@link #RUY_LOPEZ}, without move numbers. */
  private static final String RUY_LOPEZ_LONG =
      RUY_LOPEZ + " 9. h3 Nb8 10. d4 Nbd7 11. Nbd2 Bb7 12. Bc2 Re8";

  /**
   * Puts a comment after every move.
   *
   * @param comment gets the number of the move, from 0, and gives the text of the comment
   */
  private static String perMove(String moves, java.util.function.IntFunction<String> comment) {
    StringBuilder sb = new StringBuilder();
    int ply = 0;
    for (String token : moves.split(" ")) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(token);
      if (!token.matches("\\d+\\.")) {
        sb.append(" { ").append(comment.apply(ply++)).append(" }");
      }
    }
    return sb.toString();
  }

  private static String clock(int seconds) {
    return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }

  private static String sentence(Random random) {
    String[] words = {
      "the", "pawn", "structure", "favours", "white", "because", "knight", "outpost", "bishop",
      "pair", "rook", "endgame", "king", "safety", "initiative", "tempo", "central", "control",
      "weak", "square", "d5", "plan", "exchange", "sacrifice", "über", "café", "naïve",
      "søren", "ångström", "prophylaxis", "compensation"
    };
    StringBuilder sb = new StringBuilder();
    int length = 6 + random.nextInt(10);
    for (int i = 0; i < length; i++) {
      sb.append(i == 0 ? "" : " ").append(words[random.nextInt(words.length)]);
    }
    return sb.substring(0, 1).toUpperCase() + sb.substring(1) + ".";
  }

  private static void everything(Session s) {
    Random random = new Random(606);
    Event event = Corpus.WCH_2021;

    s.feature(
        "Every kind of annotation on the first moves of a Ruy Lopez: clocks, time spent, engine"
            + " evaluations (a score and a mate), critical position, pawn structure, piece path, web"
            + " link, video time, time control, variation colour, medals, and text in German and French.",
        () ->
            s.add(
                "matrix",
                between(Corpus.NEPOMNIACHTCHI, Corpus.CARLSEN, GameResult.BLACK_WINS, event, 1)
                    .annotator(Corpus.CHESSBASE)
                    .source(Corpus.CBM_211)
                    .gameTag("Model game")
                    .moves(
                        Moves.annotated(
                            "1. e4 { [%clk 1:59:58] [%emt 0:00:02] [%eval +0.30/22] } e5 { [%clk 1:59:57]"
                                + " [%emt 0:00:03] [%eval +0.25/22] } 2. Nf3 { [%crit opening] [%pawnstruct 3] }"
                                + " Nc6 { [%path e5 3] } 3. Bb5 { [%vst 1234] [%weblink \"https://en.wikipedia.org/wiki/Ruy_Lopez\""
                                + " \"Ruy Lopez\"] } a6 { [%tc 90m/40+30m] [%varcolor #FF8800 ML] }"
                                + " 4. Ba4 { [%medal best,novelty] } Nf6"
                                + " 5. O-O Be7 { [%eval #-3/25] [%post:GER Ein Kommentar] [%post:FRA Un commentaire] }",
                            "WhiteClock", "BlackClock", "TimeSpent", "ComputerEvaluation", "CriticalPosition",
                            "PawnStructure", "PiecePath", "WebLink", "VideoStreamTime", "TimeControl",
                            "VariationColor", "Medal", "TextAfterMove"))));

    s.feature(
        "A game with a clock and a time-spent value on every move, as in a game from a server,"
            + " with a time control of 5 minutes and 3 seconds a move.",
        () -> {
          int[] left = {300, 300};
          String moves =
              perMove(
                  RUY_LOPEZ_LONG,
                  ply -> {
                    int side = ply % 2;
                    int used = 1 + random.nextInt(14);
                    left[side] = Math.max(1, left[side] - used + 3);
                    return "[%clk " + clock(left[side]) + "] [%emt " + clock(used) + "]"
                        + (ply == 0 ? " [%tc (5m+3s)]" : "");
                  });
          s.add(
              "clocks",
              between(Corpus.DING, Corpus.CARUANA, GameResult.DRAW, Corpus.WORLD_BLITZ, 2)
                  .moves(Moves.annotated(moves, "WhiteClock", "BlackClock", "TimeSpent", "TimeControl")));
        });

    s.feature(
        "A game with an engine evaluation on every move: scores for and against white, and a mate.",
        () -> {
          String[] scores = {
            "+0.31/18", "+0.25/18", "+0.40/19", "+0.35/19", "+0.52/20", "+0.48/20", "+0.75/21",
            "+0.60/21", "+1.10/22", "-0.20/22", "-0.75/23", "#5/30", "#3/40", "#-4/35"
          };
          String moves = perMove(RUY_LOPEZ_LONG, ply -> "[%eval " + scores[ply % scores.length] + "]");
          s.add(
              "evaluations",
              between(Corpus.GUKESH, Corpus.RAPPORT, GameResult.WHITE_WINS, event, 3)
                  .moves(Moves.annotated(moves, "ComputerEvaluation")));
        });

    s.feature(
        "Text commentary in five languages, before and after moves, one language of each on a move.",
        () ->
            s.add(
                "languages",
                between(Corpus.GIRI, Corpus.DUBOV, GameResult.DRAW, event, 4)
                    .moves(
                        Moves.annotated(
                            "1. e4 { [%post:ENG Kings pawn.] } e5 { [%post:GER Königsbauer.] } 2. Nf3"
                                + " { [%post:FRA Le cavalier.] } Nc6 { [%post:ESP El caballo.] } 3. Bb5"
                                + " { [%post:ITA L'alfiere.] } a6 { [%pre:GER Vorher.] } 4. Ba4",
                            "TextAfterMove", "TextBeforeMove"))));

    s.feature(
        "Graphics in all the colours that are kept (green, red, yellow): many squares and arrows on one"
            + " move, and on many moves.",
        () ->
            s.add(
                "graphics-many",
                between(Corpus.SO, Corpus.ARONIAN, GameResult.DRAW, event, 5)
                    .moves(
                        Moves.annotated(
                            "1. e4 { [%csl Ga1,Gb2,Gc3,Gd4,Ye5,Yf6,Yg7,Yh8,Ra8,Rb7] [%cal Ge2e4,Gd2d4,Yg1f3,Yb1c3,Rf1c4,Rc1f4] }"
                                + " e5 { [%csl Ye5] } 2. Nf3 { [%cal Gg1f3] } Nc6 { [%cal Yb8c6,Rd8h4] }",
                            "GraphicalSquares", "GraphicalArrows"))));

    s.feature(
        "Annotations inside nested variations, together with symbols and text, and on a setup position,"
            + " and on a Chess960 game.",
        () -> {
          s.add(
              "nested",
              between(Corpus.ERIGAISI, Corpus.PRAGG, GameResult.WHITE_WINS, event, 6)
                  .moves(
                      Moves.annotated(
                          "1. e4 $1 { [%eval +0.30/20] } c5 (1... e5 $10 { [%clk 1:00:00] } 2. Nf3 (2. Nc3 $14"
                              + " { [%csl Gc3] } Nf6 (2... Nc6 { [%eval +0.10/18] })) 2... Nc6 { Solid. }) 2. Nf3"
                              + " { [%cal Gg1f3] } d6 { [%medal tactics] } 3. d4 cxd4 4. Nxd4 Nf6",
                          "ComputerEvaluation", "BlackClock", "GraphicalSquares", "Symbol", "TextAfterMove",
                          "Medal")));
          s.add(
              "setup-annotated",
              between(Corpus.FIROUZJA, Corpus.NAKAMURA, GameResult.BLACK_WINS, event, 7)
                  .moves(
                      Moves.annotated(
                          "5... a6 { [%eval +0.20/20] Najdorf. } 6. Be3 { [%cal Gc1e3] } e5 7. Nb3 Be6",
                          "rnbqkb1r/pp2pppp/3p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R b KQkq - 2 5",
                          false,
                          "ComputerEvaluation", "GraphicalArrows", "TextAfterMove")));
          s.add(
              "960-annotated",
              between(Corpus.VACHIER, Corpus.DUDA, GameResult.DRAW, event, 8)
                  .chess960()
                  .moves(
                      Moves.annotated(
                          "1. Nc3 { [%cal Gd1c3] Chess960. } Nc6 { [%csl Gc6] } 2. Nd3 Nd6",
                          STANDARD_960,
                          true,
                          "GraphicalArrows", "GraphicalSquares", "TextAfterMove")));
        });

    s.feature(
        "A long commentary: about 8000 characters of text on a few moves, with accented letters and"
            + " quotation marks, and many text annotations, well below what v1 holds in one game.",
        () -> {
          StringBuilder essay = new StringBuilder();
          while (essay.length() < 2500) {
            essay.append(sentence(random)).append(' ');
          }
          String text = essay.toString().trim();
          s.add(
              "essay",
              between(Corpus.CARUANA, Corpus.NEPOMNIACHTCHI, GameResult.DRAW, event, 9)
                  .moves(
                      Moves.annotated(
                          "1. e4 { \"" + text + "\" } e5 { " + text + " } 2. Nf3 { " + text + " } Nc6",
                          "TextAfterMove")));
        });

    // ── a second round of edits ────────────────────────────────────────────

    s.feature(
        "Annotated games replaced: the commentary game from v4 replaced by a plain one, a plain game by a"
            + " heavily annotated one (its annotations grow), and the clocks game by one with other"
            + " clocks (they change, and stay about the same size). The games with annotations from"
            + " earlier in this version are left as they are.",
        () -> {
          s.replace("commentary", s.spec("commentary").moves(Moves.of("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6")));
          String moves = perMove(RUY_LOPEZ_LONG, ply -> "[%emt 0:00:0" + (ply % 10) + "] [%eval +0." + (10 + ply) + "/20]");
          s.replace("first", s.spec("first").moves(Moves.annotated(moves, "ComputerEvaluation", "TimeSpent")));
          String other = perMove(RUY_LOPEZ_LONG, ply -> "[%clk 0:0" + (9 - ply % 9) + ":1" + (ply % 10) + "]");
          s.replace("clocks", s.spec("clocks").moves(Moves.annotated(other, "WhiteClock", "BlackClock")));
        });

    s.feature(
        "The entities of the two games added in v5 removed again, by replacing the games with other"
            + " players, event, source and annotator; and two games added with new ones after that, to"
            + " reuse what was freed.",
        () -> {
          for (int id = 1; id <= s.gameCount(); id++) {
            GameDto g = s.state.expected.get(id - 1);
            if (g.tournament() != null && "Recycling Open 2023".equals(g.tournament().title())) {
              s.replace(
                  id,
                  s.spec(id)
                      .players(Corpus.GIRI, Corpus.SO)
                      .event(Corpus.SINQUEFIELD)
                      .source(Corpus.CBM_210)
                      .annotator(Corpus.FTACNIK));
            }
          }
          Event event2 = new Event("Second Life Cup 2023", "Ribe", "DEN", new Date(2023, 10, 2), null, 5, "tourn", null);
          Person a = new Person("Vindfeldt", "Kirsten", 2200);
          Person b = new Person("Aagaard", "Jens", 2250);
          for (int i = 0; i < 2; i++) {
            s.add(
                GameSpec.game()
                    .players(i == 0 ? a : b, i == 0 ? b : a)
                    .result(RESULTS[2 - i])
                    .date(new Date(2023, 10, 2 + i))
                    .round(1 + i, null)
                    .event(event2)
                    .source(Corpus.source("Second Life Notes", "Ribe Club", new Date(2023, 10, 20)))
                    .annotator(Corpus.annotator("Vindfeldt, Kirsten"))
                    .moves(Moves.random(random, 36)));
          }
        });

    s.feature(
        "Changes to entities once more, this time to ones from the earlier changes: a player renamed"
            + " again, the event renamed in v5 renamed back, a team of the Olympiad renamed, and a game"
            + " tag given titles in German and French (only the tag has them, the games say no more"
            + " than the English title).",
        () -> {
          s.updatePlayer("Anand, Vishy", p -> new PlayerDto(p.id(), "Anand", "Viswanathan", p.gameCount(), p.fideId(), p.chessBaseId()));
          s.updateEvent("Altibox Norway Chess 2023", t -> event(t, "Norway Chess 2023", t.category(), t.rounds(), t.complete()));
          s.updateTeam("Norway", t -> team(t, "Norway 1", t.year(), t.nation()));
          s.updateGameTag(
              "Strat\u00e9gie",
              t ->
                  new GameTagDto(
                      t.id(), t.title(), t.languages(), t.languageCount(), t.englishTitle(),
                      "Strategie", "Strat\u00e9gie", t.spanishTitle(), t.italianTitle(), t.dutchTitle(),
                      t.slovenianTitle(), t.resTitle(), t.gameCount()));
        });
  }
}
