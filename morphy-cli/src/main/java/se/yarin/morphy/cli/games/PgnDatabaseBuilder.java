package se.yarin.morphy.cli.games;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.NagStyle;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.AnnotationConverter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

/**
 * Writes {@link GameModel}s to a PGN file. Used only by {@code summarize-opening}, which
 * synthesizes new annotated move trees that the {@code Database} facade's flattened PGN-text
 * {@code GameMovesDto} can't represent, so it stays on this raw writer rather than the facade.
 */
public class PgnDatabaseBuilder {
  private static final Logger log = LoggerFactory.getLogger(PgnDatabaseBuilder.class);

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
            NagStyle.NUMERIC, // nagStyle
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

  public void finish() {
    try {
      this.pgnFileWriter.close();
    } catch (IOException e) {
      log.warn("Failed to close output database", e);
    }
  }

  /** Writes a single game model to the PGN file, separated from any previous game. */
  public void writeModel(GameModel model) throws IOException {
    if (!firstGame) {
      this.pgnFileWriter.write("\n");
    }
    firstGame = false;
    exporter.exportGame(model, this.pgnFileWriter);
  }
}
