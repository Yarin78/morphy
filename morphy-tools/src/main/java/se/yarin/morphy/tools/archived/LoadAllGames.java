package se.yarin.morphy.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.annotations.AnnotationBase;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.moves.MovesBase;
import se.yarin.chess.GameMovesModel;

import java.io.File;
import java.io.IOException;

public class LoadAllGames {
    private static final Logger log = LogManager.getLogger();

    public static void main(String[] args) throws IOException, ChessBaseInvalidDataException {
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/movedatafragmentation";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/tmp3/quotation2";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/re";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/My White Openings";
//        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
        String fileBase = "testbases/tmp/Texts/codepage";

        String headerFile = fileBase + ".cbh";
        MovesBase movesBase = MovesBase.open(new File(fileBase + ".cbg"));
        AnnotationBase annotationBase = AnnotationBase.open(new File(fileBase + ".cba"));

        GameHeaderBase base = null;
        int[] annotationCount = new int[256];
        try {
            base = GameHeaderBase.open(new File(headerFile));
//            int start = 4939, stop = 4939; //  From,Martin Severin-Winawer,Szymon Paris Paris 1867.06.04 0-1
//            int start = 4489434, stop = base.size();
//            int start = 4940, stop = base.size(); //  From,Martin Severin-Winawer,Szymon Paris Paris 1867.06.04 0-1
            int start = 1, stop = base.size();
            for (int i = start; i <= stop; i++) {
                GameHeader gameHeader = base.getGameHeader(i);
                if (gameHeader.isGuidingText()) continue;
                GameMovesModel moves = movesBase.getMoves(gameHeader.getMovesOffset(), i);

                log.info("Game #" + i);
                int ofs = gameHeader.getAnnotationOffset();
                if (ofs != 0) {
                    annotationBase.getAnnotations(moves, ofs);
//                    log.info("Annotation size: " + data.limit());
//                    log.info(String.format("#%d: Found %d annotations", i, annotations.size()));
                    int posNo = 0;
//                    for (Annotations anno : annotations.values()) {
//                        for (Annotation annotation : anno.getAll()) {
//                            log.info(String.format("Annotation type %s found in game %d after position %d", annotation.getClass().getName(), i, posNo));
//                            log.info(annotation.toString());
                            /*
                            if (annotation instanceof TextBeforeMoveAnnotation) {
                                TextBeforeMoveAnnotation tama = (TextBeforeMoveAnnotation) annotation;
                                if (tama.getUnknown() != 0) {
                                    log.info(String.format("Game %d position %d: %d %s", i, posNo, tama.getUnknown(), tama.getText()));
                                }
                            }
                            */
                            /*
                            if (annotation instanceof GameQuotationAnnotation) {
                                GameQuotationAnnotation gqa = (GameQuotationAnnotation) annotation;

                                try {
                                    GameMovesModel moves = gqa.getMoves();
                                    if (gqa.hasSetupPosition()) {
                                        log.info("Setup position in game " + i);
                                        log.info("  " + moves.toString());
                                    }
                                } catch (Exception e) {
                                    log.error("Error parsing moves", e);
                                    log.info(String.format("Game %d after move %d: %s", i, posNo, gqa.toString()));
                                    log.info(String.format("  Type is %d, eco is %s, unknown value is %d (%04x)",
                                            gqa.getType(), gqa.getEco(), gqa.getUnknown(), gqa.getUnknown()));
                                    log.info(String.format("  Tournament: type = %s, nation = %s, rounds = %d, time control = %s, category = %d",
                                            gqa.getTournamentType(), gqa.getTournamentCountry(), gqa.getTournamentRounds(), gqa.getTournamentTimeControl(), gqa.getTournamentCategory()));
//                                    log.info("  " + moves.toString());
                                }
                            }
                            */
/*
                            if (annotation instanceof UnknownAnnotation) {
                                GameMovesModel moves = getMoves(movesChannel, gameHeader.getMovesOffset());
                                List<GameMovesModel.Node> nodes = moves.getAllNodes();
                                UnknownAnnotation ua = (UnknownAnnotation) annotation;
                                for (Map.Entry<Integer, byte[]> entry : ua.getMap().entrySet()) {
                                    int annoType = entry.getKey();
                                    byte[] rawData = entry.getValue();
//                                    if (annoType == 0x11) {
//                                    if (annotationCount[annoType]++ < 100) {
//                                    if (annoType == 0x17)
                                        StringBuilder sb = new StringBuilder();
                                        for (byte b : rawData) {
                                            if (b >= 0x20 && b <= 0x7E) {
                                                sb.append((char) b);
                                            } else {
                                                sb.append(".");
                                            }
                                        }

                                    String moveText = posNo == 0 ? "<start>" : nodes.get(posNo).lastMove().toSAN(nodes.get(posNo).ply());
                                    log.info(String.format("Annotation type %02X found in game %d after position %d (%s): %s %s",
                                                annoType, i, posNo, moveText, CBUtil.toHexString(rawData), sb.toString()));
//                                    }
                                }
                            }
                            */
                        }
//                        posNo++;

//                    }
//                }
//                log.info(String.format("#%d: moves offset = %d, moves size = %d, annotation offset = %d, annotation size = %d",
//                        i,
//                        gameHeader.getMovesOffset(), movesSize,
//                        gameHeader.getAnnotationOffset(), annotationSize));

//                log.info(String.format("#%d: annotation offset = %d, annotation header =  %s", i,
//                        gameHeader.getAnnotationOffset(),
//                        getAnnotationHeader(annotationChannel, gameHeader.getAnnotationOffset())));
            }

//            for (int i = 0; i < 256; i++) {
//                if (annotationCount[i] > 0) {
//                    log.info(String.format("UnknownAnnotation type %02X occurs %d times", i, annotationCount[i]));
//                }
//            }
        } catch (IOException e) {
            log.error("IO error reading " + headerFile, e);
        } finally {
            if (base != null) {
                try {
                    base.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
