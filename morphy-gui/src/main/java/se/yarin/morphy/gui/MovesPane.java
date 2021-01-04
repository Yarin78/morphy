package se.yarin.morphy.gui;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.CriticalPositionAnnotation;
import se.yarin.cbhlib.annotations.GraphicalArrowsAnnotation;
import se.yarin.cbhlib.annotations.GraphicalSquaresAnnotation;
import se.yarin.cbhlib.annotations.SymbolAnnotation;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;

import java.util.*;

public class MovesPane extends Control {
    private static final Logger log = LoggerFactory.getLogger(MovesPane.class);

    private final double MOVE_BOX_RIGHT_MARGIN = 25; // Compensate for the padding and some extra space to be safe

    private NavigableGameModel model = new NavigableGameModel();

    private ScrollPane movePane;
    private VBox moveBox;

    private double currentRowWidth;
    private HBox currentRow;
    private boolean pullDownLastIfEOL;
    private HashMap<String, Double> labelWidthCache = new HashMap<>(); // TODO: Make real cache

    private Map<GameMovesModel.Node, MoveLabel> positionLabelMap = new HashMap<>();

    public MovesPane() {
        movePane = new ScrollPane();
        movePane.setId("movePane");
        movePane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        movePane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        moveBox = new VBox();
        moveBox.setId("moveBox");
        movePane.setContent(moveBox);

        moveBox.prefWidthProperty().bind(movePane.widthProperty().subtract(20)); // Compensate for vertical scrollbar
        moveBox.widthProperty().addListener(observable -> drawMoves());
    }

    public void setModel(NavigableGameModel model) {
        this.model = model;
    }

    private void handleMoveSelected(MouseEvent mouseEvent) {
        MoveLabel source = (MoveLabel) mouseEvent.getSource();
        log.debug("Clicked on " + source.getMove() + ", node " + source.getNode());
        selectPosition(source.getNode());
    }

    public void selectPosition(GameMovesModel.Node position) {
        if (model.cursor() != null) {
            // Deselect previous selection
            MoveLabel moveLabel = positionLabelMap.get(model.cursor());
            if (moveLabel != null) {
                moveLabel.getStyleClass().remove("selected-move");
            }
        }

        model.setCursor(position);

        // Highlight the selected position
        MoveLabel moveLabel = positionLabelMap.get(model.cursor());
        if (moveLabel != null) {
            moveLabel.getStyleClass().add("selected-move");
        }

        // TODO: drawBoard();
    }


    private void addNewRow(int level) {
        currentRow = new HBox();
        currentRowWidth = 16 * level;
        currentRow.setPadding(new Insets(2, 0, 2, currentRowWidth));
        moveBox.getChildren().add(currentRow);
//        log.debug("new row");
    }

    // Determines the width of a label based ONLY on the text and it's styleclasses (not padding)
    private double getLabelWidth(Label label, String... extraStyleClasses) {
        Text text = new Text(label.getText());
        text.getStyleClass().addAll(label.getStyleClass());
        text.getStyleClass().addAll(extraStyleClasses);

        String cacheId = text.getStyleClass() + "#" + label.getText();
        if (labelWidthCache.containsKey(cacheId)) {
            return labelWidthCache.get(cacheId);
        }

        HBox hbox = new HBox(text);
        hbox.setId("moveBox");
        Scene scene = new Scene(hbox);
        scene.getStylesheets().add("/styles/styles.css");
//        log.info("without css: " + text.getLayoutBounds().getWidth());
        text.applyCss();
//        log.info("with css: " + text.getLayoutBounds().getWidth());
        double width = text.getLayoutBounds().getWidth();
        labelWidthCache.put(cacheId, width);
        return width;
    }

    private void addImage(Image image) {
        // TODO: This is ugly (and not correct), should be same method as addControl if possible to avoid code duplication.

        double width = image.getWidth();

        currentRow.getChildren().add(new ImageView(image));
        currentRowWidth += width;
    }

    private void addControl(Control node, int level, double leftPadding, double rightPadding, String... styleClass) {
        node.getStyleClass().addAll(styleClass);

        if (!(node instanceof Label)) throw new RuntimeException("Not supported yet");
        double width = getLabelWidth((Label) node);
        boolean singleCharacter = ((Label) node).getText().length() == 1;
//        log.debug(((Label) control).getText() + " " + leftPadding + " " + currentRow.getChildren().size());

//        log.debug("currentRowWidth = " + currentRowWidth + ", controlWidth = " + width + ", moveBox width = " + moveBox.getWidth());
        // Force single characters to be on same line; we give enough margin to make this possible
        if (!singleCharacter && currentRowWidth + width + leftPadding + rightPadding >
                moveBox.getWidth() - MOVE_BOX_RIGHT_MARGIN) {
            Node last = null;
            if (pullDownLastIfEOL) {
                int lastIndex = currentRow.getChildren().size() - 1;
                last = currentRow.getChildren().get(lastIndex);
                currentRow.getChildren().remove(lastIndex);
            }
            addNewRow(level);
            if (last != null) {
                currentRow.getChildren().add(last);
                if (last instanceof Label) {
                    currentRowWidth += getLabelWidth((Label) last);
                }
            }
        }

        pullDownLastIfEOL = false;

        if (currentRow.getChildren().size() == 0) {
            leftPadding = 0;
        }

        node.setPadding(new Insets(0, rightPadding, 0, leftPadding));
        currentRow.getChildren().add(node);
        currentRowWidth += width + leftPadding + rightPadding;
    }

