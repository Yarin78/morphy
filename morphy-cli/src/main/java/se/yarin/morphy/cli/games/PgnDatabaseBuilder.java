package se.yarin.morphy.cli.games;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.chess.*;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.annotations.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PgnDatabaseBuilder extends GameConsumerBase {
    private static final Logger log = LogManager.getLogger();

    private static final int PGN_MAX_LINE_LENGTH = 79;

    private static class PgnHeaderField {
        private final String pgnField;
        private final Function<GameModel, Object> fieldFetcher;
        private final boolean mandatory;

        private PgnHeaderField(String pgnField, Function<GameModel, Object> fieldFetcher, boolean mandatory) {
            this.pgnField = pgnField;
            this.fieldFetcher = fieldFetcher;
            this.mandatory = mandatory;
        }
    }

    private static final PgnHeaderField[] PGN_HEADER_FIELDS = {
            // "Seven Tag Rooster" (mandatory header fields)
            new PgnHeaderField("Event", model -> model.header().getEvent(), true),
            new PgnHeaderField("Site", model -> model.header().getEventSite(), true),
            new PgnHeaderField("Date", model -> model.header().getDate(), true),
            new PgnHeaderField("Round", model -> model.header().getRound(), true),
            new PgnHeaderField("White", model -> model.header().getWhite(), true),
            new PgnHeaderField("Black", model -> model.header().getBlack(), true),
            new PgnHeaderField("Result", model -> model.header().getResult(), true),

            // Optional header fields
            new PgnHeaderField("ECO", model -> model.header().getEco(), false),
            new PgnHeaderField("Annotator", model -> model.header().getAnnotator(), false),
            new PgnHeaderField("PlyCount", model -> model.moves().countPly(false), false),
            new PgnHeaderField("TimeControl", model -> model.header().getEventTimeControl(), false),
            new PgnHeaderField("SetUp", model -> model.moves().isSetupPosition() ? "1": null, false),
            // TODO: Add FEN support
            new PgnHeaderField("FEN", model -> model.moves().isSetupPosition() ? "not supported": null, false),
            new PgnHeaderField("SourceVersionDate", model -> model.header().getSourceDate(), false)
    };

    private final FileWriter pgnFileWriter;
    private StringBuilder currentLine = new StringBuilder();


    public PgnDatabaseBuilder(File file) throws IOException {
        this.pgnFileWriter = new FileWriter(file);
    }

    @Override
    public void finish() {
        try {
            flushCurrentLine();
            this.pgnFileWriter.close();
        } catch (IOException e) {
            log.warn("Failed to close output database");
        }
    }

    protected void emit(String s, boolean needsDelimeter) {
        for (String word : s.split(" ")) {
            char lastChar = this.currentLine.length() > 0 ? this.currentLine.charAt(this.currentLine.length() - 1) : ' ';
            boolean isDelimited = lastChar == ' ' || lastChar == '{' || lastChar == '(';

            if (needsDelimeter && !isDelimited) {
                if (this.currentLine.length() < PGN_MAX_LINE_LENGTH) {
                    this.currentLine.append(" ");
                } else {
                    flushCurrentLine();
                }
            }
            if (this.currentLine.length() + word.length() > PGN_MAX_LINE_LENGTH) {
                flushCurrentLine();
            }
            this.currentLine.append(word);
            needsDelimeter = true;
        }
    }

    protected void flushCurrentLine() {
        try {
            this.pgnFileWriter.write(currentLine + "\n");
            this.currentLine = new StringBuilder();
        } catch (IOException e) {
            log.warn("Failed to write to output database");
        }
    }

    @Override
    public void accept(Game game) {
        GameModel model = game.getModel();
        try {
            for (PgnHeaderField pgnHeaderField : PGN_HEADER_FIELDS) {
                String key = pgnHeaderField.pgnField;
                Object value = pgnHeaderField.fieldFetcher.apply(model);
                if (value == null || value.toString().isEmpty()) {
                    if (!pgnHeaderField.mandatory) {
                        continue;
                    }
                    value = "?";
                }
                this.pgnFileWriter.write(String.format("[%s \"%s\"]\n", key, value));
            }
            this.pgnFileWriter.write("\n");

            this.outputMoves(model.moves().root(), true);
            this.emit(model.header().getResult().toString(), true);
            this.flushCurrentLine();
            this.pgnFileWriter.write("\n");
        } catch (IOException e) {
            log.warn("Failed to write to PGN database");
        }
    }

    private void outputMoves(GameMovesModel.Node node, boolean showMoveNumber) {
        while (node.mainMove() != null) {
            outputLastMove(node.mainNode(), showMoveNumber);
            if (node.hasVariations()) {
                for (GameMovesModel.Node child : node.children().subList(1, node.moves().size())) {
                    this.emit("(", true);
                    outputLastMove(child, true);
                    outputMoves(child, false);
                    this.emit(")", false);
                }
                showMoveNumber = true;
            } else {
                showMoveNumber = false;
            }
            node = node.mainNode();
        }
    }

    private void outputLastMove(GameMovesModel.Node node, boolean showMoveNumber) {
        TextBeforeMoveAnnotation preMoveTextAnnotation = node.getAnnotation(TextBeforeMoveAnnotation.class);
        if (preMoveTextAnnotation != null) {
            this.emit("{", true);
            this.emit(preMoveTextAnnotation.text().strip(), false);
            this.emit("}", false);
        }

        emit(getMoveString(node, showMoveNumber), true);

        SymbolAnnotation symbolAnnotation = node.getAnnotation(SymbolAnnotation.class);
        if (symbolAnnotation != null) {
            int moveComment = symbolAnnotation.moveComment().ordinal();
            int movePrefix = symbolAnnotation.movePrefix().ordinal();
            int lineEvaluation = symbolAnnotation.lineEvaluation().ordinal();
            if (moveComment != 0) {
                emit(String.format("$%d", moveComment), true);
            }
            if (movePrefix != 0) {
                emit(String.format("$%d", movePrefix), true);
            }
            if (lineEvaluation != 0) {
                emit(String.format("$%d", lineEvaluation), true);
            }
        }

        StringBuilder textAfter = new StringBuilder();

        GraphicalSquaresAnnotation squaresAnnotation = node.getAnnotation(GraphicalSquaresAnnotation.class);
        if (squaresAnnotation != null) {
            textAfter
                    .append("[%csl ")
                    .append(squaresAnnotation.squares()
                        .stream()
                        .map(square -> annotationColorToChar(square.color()) + Chess.sqiToStr(square.sqi()))
                        .collect(Collectors.joining(",")))
                    .append("]");
        }
        GraphicalArrowsAnnotation arrowsAnnotation = node.getAnnotation(GraphicalArrowsAnnotation.class);
        if (arrowsAnnotation != null) {
            textAfter
                    .append("[%cal ")
                    .append(arrowsAnnotation.arrows()
                        .stream()
                        .map(arrow -> annotationColorToChar(arrow.color()) + Chess.sqiToStr(arrow.fromSqi()) + Chess.sqiToStr(arrow.toSqi()))
                        .collect(Collectors.joining(",")))
                    .append("]");
        }

        TextAfterMoveAnnotation textAnnotation = node.getAnnotation(TextAfterMoveAnnotation.class);
        if (textAnnotation != null) {
            if (textAfter.length() > 0) {
                textAfter.append(" ");
            }
            textAfter.append(textAnnotation.text().strip());
        }

        if (textAfter.length() > 0) {
            emit("{", true);
            emit(textAfter.toString(), false);
            emit("}", false);
        }
    }

    private String getMoveString(GameMovesModel.Node node, boolean showMoveNumber) {
        StringBuilder moveString = new StringBuilder();

        GameMovesModel.Node parentNode = node.parent();
        if (showMoveNumber || parentNode.position().playerToMove() == Player.WHITE) {
            int ply = parentNode.ply();
            moveString.append(Chess.plyToMoveNumber(ply));
            moveString.append('.');
            if (!Chess.isWhitePly(ply)) moveString.append("..");
            moveString.append(' ');
        }
        moveString.append(node.lastMove().toSAN());
        return moveString.toString();
    }

    private String annotationColorToChar(GraphicalAnnotationColor color) {
        return switch (color) {
            case GREEN -> "G";
            case RED -> "R";
            case YELLOW -> "Y";
            default -> "?";
        };
    }
}
