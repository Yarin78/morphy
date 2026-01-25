package se.yarin.morphy.cli.games;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.annotations.AnnotationConverter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

public class PgnDatabaseBuilder extends GameConsumerBase {
  private static final Logger log = LogManager.getLogger();

  private final FileWriter pgnFileWriter;
  private final PgnExporter exporter;
  private final AnnotationConverter converter;
  private boolean firstGame = true;

  public PgnDatabaseBuilder(File file) throws IOException {
    this(file, true, false, null);
  }

  public PgnDatabaseBuilder(
      File file,
      boolean includeOptionalHeaders,
      boolean standardAnnotationsOnly,
      Set<Nation> commentLanguageFilter)
      throws IOException {
    this.pgnFileWriter = new FileWriter(file);

    // Use simplified converter for human-readable PGN
    this.converter = AnnotationConverter.getSimplifiedPgnConverter();

    PgnFormatOptions options =
        new PgnFormatOptions(
            79, // maxLineLength
            includeOptionalHeaders, // includeOptionalHeaders
            true, // includePlyCount
            true, // exportVariations
            true, // exportComments
            true, // exportNAGs
            false, // useSymbolsForNAGs
            "\n" // lineEnding
            );

    AnnotationTransformer transformer =
        (annotations, lastMoveBy) -> {
          // Use instance method instead of static method
          converter.convertToPgn(annotations, lastMoveBy);
          if (standardAnnotationsOnly
              || (commentLanguageFilter != null && !commentLanguageFilter.isEmpty())) {
            new PgnAnnotationFilter(standardAnnotationsOnly, commentLanguageFilter)
                .transform(annotations, lastMoveBy);
          }
        };

    this.exporter = new PgnExporter(options, transformer);
  }

  @Override
  public void finish() {
    try {
      this.pgnFileWriter.close();
    } catch (IOException e) {
      log.warn("Failed to close output database", e);
    }
  }

  @Override
  public void accept(Game game) {
    try {
      if (!firstGame) {
        this.pgnFileWriter.write("\n");
      }
      firstGame = false;
      exporter.exportGame(game.getModel(), this.pgnFileWriter);
    } catch (IOException e) {
      log.warn("Failed to write to PGN database", e);
    }
  }
}
