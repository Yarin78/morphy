package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.TextAfterMoveAnnotation;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.SymbolAnnotation;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Loads a single game from a database, prints verbose output
 */
public class LoadGameVerbose {
    private static final Logger log = LoggerFactory.getLogger(LoadGameVerbose.class);

    public static void main(String[] args) throws IOException, ChessBaseException {
        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
//        String fileBase = "testbases/tmp/re/re";
        File headerFile = new File(fileBase + ".cbh");
        File movesFile = new File(fileBase + ".cbg");
        File annotationFile = new File(fileBase + ".cba");
        FileChannel movesChannel = FileChannel.open(movesFile.toPath());
        FileChannel annotationChannel = FileChannel.open(annotationFile.toPath());

//        int gameId = 757812;
        int gameId = 4342308;

        GameHeaderBase base = GameHeaderBase.open(headerFile);

        GameHeader gameHeader = base.getGameHeader(gameId);
        GameMovesModel model = getMoves(movesChannel, annotationChannel, gameHeader);

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
        for (Annotation annotation : node.getAnnotations().getAll()) {
            System.out.println(String.format("%s     %s", indent, annotation.toString()));
            if (annotation instanceof SymbolAnnotation) {
                SymbolAnnotation sa = (SymbolAnnotation) annotation;
                System.out.println("Symbol: " + sa.getLineEvaluation() + " " + sa.getMoveComment() + " " + sa.getMovePrefix());
            }
        }
    }

    private static GameMovesModel getMoves(FileChannel movesFiles, int ofs) throws IOException, ChessBaseException {
        movesFiles.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(4);
        movesFiles.read(buf);
        buf.position(1);
        int size = ByteBufferUtil.getUnsigned24BitB(buf);
        if (size < 0 || size > 100000) throw new RuntimeException("Unreasonable game size: " + size);
        buf = ByteBuffer.allocate(size);
        movesFiles.position(ofs);
        movesFiles.read(buf);
        buf.position(0);
        return MovesParser.parseMoveData(buf);
    }

    private static ByteBuffer getAnnotationData(FileChannel annotationFile, int ofs) throws IOException {
        annotationFile.position(ofs + 10);
        ByteBuffer buf = ByteBuffer.allocate(4);
        annotationFile.read(buf);
        buf.position(0);
        int size = ByteBufferUtil.getIntB(buf);
        if (size < 0 || size > 100000) throw new RuntimeException("Unreasonable annotation size: " + size);
        buf = ByteBuffer.allocate(size);
        annotationFile.position(ofs);
        annotationFile.read(buf);
        buf.position(0);
        return buf;
    }

    private static GameMovesModel getMoves(FileChannel movesChannel, FileChannel annotationChannel, GameHeader gameHeader)
            throws IOException, ChessBaseException {
        GameMovesModel moves = getMoves(movesChannel, gameHeader.getMovesOffset());
        List<GameMovesModel.Node> allNodes = moves.getAllNodes();
        int ofs = gameHeader.getAnnotationOffset();
        if (ofs != 0) {
            ByteBuffer data = getAnnotationData(annotationChannel, ofs);
            List<Annotations> annotations = AnnotationParser.parseGameAnnotations(data);
            int posNo = 0;
            for (Annotations anno : annotations) {
                for (Annotation annotation : anno.getAll()) {
                    if (posNo < allNodes.size()) {
                        allNodes.get(posNo).addAnnotation(annotation);
                    } else {
                        // This happens!
                        log.info("Annotation " + annotation.toString() + " on position " + posNo + " but highest position index is " + (allNodes.size() - 1) + " in the game");
                        if (annotation instanceof TextAfterMoveAnnotation) {
                            log.info("Text: " + ((TextAfterMoveAnnotation) annotation).getText());
                        }
                    }
                }
                posNo++;
            }
        }
        return moves;
    }
}
