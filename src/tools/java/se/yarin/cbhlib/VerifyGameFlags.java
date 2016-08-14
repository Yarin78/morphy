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
import java.util.*;

import static se.yarin.cbhlib.GameHeaderFlags.*;

/**
 * Iterate through all games in a database and ensure that the correct GameHeader flags are set
 */
public class VerifyGameFlags {
    private static final Logger log = LoggerFactory.getLogger(VerifyGameFlags.class);

    private static GameMovesModel getMoves(MovesBase movesBase, AnnotationBase annotationBase, GameHeader gameHeader)
            throws IOException, ChessBaseException {
        GameMovesModel moves = movesBase.getMoves(gameHeader.getMovesOffset());
        int ofs = gameHeader.getAnnotationOffset();
        if (ofs != 0) {
            Map<Integer, Annotations> annotations = annotationBase.getAnnotations(ofs);
            AnnotationParser.decorateMoves(moves, annotations);
        }
        return moves;
    }

    public enum CheckFlags {
        SUPPRESS_FALSE_POSITIVE, // Allow that Game Header flag says true even if it's false
        SUPPRESS_FALSE_NEGATIVE, // Allow that Game Header flag says false even if it's true
    }

    private static int[] noFalsePositives = new int[GameHeaderFlags.values().length];
    private static int[] noFalseNegatives = new int[GameHeaderFlags.values().length];
    private static int[] noPositiveMatches = new int[GameHeaderFlags.values().length];

    private static void verifyFlag(int gameId, EnumSet<GameHeaderFlags> headerFlags, GameHeaderFlags headerFlag, boolean movesValue, CheckFlags... checkFlags) {
        boolean suppressFalseNeg = false, suppressFalsePos = false;
        for (CheckFlags checkFlag : checkFlags) {
            suppressFalsePos |= (checkFlag == CheckFlags.SUPPRESS_FALSE_POSITIVE);
            suppressFalseNeg |= (checkFlag == CheckFlags.SUPPRESS_FALSE_NEGATIVE) ;
        }
        boolean headerValue = headerFlags.contains(headerFlag);
        if (headerValue && movesValue) {
            noPositiveMatches[headerFlag.ordinal()]++;
        }

        if (headerValue != movesValue) {
            boolean suppress;
            if (headerValue) {
                noFalsePositives[headerFlag.ordinal()]++;
                suppress = suppressFalsePos;
                suppress = true;
            } else {
                noFalseNegatives[headerFlag.ordinal()]++;
                suppress = suppressFalseNeg;
            }
            if (!suppress) {
                log.warn(String.format("Game #%d: %s mismatch (header %s, moves %s)", gameId, headerFlag, headerValue, movesValue));
            }
        }
    }

