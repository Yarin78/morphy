package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.*;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.SymbolAnnotation;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;

public class LoadAllGames {
    private static final Logger log = LoggerFactory.getLogger(LoadAllGames.class);

    private static int getMovesSize(FileChannel movesFile, int ofs) throws IOException {
        movesFile.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(4);
        movesFile.read(buf);
        buf.position(0);
        return ByteBufferUtil.getIntB(buf);
    }

    private static GameMovesModel getMoves(FileChannel movesFiles, int ofs) throws IOException, ChessBaseException {
        movesFiles.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(4);
        movesFiles.read(buf);
        buf.position(0);
        int size = ByteBufferUtil.getIntB(buf);
        buf = ByteBuffer.allocate(size);
        movesFiles.position(ofs);
        movesFiles.read(buf);
        buf.position(0);
        return MovesParser.parseMoveData(buf);
    }

    private static int getAnnotationSize(FileChannel annotationFile, int ofs) throws IOException {
        if (ofs == 0) return 0;
        annotationFile.position(ofs + 10);
        ByteBuffer buf = ByteBuffer.allocate(4);
        annotationFile.read(buf);
        buf.position(0);
        return ByteBufferUtil.getIntB(buf);
    }

    private static ByteBuffer getAnnotationData(FileChannel annotationFile, int ofs) throws IOException {
        annotationFile.position(ofs + 10);
        ByteBuffer buf = ByteBuffer.allocate(4);
        annotationFile.read(buf);
        buf.position(0);
        int size = ByteBufferUtil.getIntB(buf);
        buf = ByteBuffer.allocate(size);
        annotationFile.position(ofs);
        annotationFile.read(buf);
        buf.position(0);
        return buf;
    }

    private static String getAnnotationHeader(FileChannel annotationFile, int ofs) throws IOException {
        if (ofs == 0) return "";
        annotationFile.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(14);
        annotationFile.read(buf);
        buf.position(0);
        return CBUtil.toHexString(buf);
    }

    public static void main(String[] args) throws IOException, ChessBaseInvalidDataException {
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/movedatafragmentation";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/tmp3/quotation2";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/re";
//        String fileBase = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp/My White Openings";
        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
        File headerFile = new File(fileBase + ".cbh");
        File movesFile = new File(fileBase + ".cbg");
        File annotationFile = new File(fileBase + ".cba");
        FileChannel movesChannel = FileChannel.open(movesFile.toPath());
        FileChannel annotationChannel = FileChannel.open(annotationFile.toPath());
        GameHeaderBase base = null;
        int[] annotationCount = new int[256];
        try {
            base = GameHeaderBase.open(headerFile);
//            int start = 4939, stop = 4939; //  From,Martin Severin-Winawer,Szymon Paris Paris 1867.06.04 0-1
//            int start = 4489434, stop = base.size();
//            int start = 4940, stop = base.size(); //  From,Martin Severin-Winawer,Szymon Paris Paris 1867.06.04 0-1
            int start = 1, stop = base.size();
            for (int i = start; i <= stop; i++) {
                GameHeader gameHeader = base.getGameHeader(i);
//                int movesSize = getMovesSize(movesChannel, gameHeader.getMovesOffset());
                /*
                if ((movesSize & 0x80000000) == 0x80000000) {
//                    log.info(String.format("#%d: Not encoded (guiding text?)", i));
                }
                if ((movesSize & 0x40000000) == 0x40000000) {
//                    log.info(String.format("#%d: Setup position", i));
                }

                if ((movesSize & 0x3F000000) != 0) {
                    log.info(String.format("#%d: Strange initial byte %08X", i, movesSize));
                }
                */
//                movesSize &= 0xFFFFFF;

//                int annotationSize = getAnnotationSize(annotationChannel, gameHeader.getAnnotationOffset());
                int ofs = gameHeader.getAnnotationOffset();
//                log.info("Annotations start at ofs " + ofs);
                if (ofs != 0) {
                    ByteBuffer data = getAnnotationData(annotationChannel, ofs);
//                    log.info("Annotation size: " + data.limit());
                    List<Annotations> annotations = AnnotationParser.parseGameAnnotations(data);
//                    log.info(String.format("#%d: Found %d annotations", i, annotations.size()));
                    int posNo = 0;
                    for (Annotations anno : annotations) {
                        for (Annotation annotation : anno.getAll()) {
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
                        posNo++;

                    }
                }
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