    private boolean fitsOnRow(String text, double leftPadding, double rightPadding, String... styleClass) {
        Label label = new Label(text);
        double width = getLabelWidth(label, styleClass);
        return currentRowWidth + width + leftPadding + rightPadding <= moveBox.getWidth() - MOVE_BOX_RIGHT_MARGIN;
    }

    private void addText(String text, int level, double leftPadding, double rightPadding, String... styleClass) {
        if (text.trim().length() == 0) return;

        // Check if the entire text fits on the row
        if (fitsOnRow(text, leftPadding, rightPadding, styleClass)) {
            addControl(new Label(text), level, leftPadding, rightPadding, styleClass);
        } else {
//            log.debug("Doesn't fit: " + text);
            // Check if we can split it
            int cur = text.indexOf(' ');
            if (cur < 0) {
                // If there are no spaces, we can't do so much
                addControl(new Label(text), level, leftPadding, rightPadding, styleClass);
            } else {
                int best = cur; // Always take the first word, even if it's too long (can't do better)
                while (cur != -1 && fitsOnRow(text.substring(0, cur), leftPadding, 0, styleClass)) {
                    best = cur;
                    cur = text.indexOf(' ', cur + 1);
                }
//                log.debug("DID FIT: " + text.substring(0, best));
                addControl(new Label(text.substring(0, best)), level, leftPadding, 0, styleClass);
                addNewRow(level);
                // Recursively split the next part, if needed
                addText(text.substring(best + 1), level, 0, rightPadding, styleClass);
            }
        }
    }

    private void addText(String text, int level, String... styleClass) {
        addText(text, level, 0.0, 0.0, styleClass);
    }

    private void addPreMoveAnnotations(GameMovesModel.Node node, int level) {
        // TODO: This won't work properly in case of multiple languages
        CommentaryBeforeMoveAnnotation beforeMoveAnnotation = model.cursor().getAnnotation(CommentaryBeforeMoveAnnotation.class);
        if (beforeMoveAnnotation != null) {
            addText(beforeMoveAnnotation.getCommentary(), level, "comment-label");
        }
    }

    private void addPostMoveAnnotations(GameMovesModel.Node node, int level) {
        Annotations annotations = node.getAnnotations();
        boolean hasGraphicalAnnotations =
                annotations.getByClass(GraphicalArrowsAnnotation.class) != null ||
                        annotations.getByClass(GraphicalSquaresAnnotation.class) != null;
        if (hasGraphicalAnnotations) {
            addImage(new Image("images/graphical-annotation.png", 16, 16, true, true));
        }

        // TODO: This won't work properly in case of multiple languages
        CommentaryAfterMoveAnnotation afterMoveAnnotation = model.cursor().getAnnotation(CommentaryAfterMoveAnnotation.class);

        if (afterMoveAnnotation != null) {
            addText(afterMoveAnnotation.getCommentary(), level, "comment-label");
        }
    }

    /**
     * Output the last move made with annotations
     * @param node the position after the last move (can be the start position)
     */
    private void addMove(GameMovesModel.Node node,
                         boolean showMoveNumber,
                         int level,
                         boolean inlineVariation,
                         boolean headOfVariation) {
        Move move = node.lastMove();
        addPreMoveAnnotations(node, level);

        // Move is null if node is the start position of the game
        if (move != null) {
            // This assumes there can only be on symbol annotation per move
            SymbolAnnotation symbols = model.cursor().getAnnotation(SymbolAnnotation.class);
            NAG movePrefix = symbols == null ? NAG.NONE : symbols.getMovePrefix();
            NAG moveComment = symbols == null ? NAG.NONE : symbols.getMoveComment();
            NAG lineEvaluation = symbols == null ? NAG.NONE : symbols.getLineEvaluation();

            CriticalPositionAnnotation criticalPosition = model.cursor().getAnnotation(CriticalPositionAnnotation.class);

            // Add move, symbols and move number
            MoveLabel lbl = new MoveLabel(move, node);
            String moveText = movePrefix.toUnicodeString();
            Player moveColor = node.parent().position().playerToMove();
            if (showMoveNumber || moveColor == Player.WHITE) {
                moveText += String.format("%d.", Chess.plyToMoveNumber(node.parent().ply()));
                if (moveColor == Player.BLACK) moveText += "..";
            }

            moveText += move.toSAN();
            moveText += moveComment.toUnicodeString();
            moveText += lineEvaluation.toUnicodeString();

            lbl.setText(moveText);
            lbl.setOnMouseClicked(this::handleMoveSelected);
            List<String> styles = new ArrayList<>();
            double leftPadding = 4, rightPadding = 4;
            if (headOfVariation && model.cursor().getAnnotation(CommentaryBeforeMoveAnnotation.class) == null) {
                leftPadding = 0;
            }
            if (!node.hasMoves() && model.cursor().getAnnotation(CommentaryAfterMoveAnnotation.class) == null) {
                rightPadding = 0;
            }
            if (level == 0 && !model.moves().root().isSingleLine()) {
                styles.add("main-line");
            } else if (inlineVariation) {
                styles.add("last-line");
            }
            if (headOfVariation && !inlineVariation && level > 1) {
                styles.add("variation-head");
                leftPadding = 4;
            }
            if (criticalPosition != null) {
                // TODO: These styles don't look as nice when the move is the selected move
                switch (criticalPosition.getType()) {
                    case OPENING:
                        styles.add("critical-opening-position");
                        break;
                    case MIDDLEGAME:
                        styles.add("critical-middlegame-position");
                        break;
                    case ENDGAME:
                        styles.add("critical-endgame-position");
                        break;
                }
            }

            lbl.getStyleClass().addAll(styles);
            positionLabelMap.put(node, lbl);
            addControl(lbl, level, leftPadding, rightPadding);
        }

        addPostMoveAnnotations(node, level);
    }