    public static void verifyHeader(GameHeader header, GameMovesModel moves) {
        verifyMedals(header, moves);

        HashMap<Class, Integer> annotations = new HashMap<>();
        int noVariationMoves = 0;
        int textLength = 0;
        HashSet<Integer> unknownAnnotations = new HashSet<>();
        for (GameMovesModel.Node node : moves.getAllNodes()) {
            if (!node.isMainLine()) noVariationMoves++;
            for (Annotation annotation : node.getAnnotations().getAll()) {
                if (annotation instanceof UnknownAnnotation) {
                    unknownAnnotations.addAll(((UnknownAnnotation) annotation).getMap().keySet());
                }
                if (annotation instanceof TextAfterMoveAnnotation) {
                    textLength += ((TextAfterMoveAnnotation) annotation).getText().length();
                }
                if (annotation instanceof TextBeforeMoveAnnotation) {
                    textLength += ((TextBeforeMoveAnnotation) annotation).getText().length();
                }
                annotations.putIfAbsent(annotation.getClass(), 0);
                annotations.put(annotation.getClass(), annotations.get(annotation.getClass()) + 1);
            }
        }
        int id = header.getId();
        EnumSet<GameHeaderFlags> flags = header.getFlags();

        verifyFlag(id, flags, CRITICAL_POSITION, annotations.containsKey(CriticalPositionAnnotation.class));
        verifyFlag(id, flags, CORRESPONDENCE_HEADER, annotations.containsKey(CorrespondenceMoveAnnotation.class));
        verifyFlag(id, flags, EMBEDDED_VIDEO, annotations.containsKey(VideoAnnotation.class));
        verifyFlag(id, flags, EMBEDDED_AUDIO, annotations.containsKey(SoundAnnotation.class));
        verifyFlag(id, flags, EMBEDDED_PICTURE, annotations.containsKey(PictureAnnotation.class));
        verifyFlag(id, flags, GAME_QUOTATION, annotations.containsKey(GameQuotationAnnotation.class));
        verifyFlag(id, flags, PAWN_STRUCTURE, annotations.containsKey(PawnStructureAnnotation.class));
        verifyFlag(id, flags, PIECE_PATH, annotations.containsKey(PiecePathAnnotation.class));
        verifyFlag(id, flags, TRAINING, annotations.containsKey(TrainingAnnotation.class));
        verifyFlag(id, flags, COMMENTARY, annotations.containsKey(TextAfterMoveAnnotation.class) || annotations.containsKey(TextBeforeMoveAnnotation.class), CheckFlags.SUPPRESS_FALSE_POSITIVE);
        verifyFlag(id, flags, SYMBOLS, annotations.containsKey(SymbolAnnotation.class));
        verifyFlag(id, flags, GRAPHICAL_SQUARES, annotations.containsKey(GraphicalSquaresAnnotation.class));
        verifyFlag(id, flags, GRAPHICAL_ARROWS, annotations.containsKey(GraphicalArrowsAnnotation.class));
        verifyFlag(id, flags, TIME_SPENT, annotations.containsKey(TimeSpentAnnotation.class));
        verifyFlag(id, flags, WHITE_CLOCK, annotations.containsKey(WhiteClockAnnotation.class));
        verifyFlag(id, flags, BLACK_CLOCK, annotations.containsKey(BlackClockAnnotation.class));
        verifyFlag(id, flags, WEB_LINK, annotations.containsKey(WebLinkAnnotation.class));
        verifyFlag(id, flags, ANNO_TYPE_8, unknownAnnotations.contains(8));
        verifyFlag(id, flags, VARIATIONS, !moves.root().isSingleLine());
        verifyFlag(id, flags, SETUP_POSITION, moves.isSetupPosition());

        // The first bytes in moves data is 0x4A if it's a Fischer Random game but we don't support it yet
//        verifyFlag(id, "Fischer random ", flags.contains(FISCHER_RANDOM), moves.isFischerRandom());

        int minTextLength = 0, maxTextLength = 0;
//        int noText = annotations.getOrDefault(TextAfterMoveAnnotation.class, 0) + annotations.getOrDefault(TextBeforeMoveAnnotation.class, 0);
        switch (header.getCommentariesMagnitude()) {
            case 1: minTextLength = 0; maxTextLength = 200; break;
            case 2: minTextLength = 201; maxTextLength = 100000; break;
        }
        if (textLength<minTextLength || textLength>maxTextLength) {
            log.warn(String.format("Game #%d: Text annotations length was %d but expected to be in range [%d,%d]",
                    id, textLength, minTextLength, maxTextLength));
        }
//        if (noText > 0 && header.getCommentariesMagnitude() == 2) {
//        if (noText > 0 && header.getCommentariesMagnitude() == 2) {
//            log.info(String.format("Game #%d: No text annotations = %d, magnitude = %d, length = %d", id, noText, header.getCommentariesMagnitude(), textLength));
//        }

        /*
        int minTrain = 0, maxTrain = 0, noTrain = annotations.getOrDefault(TrainingAnnotation.class, 0);
        switch (header.getTrainingMagnitude()) {
            case 1: minTrain = 1; maxTrain = 10; break;
            case 2: minTrain = 11; maxTrain = 100000; break;
        }

        int minSymbols = 0, maxSymbols = 0;
        int noSymbols = annotations.getOrDefault(SymbolAnnotation.class, 0);
        switch (header.getSymbolsMagnitude()) {
            case 1: minSymbols = 1; maxSymbols = 9; break;
            case 2: minSymbols = 10; maxSymbols = 100000; break;
        }

        int minSquares = 0, maxSquares = 0;
        int noSquares = annotations.getOrDefault(GraphicalSquaresAnnotation.class, 0);
        switch (header.getGraphicalSquaresMagnitude()) {
            case 1: minSquares = 1; maxSquares = 9; break;
            case 2: minSquares = 10; maxSquares = 100000; break;
        }

        int minArrows = 0, maxArrows = 0;
        int noArrows = annotations.getOrDefault(GraphicalArrowsAnnotation.class, 0);
        switch (header.getGraphicalArrowsMagnitude()) {
            case 1: minArrows = 1; maxArrows = 5; break;
            case 2: minArrows = 6; maxArrows = 100000; break;
        }
        int minTime = 0, maxTime = 0;
        int noTime = annotations.getOrDefault(TimeSpentAnnotation.class, 0);
        switch (header.getTimeAnnotationsMagnitude()) {
            case 1: minTime = 1; maxTime = 10; break;
            case 2: minTime = 11; maxTime = 100000; break;
        }

        int minVar = 0, maxVar = 0;
        switch (header.getVariationsMagnitude()) {
            case 1 : minVar = 1; maxVar = 50; break;
            case 2 : minVar = 51; maxVar = 300; break;
            case 3 : minVar = 301; maxVar = 1000; break;
            case 4 : minVar = 1001; maxVar = 1000000; break;
        }


        if (noTrain<minTrain || noTrain>maxTrain) {
            log.warn(String.format("Game #%d: Number of training annotations was %d but expected to be in range [%d,%d]",
                    id, noTrain, minTrain, maxTrain));
        }

        if (noSymbols<minSymbols || noSymbols>maxSymbols) {
            log.warn(String.format("Game #%d: Number of symbols annotations was %d but expected to be in range [%d,%d]",
                    id, noSymbols, minSymbols, maxSymbols));
        }
        if (noSquares<minSquares || noSquares>maxSquares) {
            log.warn(String.format("Game #%d: Number of graphical squares annotations was %d but expected to be in range [%d,%d]",
                    id, noSquares, minSquares, maxSquares));
        }
        if (noArrows<minArrows || noArrows>maxArrows) {
            log.warn(String.format("Game #%d: Number of graphical arrows annotations was %d but expected to be in range [%d,%d]",
                    id, noArrows, minArrows, maxArrows));
        }
        if (noTime<minTime || noTime>maxTime) {
            log.warn(String.format("Game #%d: Number of time spent annotations was %d but expected to be in range [%d,%d]",
                    id, noTime, minTime, maxTime));
        }
        if (noVariationMoves<minVar || noVariationMoves>maxVar) {
            log.warn(String.format("Game #%d: Number of variation moves was %d but expected to be in range [%d,%d]",
                    id, noVariationMoves, minVar, maxVar));
        }
        */
    }

