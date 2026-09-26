package se.yarin.morphy.tools.testdata;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.model.GameTextDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;
import se.yarin.morphy.tools.testdata.Corpus.Event;
import se.yarin.morphy.tools.testdata.Corpus.Person;
import se.yarin.morphy.tools.testdata.Corpus.RealGame;

/**
 * Builds the {@link GameDto} of a test game or guiding text. Everything not set is left out. A spec
 * can also be made from an existing DTO, to change some of it.
 */
final class GameSpec {
  String type = "game";
  @Nullable PlayerDto white;
  @Nullable PlayerDto black;
  @Nullable Integer whiteElo;
  @Nullable Integer blackElo;
  @Nullable TeamDto whiteTeam;
  @Nullable TeamDto blackTeam;
  GameResult result = GameResult.NOT_FINISHED;
  Date date = Date.unset();
  @Nullable String eco;
  @Nullable Integer round;
  @Nullable Integer subRound;
  @Nullable NAG lineEvaluation;
  @Nullable TournamentDto tournament;
  @Nullable SourceDto source;
  @Nullable AnnotatorDto annotator;
  @Nullable String variant;
  @Nullable Map<String, String> extraTags;
  @Nullable GameMovesDto moves = Moves.none();
  @Nullable GameTextDto text;

  static GameSpec game() {
    return new GameSpec();
  }

  /** A guiding text, which is a record in the database like a game but with text instead of moves. */
  static GameSpec text(String contents) {
    GameSpec spec = new GameSpec();
    spec.type = "text";
    spec.moves = null;
    spec.text = new GameTextDto(contents);
    return spec;
  }

  /**
   * A guiding text of an event. The date of a text is the start of its event, which is what the
   * databases give back.
   */
  static GameSpec text(String contents, Event event) {
    return text(contents).event(event).date(event.start());
  }

  /** A real game, with the header it had. */
  static GameSpec real(RealGame g) {
    GameSpec spec = new GameSpec();
    spec.white = person(g.white());
    spec.black = person(g.black());
    spec.whiteElo = g.whiteElo();
    spec.blackElo = g.blackElo();
    spec.result = g.result();
    spec.date = g.date();
    spec.eco = g.eco();
    spec.round = g.round();
    spec.tournament = g.event().dto();
    spec.moves = Moves.of(g.moves());
    return spec;
  }

  /** A spec with everything an existing game says. The id is not kept. */
  static GameSpec from(GameDto g) {
    GameSpec spec = new GameSpec();
    spec.type = g.type();
    spec.white = g.whitePlayer();
    spec.black = g.blackPlayer();
    spec.whiteElo = g.whiteElo();
    spec.blackElo = g.blackElo();
    spec.whiteTeam = g.whiteTeam();
    spec.blackTeam = g.blackTeam();
    spec.result = g.result();
    spec.date = g.date();
    spec.eco = g.eco();
    spec.round = g.round();
    spec.subRound = g.subRound();
    spec.lineEvaluation = g.lineEvaluation();
    spec.tournament = g.tournament();
    spec.source = g.source();
    spec.annotator = g.annotator();
    spec.variant = g.variant();
    spec.extraTags = g.extraTags();
    spec.moves = g.moves();
    spec.text = g.text();
    return spec;
  }

  private static PlayerDto person(String full) {
    int comma = full.indexOf(',');
    return comma < 0
        ? new PlayerDto(null, full, null, null, null, null)
        : new PlayerDto(null, full.substring(0, comma), full.substring(comma + 1).strip(), null, null, null);
  }

  /** The players, with their usual ratings. */
  GameSpec players(Person w, Person b) {
    white = w.dto();
    black = b.dto();
    whiteElo = w.elo() == 0 ? null : w.elo();
    blackElo = b.elo() == 0 ? null : b.elo();
    return this;
  }

  GameSpec elo(@Nullable Integer w, @Nullable Integer b) {
    whiteElo = w;
    blackElo = b;
    return this;
  }

  GameSpec result(GameResult r) {
    result = r;
    return this;
  }

  GameSpec date(Date d) {
    date = d;
    return this;
  }

  GameSpec eco(@Nullable String e) {
    eco = e;
    return this;
  }

  GameSpec round(@Nullable Integer r, @Nullable Integer sub) {
    round = r;
    subRound = sub;
    return this;
  }

  GameSpec event(@Nullable Event e) {
    tournament = e == null ? null : e.dto();
    return this;
  }

  GameSpec tournament(@Nullable TournamentDto t) {
    tournament = t;
    return this;
  }

  GameSpec teams(@Nullable String w, @Nullable String b) {
    whiteTeam = w == null ? null : Corpus.team(w);
    blackTeam = b == null ? null : Corpus.team(b);
    return this;
  }

  GameSpec source(@Nullable SourceDto s) {
    source = s;
    return this;
  }

  GameSpec annotator(@Nullable AnnotatorDto a) {
    annotator = a;
    return this;
  }

  GameSpec moves(GameMovesDto m) {
    moves = m;
    return this;
  }

  GameSpec lineEvaluation(@Nullable NAG nag) {
    lineEvaluation = nag;
    return this;
  }

  /** A Chess960 game, whose moves must have a start position. */
  GameSpec chess960() {
    variant = "Chess960";
    return this;
  }

  GameSpec extraTags(Map<String, String> tags) {
    extraTags = tags;
    return this;
  }

  GameDto build() {
    return new GameDto(
        null, type, null, white, whiteElo, black, blackElo, whiteTeam, blackTeam, result, date,
        eco, round, subRound, lineEvaluation, tournament, source, annotator, null, null, null,
        null, null, variant, null, null, null, null, null, null, null, null, null, moves, text,
        extraTags);
  }
}
