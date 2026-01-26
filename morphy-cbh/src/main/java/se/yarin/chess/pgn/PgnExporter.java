package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.*;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Stream;

/**
 * Exports GameModel objects to PGN format.
 */
public class PgnExporter {

    private final PgnFormatOptions options;
    private final AnnotationTransformer annotationTransformer;

    /**
     * Creates an exporter with default options.
     */
    public PgnExporter() {
        this(PgnFormatOptions.DEFAULT, null);
    }

    /**
     * Creates an exporter with custom options.
     *
     * @param options the format options
     */
    public PgnExporter(@NotNull PgnFormatOptions options) {
        this(options, null);
    }

    /**
     * Creates an exporter with custom options and annotation transformer.
     *
     * @param options the format options
     * @param annotationTransformer the transformer to apply to annotations before export (may be null)
     */
    public PgnExporter(@NotNull PgnFormatOptions options, @Nullable AnnotationTransformer annotationTransformer) {
        this.options = options;
        this.annotationTransformer = annotationTransformer;
    }

    /**
     * Exports a game to a PGN string.
     *
     * @param game the game to export
     * @return the PGN string
     */
    @NotNull
    public String exportGame(@NotNull GameModel game) {
        StringWriter writer = new StringWriter();
        try {
            exportGame(game, writer);
        } catch (IOException e) {
            // StringWriter doesn't throw IOException
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    /**
     * Exports a game to a Writer.
     *
     * @param game the game to export
     * @param writer the writer to export to
     * @throws IOException if an I/O error occurs
     */
    public void exportGame(@NotNull GameModel game, @NotNull Writer writer) throws IOException {
        exportHeaders(game.header(), game.moves(), writer);
        writer.write(options.lineEnding());
        exportMoves(game.moves(), game.header().getResult(), writer);
        writer.write(options.lineEnding());
    }

    /**
     * Exports multiple games to a Writer.
     * Games are separated by blank lines according to PGN standard.
     *
     * @param games the stream of games to export
     * @param writer the writer to export to
     * @throws IOException if an I/O error occurs
     */
    public void exportGames(@NotNull Stream<GameModel> games, @NotNull Writer writer) throws IOException {
        Iterator<GameModel> iterator = games.iterator();
        boolean firstGame = true;
        while (iterator.hasNext()) {
            if (!firstGame) {
                writer.write(options.lineEnding());
            }
            exportGame(iterator.next(), writer);
            firstGame = false;
        }
    }

    /**
     * Exports only the moves as a single-line string without headers or result suffix.
     * Useful for debugging and toString() implementations.
     *
     * @param moves the moves to export
     * @return a single-line string of moves with variations and annotations
     */
    @NotNull
    public String exportMovesOnly(@NotNull GameMovesModel moves) {
        return exportMovesOnly(moves.root());
    }

    /**
     * Exports only the moves as a single-line string without headers or result suffix.
     * Useful for debugging and toString() implementations.
     *
     * @param node the game node to start export from
     * @return a single-line string of moves with variations and annotations
     */
    @NotNull
    public String exportMovesOnly(@NotNull GameMovesModel.Node node) {
        try {
            StringWriter writer = new StringWriter();
            // Single-line options: unlimited line length, no line endings
            PgnFormatOptions singleLineOpts = new PgnFormatOptions(
                Integer.MAX_VALUE,              // no line wrapping
                false,                           // includeOptionalHeaders (unused here)
                false,                           // includePlyCount (unused here)
                options.exportVariations(),      // preserve setting
                options.exportComments(),        // preserve setting
                options.exportNAGs(),            // preserve setting
                true,                            // useSymbolsForNAGs - use symbols for better readability
                ""                               // no line ending
            );
            PgnExporter exporter = new PgnExporter(singleLineOpts, this.annotationTransformer);
            MoveTextWriter moveWriter = exporter.new MoveTextWriter(writer);
            exporter.exportNode(node, true, moveWriter);
            return writer.toString().trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void exportHeaders(GameHeaderModel header, GameMovesModel moves, Writer writer) throws IOException {
        // Seven Tag Roster (in order)
        writeTag(writer, "Event", header.getEvent(), "?");
        writeTag(writer, "Site", header.getEventSite(), "?");
        writeTag(writer, "Date", header.getDate() != null ? header.getDate().toString() : "????.??.??", null);
        writeTag(writer, "Round", header.getRound() != null ? formatRound(header.getRound(), header.getSubRound()) : "?", null);
        writeTag(writer, "White", header.getWhite(), "?");
        writeTag(writer, "Black", header.getBlack(), "?");
        writeTag(writer, "Result", header.getResult() != null ? header.getResult().toString() : "*", null);

        if (!options.includeOptionalHeaders()) {
            return;
        }

        // Optional headers
        if (header.getWhiteElo() != null) {
            writeTag(writer, "WhiteElo", header.getWhiteElo().toString(), null);
        }
        if (header.getBlackElo() != null) {
            writeTag(writer, "BlackElo", header.getBlackElo().toString(), null);
        }

        if (header.getWhiteTeam() != null) {
            writeTag(writer, "WhiteTeam", header.getWhiteTeam(), null);
        }
        if (header.getBlackTeam() != null) {
            writeTag(writer, "BlackTeam", header.getBlackTeam(), null);
        }

        if (header.getEco() != null && header.getEco().isSet()) {
            writeTag(writer, "ECO", header.getEco().toString(), null);
        }
        if (header.getEventTimeControl() != null) {
            writeTag(writer, "TimeControl", header.getEventTimeControl(), null);
        }

        if (header.getAnnotator() != null) {
            writeTag(writer, "Annotator", header.getAnnotator(), null);
        }
        if (header.getEventDate() != null && !header.getEventDate().isUnset()) {
            writeTag(writer, "EventDate", header.getEventDate().toString(), null);
        }
        if (header.getEventType() != null) {
            writeTag(writer, "EventType", header.getEventType(), null);
        }
        if (header.getEventCategory() != null) {
            writeTag(writer, "EventCategory", header.getEventCategory().toString(), null);
        }
        if (header.getEventCountry() != null) {
            writeTag(writer, "EventCountry", header.getEventCountry(), null);
        }
        if (header.getEventRounds() != null) {
            writeTag(writer, "EventRounds", header.getEventRounds().toString(), null);
        }
        if (header.getEventTimeControl() != null) {
            writeTag(writer, "EventTimeControl", header.getEventTimeControl(), null);
        }

        if (header.getSource() != null) {
            writeTag(writer, "Source", header.getSource(), null);
        }
        if (header.getSourceDate() != null && !header.getSourceDate().isUnset()) {
            writeTag(writer, "SourceDate", header.getSourceDate().toString(), null);
        }
        if (header.getSourceTitle() != null) {
            writeTag(writer, "SourceTitle", header.getSourceTitle(), null);
        }

        if (options.includePlyCount()) {
            writeTag(writer, "PlyCount", String.valueOf(moves.countPly(false)), null);
        }

        // SetUp and FEN for setup positions
        if (moves.isSetupPosition()) {
            writeTag(writer, "SetUp", "1", null);
            // Generate FEN
            Position startPos = moves.root().position();
            int startPly = moves.root().ply();
            String fen = PositionState.toFen(startPos, startPly);
            writeTag(writer, "FEN", fen, null);
        }

        // Custom fields (excluding SetUp and FEN)
        Map<String, Object> allFields = header.getAllFields();
        List<String> customFields = new ArrayList<>();
        for (String field : allFields.keySet()) {
            if (!isStandardField(field) && !field.equals("SetUp") && !field.equals("FEN")) {
                customFields.add(field);
            }
        }
        Collections.sort(customFields);
        for (String field : customFields) {
            Object value = header.getField(field);
            if (value != null) {
                writeTag(writer, field, value.toString(), null);
            }
        }
    }

    private void writeTag(Writer writer, String name, String value, String defaultValue) throws IOException {
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            return;
        }
        writer.write("[");
        writer.write(name);
        writer.write(" \"");
        writer.write(escapeString(value));
        writer.write("\"]");
        writer.write(options.lineEnding());
    }

    private String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatRound(int round, Integer subRound) {
        if (subRound != null && subRound > 0) {
            return round + "." + subRound;
        }
        return String.valueOf(round);
    }

    private boolean isStandardField(String field) {
        return switch (field) {
            case "white", "black", "whiteElo", "blackElo", "whiteTeam", "blackTeam",
                 "result", "lineEvaluation", "date", "eco", "round", "subRound",
                 "event", "eventDate", "eventEndDate", "eventSite", "eventCountry",
                 "eventCategory", "eventRounds", "eventType", "eventTimeControl",
                 "sourceTitle", "source", "sourceDate", "annotator", "gameTag" -> true;
            default -> false;
        };
    }

    private void exportMoves(GameMovesModel moves, @Nullable GameResult result, Writer writer) throws IOException {
        MoveTextWriter moveWriter = new MoveTextWriter(writer);
        exportNode(moves.root(), true, moveWriter);

        moveWriter.ensureSpace();
        moveWriter.write((result == null ? GameResult.NOT_FINISHED : result).toString());
    }

    void exportNode(GameMovesModel.Node node, boolean firstMoveInLine, MoveTextWriter writer) throws IOException {
        if (!node.hasMoves()) {
            return;
        }

        // Get main line move (first child)
        GameMovesModel.Node mainChild = node.children().getFirst();

        // Export the main line move
        exportSingleMove(mainChild, node, writer, firstMoveInLine);

        // Export variations (children 1+)
        if (options.exportVariations()) {
            for (int i = 1; i < node.numMoves(); i++) {
                GameMovesModel.Node varChild = node.children().get(i);
                writer.ensureSpace();
                writer.write("(");
                exportSingleMove(varChild, node, writer, true);
                // Recursively export the variation's continuation
                if (varChild.hasMoves()) {
                    writer.ensureSpace();
                    exportNode(varChild, false, writer);
                }
                writer.write(")");
            }
        }

        // Continue with main line recursively
        if (mainChild.hasMoves()) {
            writer.ensureSpace();
            exportNode(mainChild, options.exportVariations() && node.numMoves() > 1, writer);
        }
    }

    private void exportSingleMove(GameMovesModel.Node child, GameMovesModel.Node parent,
                                   MoveTextWriter writer, boolean firstMoveInLine) throws IOException {
        // Create a copy of annotations to avoid modifying the original game model
        Annotations annotations = new Annotations(child.getAnnotations());

        // Apply annotation transformer if present
        if (annotationTransformer != null) {
            // Determine which player made the move (the parent's player to move)
            Player lastMoveBy = parent.position().playerToMove();
            annotationTransformer.transform(annotations, lastMoveBy);
        }

        // Export comments before the move
        if (options.exportComments()) {
            CommentaryBeforeMoveAnnotation beforeComment =
                    annotations.getByClass(CommentaryBeforeMoveAnnotation.class);
            if (beforeComment != null) {
                writer.write("{ " + beforeComment.getCommentary() + " }");
            }
        }

        // Write move number if needed
        Player movingPlayer = parent.position().playerToMove();
        boolean isWhiteMove = (movingPlayer == Player.WHITE);
        boolean needMoveNumber = isWhiteMove || firstMoveInLine || writer.wasNewline;

        writer.write(getMoveStr(needMoveNumber, child, annotations));

        // Export comments after the move
        if (options.exportComments()) {
            CommentaryAfterMoveAnnotation afterComment =
                    annotations.getByClass(CommentaryAfterMoveAnnotation.class);
            if (afterComment != null) {
                writer.ensureSpace();
                writer.write("{ " + afterComment.getCommentary() + " }");
            }
        }
    }

    private String getMoveStr(boolean needMoveNumber, GameMovesModel.Node node, Annotations transformedAnnotations) {
        StringBuilder moveStr = new StringBuilder();

        if (needMoveNumber) {
            moveStr.append(formatMoveNumber(node.ply())).append(' ');
        }

        List<NAGAnnotation> nags = transformedAnnotations.getAllByClass(NAGAnnotation.class);

        if (options.exportNAGs() && options.useSymbolsForNAGs()) {
            for (NAGAnnotation nag : nags) {
                if (nag.getNag().getType() == NAGType.MOVE_PREFIX) {
                    String symbol = nag.getNag().toASCIIString();
                    if (!symbol.isEmpty()) {
                        moveStr.append(symbol);
                        char ch = symbol.charAt(symbol.length() - 1);
                        if (Character.isLetter(ch) || ch == '.') {
                            moveStr.append(' ');
                        }
                    }
                }
            }
        }

        moveStr.append(node.lastMove().toSAN());

        // Export NAG annotations
        if (options.exportNAGs()) {
            for (NAGAnnotation nag : nags) {
                if (options.useSymbolsForNAGs()) {
                    if (nag.getNag().getType() == NAGType.MOVE_PREFIX) {
                        // Already handled
                        continue;
                    }
                    String symbol = nag.getNag().toASCIIString();
                    if (!symbol.isEmpty()) {
                        // Symbols related to the move itself appear immediately after the move,
                        // like !, ?, !?, ?! etc, and should thus have no space separator
                        if (nag.getNag().getType() != NAGType.MOVE_COMMENT || Character.isLetter(symbol.charAt(0))) {
                            moveStr.append(' ');
                        }
                        moveStr.append(symbol);
                    } else {
                        moveStr.append(" $").append(nag.getNag().ordinal());
                    }
                } else {
                    moveStr.append(" $").append(nag.getNag().ordinal());
                }
            }
        }

        return moveStr.toString();
    }

    private String formatMoveNumber(int ply) {
        int moveNum = (ply + 1) / 2;
        if (ply % 2 == 1) {
            // Odd ply = White's move
            return moveNum + ".";
        } else {
            // Even ply = Black's move
            return moveNum + "...";
        }
    }

    /**
     * Helper class for writing move text with line wrapping.
     */
    private class MoveTextWriter {
        private final Writer writer;
        private int currentLineLength = 0;
        private boolean needsSpace = false;
        private boolean pendingLeftParen = false;
        boolean wasNewline = false;

        public MoveTextWriter(Writer writer) {
            this.writer = writer;
        }

        public void write(String text) throws IOException {
            // Handle pending "(" from previous call
            if (pendingLeftParen) {
                // Calculate total length needed: space before "(" + "(" + text
                int totalLength = 1 + text.length(); // "(" + text
                if (needsSpace) {
                    totalLength++; // space before "("
                }

                // Check if it fits on current line
                if (currentLineLength + totalLength > options.maxLineLength() && currentLineLength > 0) {
                    // Doesn't fit, wrap to new line before "("
                    writer.write(options.lineEnding());
                    currentLineLength = 0;
                    needsSpace = false;
                    wasNewline = true;
                } else {
                    wasNewline = false;
                }

                // Write space before "(" if needed
                if (needsSpace) {
                    writer.write(" ");
                    currentLineLength++;
                }

                // Write the "("
                writer.write("(");
                currentLineLength++;
                pendingLeftParen = false;
                needsSpace = false; // No space between "(" and what follows
            }

            // Special case: no space before ")"
            if (text.equals(")")) {
                needsSpace = false;
            }

            // If this is a "(", delay its output
            if (text.equals("(")) {
                pendingLeftParen = true;
                return;
            }

            // Normal processing for non-"(" tokens
            int textLength = text.length();
            if (needsSpace) {
                textLength++; // Account for the space
            }

            if (currentLineLength + textLength > options.maxLineLength() && currentLineLength > 0) {
                writer.write(options.lineEnding());
                currentLineLength = 0;
                needsSpace = false;
                wasNewline = true;
            } else {
                wasNewline = false;
            }

            if (needsSpace) {
                writer.write(" ");
                currentLineLength++;
            }

            writer.write(text);
            currentLineLength += text.length();

            needsSpace = true; // Space before next token (since text is not "(")
        }

        public void ensureSpace() {
            needsSpace = true;
        }
    }
}