    private static void verifyMedals(GameHeader header, GameMovesModel moves) {
        // Check medals
        EnumSet<Medal> movesMedals = EnumSet.noneOf(Medal.class);
        for (GameMovesModel.Node node : moves.getAllNodes()) {
            MedalAnnotation annotation = node.getAnnotation(MedalAnnotation.class);
            if (annotation != null) {
                movesMedals.addAll(annotation.getMedals());
            }
        }
        for (Medal medal : movesMedals) {
            if (!header.getMedals().contains(medal)) {
                log.warn(String.format("Game #%d: Moves contain medal %s which header does not have",
                        header.getId(), medal));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
//        String fileBase = "testbases/tmp/re/re";
//        String fileBase = "testbases/Jimmys bases/My White Openings";

        File headerFile = new File(fileBase + ".cbh");

        MovesBase movesBase = MovesBase.open(new File(fileBase + ".cbg"));
        AnnotationBase annotationBase = AnnotationBase.open(new File(fileBase + ".cba"));

        GameHeaderBase base = null;
        int noErrors = 0, maxErrors = 10, noUnsupported = 0, noGuidingTexts = 0;
        try {
            base = GameHeaderBase.open(headerFile);
            int cnt=0;
            int start = 1, stop = base.size(), step = 1;
//            int start = 2017673, stop = start, step = 100;
//            int gameIds[] = new int[] {2017678,2769933,2870325,3789643,6161154,2017673};
//            int gameIds[] = new int[] { 4512477, 4753978,4855756, 4904186,5140032,5140036,5140038,5355421}; // Games where annotation exists outside range
//            int gameIds[] = new int[] { 6397679 }; // Game causing BufferUnderflowException

            for (int gameId = start; gameId <= stop && noErrors < maxErrors; gameId+=step) {
//            for (int gameId : gameIds) {
                try {
                    GameHeader gameHeader = base.getGameHeader(gameId);
                    if (gameHeader.isGuidingText()) {
//                        log.info("Game " + gameId + " is a guiding text, skipping");
                        noGuidingTexts++;
                        continue;
                    }
                    GameMovesModel model = getMoves(movesBase, annotationBase, gameHeader);
                    verifyHeader(gameHeader, model);
                    cnt++;
                    if (cnt % 10000 == 0) {
                        log.info("Parsed " + cnt + " games");
                    }
                } catch (ChessBaseUnsupportedException e) {
                    noUnsupported++;
                    continue;
                } catch (Exception e) {
                    log.error("Error parsing data in game " + gameId, e);
                    noErrors++;
                }
            }
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
        if (noErrors >= maxErrors) {
            log.info("Too many errors, aborting");
        }
        log.info(String.format("%d unsupported games, %d guiding texts", noUnsupported, noGuidingTexts));

        for (GameHeaderFlags flag : GameHeaderFlags.values()) {
            int ix = flag.ordinal();
            int tot = noPositiveMatches[ix] + noFalsePositives[ix] + noFalseNegatives[ix];
            if (tot > 0) {
                log.info(String.format("%25s  Pos match: %5d    Mismatch: %3d  (false pos: %3d, false neg: %3d)",
                        flag, noPositiveMatches[ix],
                        noFalsePositives[ix] + noFalseNegatives[ix],
                        noFalsePositives[ix], noFalseNegatives[ix]));
            }
        }
    }
}
