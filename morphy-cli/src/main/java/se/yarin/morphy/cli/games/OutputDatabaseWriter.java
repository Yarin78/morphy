package se.yarin.morphy.cli.games;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.cb2.Database2Cbh;
import se.yarin.morphy.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Writes matched games into a new output database, in whatever format its extension names (.cbh,
 * .2cbh or .pgn) via the {@link Database} facade.
 */
public class OutputDatabaseWriter extends GameConsumerBase {
  private static final Logger log = LoggerFactory.getLogger(OutputDatabaseWriter.class);
  private static final int LOG_INFO_SIZE = 1000;

  private final File file;
  private final Database output;
  private int gamesAdded = 0;

  public OutputDatabaseWriter(File file, boolean overwrite) throws IOException {
    this.file = file;
    if (overwrite && file.exists()) {
      delete(file);
    }
    this.output = Databases.create(file);
  }

  private static void delete(File file) throws IOException {
    String name = file.getName().toLowerCase();
    if (name.endsWith(".cbh")) {
      DatabaseCbh.delete(file);
    } else if (name.endsWith(".2cbh")) {
      Database2Cbh.delete(file);
    } else {
      // TODO: A pgn database may have additional index files that should be deleted as well
      file.delete();
    }
  }

  @Override
  public boolean needsMoves() {
    return true;
  }

  @Override
  public void accept(GameDto game) {
    try {
      output.addGame(detach(game));
      gamesAdded++;
      if (gamesAdded % LOG_INFO_SIZE == 0) {
        System.out.println(gamesAdded + " games added");
      }
    } catch (RuntimeException e) {
      log.warn("Failed to add game {} to {}", game.id(), file, e);
    }
  }

  @Override
  public void finish() {
    try {
      output.close();
    } catch (IOException e) {
      log.warn("Failed to close output database", e);
    }
    System.out.printf(
        "%d games added to %s in %.2f s%n", gamesAdded, file, totalSearchTime / 1000.0);
  }

  /**
   * Detaches the game and its entities from their source ids, so the target database finds or
   * creates matching entities by name instead of trying to resolve ids that mean nothing there.
   */
  private static GameDto detach(GameDto dto) {
    return new GameDto(
        null,
        dto.type(),
        dto.textTitle(),
        detach(dto.whitePlayer()),
        dto.whiteElo(),
        detach(dto.blackPlayer()),
        dto.blackElo(),
        detach(dto.whiteTeam()),
        detach(dto.blackTeam()),
        dto.result(),
        dto.date(),
        dto.eco(),
        dto.round(),
        dto.subRound(),
        dto.lineEvaluation(),
        detach(dto.tournament()),
        detach(dto.source()),
        detach(dto.annotator()),
        detach(dto.gameTag()),
        dto.medals(),
        dto.deleted(),
        dto.topGame(),
        dto.setupPosition(),
        dto.variant(),
        dto.noMoves(),
        dto.notation(),
        dto.variationMoves(),
        dto.ait(),
        dto.vcs(),
        dto.finalMaterial(),
        dto.gameVersion(),
        dto.creationTimestamp(),
        dto.lastChanged(),
        dto.moves(),
        dto.text(),
        dto.extraTags());
  }

  private static PlayerDto detach(PlayerDto p) {
    return p == null
        ? null
        : new PlayerDto(null, p.lastName(), p.firstName(), p.gameCount(), p.fideId(), p.chessBaseId());
  }

  private static TeamDto detach(TeamDto t) {
    return t == null
        ? null
        : new TeamDto(null, t.title(), t.teamNumber(), t.season(), t.year(), t.nation(), t.gameCount());
  }

  private static TournamentDto detach(TournamentDto t) {
    return t == null
        ? null
        : new TournamentDto(
            null,
            t.title(),
            t.startDate(),
            t.endDate(),
            t.place(),
            t.nation(),
            t.category(),
            t.categoryRoman(),
            t.rounds(),
            t.type(),
            t.timeControl(),
            t.typeCombined(),
            t.complete(),
            t.teamTournament(),
            t.tiebreakRules(),
            t.latitude(),
            t.longitude(),
            t.gameCount());
  }

  private static SourceDto detach(SourceDto s) {
    return s == null
        ? null
        : new SourceDto(
            null, s.title(), s.publisher(), s.publication(), s.date(), s.version(), s.quality(), s.gameCount());
  }

  private static AnnotatorDto detach(AnnotatorDto a) {
    return a == null ? null : new AnnotatorDto(null, a.name(), a.gameCount());
  }

  private static GameTagDto detach(GameTagDto g) {
    return g == null
        ? null
        : new GameTagDto(
            null,
            g.title(),
            g.languages(),
            g.languageCount(),
            g.englishTitle(),
            g.germanTitle(),
            g.frenchTitle(),
            g.spanishTitle(),
            g.italianTitle(),
            g.dutchTitle(),
            g.slovenianTitle(),
            g.resTitle(),
            g.gameCount());
  }
}