    private boolean allVariationsAreSingleLine(GameMovesModel.Node node) {
        return node.children()
                .stream()
                .skip(1) // Skip the main variation
                .allMatch(child -> node.isSingleLine());
    }

    private void generateMoveControls(GameMovesModel.Node node, boolean showMoveNumber,
                                      int level, boolean inlineVariation, String linePrefix) {
        // TODO: Try and make this cleaner by using TextFlow, so we don't have to calculate the width of everything manually
        if (node.lastMove() != null) {
            addMove(node, showMoveNumber, level, inlineVariation, true);
            showMoveNumber = false;
        }

        while (node.hasMoves()) {
            List<Move> moves = node.moves();
            if (moves.size() == 1) {
                addMove(node.mainNode(), showMoveNumber, level, inlineVariation, false);
                showMoveNumber = false;
            } else {
                if (inlineVariation) throw new RuntimeException("Found variations in an inline variation");
                if (level == 0) {
                    // Show main move on existing line, but then one new paragraph per sub-line,
                    // each paragraph starting with [ and ending with ]
                    addMove(node.mainNode(), showMoveNumber, level, false, false);

                    for (int i = 1; i < moves.size(); i++) {
                        addNewRow(level + 1);
                        addText("[", level + 1);
                        generateMoveControls(node.children().get(i), true, level + 1, false, linePrefix);
                        addText("]", level + 1);
                    }
                    addNewRow(level);
                    showMoveNumber = true;
                } else if (allVariationsAreSingleLine(node)) {
                    // Show the alternatives inline, within () and separated by ;
                    addMove(node.mainNode(), showMoveNumber, level, false, false);

                    addText("(", level, 6.0, 0.0, "last-line");
                    pullDownLastIfEOL = true;
                    for (int i = 1; i < moves.size(); i++) {
                        if (i > 1) addText(";", level, 0.0, 3.0, "last-line");
                        generateMoveControls(node.children().get(i), true, level, true, linePrefix);
                    }
                    addText(")", level, "last-line");
                    showMoveNumber = true;
                } else {
                    // Subvariations are marked with letters and digits alternatively for each level, e.g. "B1c"
                    // The order is: [A-Z], [1-9], [a-z], [1-9], [1-9] and repeat digits
                    // But replace [1-9] with [a-z] if there are 10 or more lines at that level
                    // It won't look pretty if there are more than 26 lines, but CB has the same issue
                    char startChar = '1';
                    if (level == 1) startChar = 'A';
                    if (level == 3) startChar = 'a';
                    if (startChar == '1' && moves.size() >= 10) startChar = 'a';

                    for (int i = 0; i < moves.size(); i++) {
                        if (i > 0) addText(";", level + 1);
                        addNewRow(level + 1);

                        String newLinePrefix = linePrefix + (char) (startChar+i);
                        addText(String.format("%s)", newLinePrefix), level + 1, "variation-name");
                        // The main line goes last
                        generateMoveControls(node.children().get((i+1) % moves.size()), true, level + 1, false, newLinePrefix);

                    }
                    break;
                }
            }
            node = node.mainNode();
        }
    }

    public void drawMoves() {
        if (moveBox.getWidth() == 0) {
            log.info("Can't generate moves because moveBox width is not known");
            return;
        }
        log.debug("starting to generate move controls");
        long start = System.currentTimeMillis();
        positionLabelMap = new HashMap<>();
        moveBox.getChildren().clear();
        addNewRow(0);

        GameMovesModel.Node rootNode = model.moves().root();
        addMove(rootNode, false, 0, false, false);
        generateMoveControls(rootNode, true, 0, false, "");

        addNewRow(0);
        // TODO: This doesn't show correct (or at least not the same as CB) in case of forfeits etc
        GameResult result = model.header().getResult();
        if (result != null) {
            addText(result.toString(), 0, "main-line");
        }

        if (model.cursor() != null) {
            selectPosition(model.cursor());
        }

        long stop = System.currentTimeMillis();
        log.debug("done in " + (stop-start) + " ms");
    }
}
