package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.SymbolAnnotation;
import se.yarin.cbhlib.annotations.TextAfterMoveAnnotation;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Loads a single game from a database, prints verbose output
 */
public class LoadGameVerbose {
    private static final Logger log = LoggerFactory.getLogger(LoadGameVerbose.class);

    public static void main(String[] args) throws IOException, ChessBaseException {
//        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
//        String fileBase = "testbases/CHESS LITERATURE 3/OPENINGS/Chessbase - Pirc Defence - Complete Cd/Chessbase - Pirc Defence - Complete Cd/B06-Base";
        String fileBase = "testbases/tmp/Texts/codepage";
//        String fileBase = "testbases/tmp/re/re";
        GameHeaderBase base = GameHeaderBase.open(new File(fileBase + ".cbh"));
        MovesBase movesBase = MovesBase.open(new File(fileBase + ".cbg"));
        AnnotationBase annotationBase = AnnotationBase.open(new File(fileBase + ".cba"));

        int gameId = 2;
//
        GameHeader gameHeader = base.getGameHeader(gameId);
        GameMovesModel model = getMoves(movesBase, annotationBase, gameHeader);

        HashMap<GameMovesModel.Node, Integer> nodePosMap = new HashMap<>();

        int nodeId = 0;
        for (GameMovesModel.Node node : model.getAllNodes()) {
            nodePosMap.put(node, nodeId++);
        }

        ArrayList<GameMovesModel.Node> infixNodes = new ArrayList<>();
        infixNodes.add(model.root());
        createInfixOrder(model.root(), infixNodes);
        for (GameMovesModel.Node infixNode : infixNodes) {
            show(infixNode, nodePosMap.get(infixNode));
        }
    }

    private static void createInfixOrder(GameMovesModel.Node current, List<GameMovesModel.Node> nodes) {
        if (current == null) return;
        boolean first = true;
        for (GameMovesModel.Node node : current.children()) {
            nodes.add(node);
            if (first) {
                first = false;
            } else {
                createInfixOrder(node, nodes);
            }
        }
        createInfixOrder(current.mainNode(), nodes);
    }

    private static void show(GameMovesModel.Node node, int nodeId) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < node.getVariationDepth(); i++) {
            indent.append("  ");
        }
        String mv = node.lastMove() == null ? "" : node.lastMove().toSAN(node.ply() - 1);
        System.out.println(String.format("Node %3d: %s%-10s", nodeId, indent, mv));
        for (Annotation annotation : node.getAnnotations()) {
            System.out.println(String.format("%s     %s", indent, annotation.toString()));
            if (annotation instanceof SymbolAnnotation) {
                SymbolAnnotation sa = (SymbolAnnotation) annotation;
                System.out.println("Symbol: " + sa.getLineEvaluation() + " " + sa.getMoveComment() + " " + sa.getMovePrefix());
            }
            if (annotation instanceof TextAfterMoveAnnotation) {
                System.out.println("Text: " + ((TextAfterMoveAnnotation) annotation).getText());
            }
        }
    }

    private static GameMovesModel getMoves(MovesBase movesBase, AnnotationBase annotationBase, GameHeader gameHeader)
            throws IOException, ChessBaseException {
        GameMovesModel moves = movesBase.getMoves(gameHeader.getMovesOffset());
        annotationBase.getAnnotations(moves, gameHeader.getAnnotationOffset());
        return moves;
    }
}
