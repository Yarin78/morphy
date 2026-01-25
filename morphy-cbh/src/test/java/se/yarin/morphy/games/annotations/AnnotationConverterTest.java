package se.yarin.morphy.games.annotations;

import org.junit.Test;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.Medal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;
import static se.yarin.chess.Chess.*;

public class AnnotationConverterTest {

    // ========== Tests for PGN → ChessBase Conversion ==========

    @Test
    public void testConvertSingleNAGToChessBase() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof NAGAnnotation);

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.NONE, symbol.lineEvaluation());
        assertEquals(NAG.NONE, symbol.movePrefix());
    }

    @Test
    public void testConvertMultipleNAGsDifferentTypes() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new NAGAnnotation(NAG.WHITE_SLIGHT_ADVANTAGE));
        node.addAnnotation(new NAGAnnotation(NAG.WITH_THE_IDEA));

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.WHITE_SLIGHT_ADVANTAGE, symbol.lineEvaluation());
        assertEquals(NAG.WITH_THE_IDEA, symbol.movePrefix());
    }

    @Test
    public void testConvertMultipleNAGsSameType() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new NAGAnnotation(NAG.DUBIOUS_MOVE));

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.NONE, symbol.lineEvaluation());
        assertEquals(NAG.NONE, symbol.movePrefix());
    }

    @Test
    public void testConvertCommentaryAfterMove() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new CommentaryAfterMoveAnnotation("This is a great move!"));

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);
        TextAfterMoveAnnotation text = (TextAfterMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("This is a great move!", text.text());
    }

    @Test
    public void testConvertCommentaryBeforeMove() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new CommentaryBeforeMoveAnnotation("Black should be careful here"));

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryBeforeMoveAnnotation);

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextBeforeMoveAnnotation);
        TextBeforeMoveAnnotation text = (TextBeforeMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("Black should be careful here", text.text());
    }

    // ========== Tests for Storage → Generic Conversion ==========

    @Test
    public void testConvertSymbolAnnotationToPgn() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof NAGAnnotation);
        NAGAnnotation nag = (NAGAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, nag.getNag());
    }

    @Test
    public void testConvertSymbolAnnotationWithMultipleNAGsToPgn() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.WITH_THE_IDEA, NAG.WHITE_SLIGHT_ADVANTAGE));

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());

        assertEquals(3, node.getAnnotations().size());

        boolean hasGoodMove = false;
        boolean hasWithTheIdea = false;
        boolean hasWhiteAdvantage = false;

        for (Annotation annotation : node.getAnnotations()) {
            assertTrue(annotation instanceof NAGAnnotation);
            NAG nag = ((NAGAnnotation) annotation).getNag();
            if (nag == NAG.GOOD_MOVE) hasGoodMove = true;
            if (nag == NAG.WITH_THE_IDEA) hasWithTheIdea = true;
            if (nag == NAG.WHITE_SLIGHT_ADVANTAGE) hasWhiteAdvantage = true;
        }

        assertTrue(hasGoodMove);
        assertTrue(hasWithTheIdea);
        assertTrue(hasWhiteAdvantage);
    }

    // ========== Tests for Clock Annotations ==========

    @Test
    public void testClockAnnotationEncoding() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableWhiteClockAnnotation.of(538500)); // 1:29:45 = (1*3600 + 29*60 + 45) * 100

        AnnotationConverter.convertToPgnAnnotations(annotations);

        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%clk 1:29:45]"));
    }

    @Test
    public void testClockAnnotationDecodingWithContext() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%clk 1:29:45]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations, Player.WHITE);

        WhiteClockAnnotation clock = annotations.getByClass(WhiteClockAnnotation.class);
        assertNotNull(clock);
        assertEquals(538500, clock.clockTime()); // 1:29:45 = (1*3600 + 29*60 + 45) * 100 centiseconds
    }

    @Test
    public void testClockAnnotationDecodingBlackContext() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%clk 1:28:30]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations, Player.BLACK);

        BlackClockAnnotation clock = annotations.getByClass(BlackClockAnnotation.class);
        assertNotNull(clock);
        assertEquals(531000, clock.clockTime()); // 1:28:30 in centiseconds
    }

    @Test
    public void testExplicitClockAnnotations() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%clkw 1:00:00] [%clkb 0:59:00]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        WhiteClockAnnotation whiteClock = annotations.getByClass(WhiteClockAnnotation.class);
        BlackClockAnnotation blackClock = annotations.getByClass(BlackClockAnnotation.class);

        assertNotNull(whiteClock);
        assertNotNull(blackClock);
        assertEquals(360000, whiteClock.clockTime());
        assertEquals(354000, blackClock.clockTime());
    }

    // ========== Tests for Eval Annotations ==========

    @Test
    public void testEvalAnnotationEncodingPositive() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(150, 0, 20)); // +1.50 at depth 20

        AnnotationConverter.convertToPgnAnnotations(annotations);

        assertEquals(1, annotations.size());
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%eval +1.50/20]"));
    }

    @Test
    public void testEvalAnnotationEncodingNegative() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(-75, 0, 18)); // -0.75 at depth 18

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%eval -0.75/18]"));
    }

    @Test
    public void testEvalAnnotationEncodingMate() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(5, 1, 30)); // #5 at depth 30

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%eval #5/30]"));
    }

    @Test
    public void testEvalAnnotationEncodingMateBlack() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(-3, 1, 25)); // #-3 at depth 25

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%eval #-3/25]"));
    }

    @Test
    public void testEvalAnnotationDecodingPositive() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%eval +1.50/20]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        ComputerEvaluationAnnotation eval = annotations.getByClass(ComputerEvaluationAnnotation.class);
        assertNotNull(eval);
        assertEquals(150, eval.eval());
        assertEquals(0, eval.evalType());
        assertEquals(20, eval.ply());
    }

    @Test
    public void testEvalAnnotationDecodingMate() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%eval #5/30]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        ComputerEvaluationAnnotation eval = annotations.getByClass(ComputerEvaluationAnnotation.class);
        assertNotNull(eval);
        assertEquals(5, eval.eval());
        assertEquals(1, eval.evalType());
        assertEquals(30, eval.ply());
    }

    @Test
    public void testEvalType3Skipped() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(100, 3, 10)); // Type 3 should be skipped

        AnnotationConverter.convertToPgnAnnotations(annotations);

        assertEquals(0, annotations.size()); // No output since type 3 is skipped
    }

    // ========== Tests for Time Annotations ==========

    @Test
    public void testTimeSpentAnnotationEncoding() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTimeSpentAnnotation.of(0, 5, 30, 0));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%emt 0:05:30]"));
    }

    @Test
    public void testTimeSpentAnnotationWithFlag() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTimeSpentAnnotation.of(0, 0, 45, 30));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%emt 0:00:45|30]"));
    }

    @Test
    public void testTimeSpentAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%emt 1:15:00]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TimeSpentAnnotation ts = annotations.getByClass(TimeSpentAnnotation.class);
        assertNotNull(ts);
        assertEquals(1, ts.hours());
        assertEquals(15, ts.minutes());
        assertEquals(0, ts.seconds());
    }

    @Test
    public void testTimeControlAnnotationEncoding() {
        List<TimeControlAnnotation.TimeSerie> series = Arrays.asList(
                ImmutableTimeSerie.of(540000, 0, 40, 0),    // 90m for 40 moves
                ImmutableTimeSerie.of(180000, 0, 1000, 0),  // 30m for rest
                ImmutableTimeSerie.of(3000, 0, 1000, 0)     // 30s for rest
        );
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTimeControlAnnotation.of(series));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%tc 90m/40+30m+30s]"));
    }

    @Test
    public void testTimeControlAnnotationWithIncrement() {
        List<TimeControlAnnotation.TimeSerie> series = Arrays.asList(
                ImmutableTimeSerie.of(90000, 1000, 1000, 0)  // 15m+10s for rest
        );
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTimeControlAnnotation.of(series));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%tc (15m+10s)]"));
    }

    @Test
    public void testTimeControlAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%tc 90m/40+30m]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TimeControlAnnotation tc = annotations.getByClass(TimeControlAnnotation.class);
        assertNotNull(tc);
        assertEquals(2, tc.timeSeries().size());
        assertEquals(540000, tc.timeSeries().get(0).start());
        assertEquals(40, tc.timeSeries().get(0).moves());
    }

    // ========== Tests for Metadata Annotations ==========

    @Test
    public void testCriticalPositionAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableCriticalPositionAnnotation.of(CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%crit middlegame]"));
    }

    @Test
    public void testCriticalPositionAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%crit endgame]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        CriticalPositionAnnotation crit = annotations.getByClass(CriticalPositionAnnotation.class);
        assertNotNull(crit);
        assertEquals(CriticalPositionAnnotation.CriticalPositionType.ENDGAME, crit.type());
    }

    @Test
    public void testMedalAnnotationEncoding() {
        EnumSet<Medal> medals = EnumSet.of(Medal.TACTICS, Medal.SACRIFICE, Medal.BEST_GAME);
        Annotations annotations = new Annotations();
        annotations.add(ImmutableMedalAnnotation.of(medals));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%medal"));
        assertTrue(commentary.contains("tactics"));
        assertTrue(commentary.contains("sacrifice"));
        assertTrue(commentary.contains("best"));
    }

    @Test
    public void testMedalAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%medal tactics,sacrifice,best]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        MedalAnnotation medal = annotations.getByClass(MedalAnnotation.class);
        assertNotNull(medal);
        assertTrue(medal.medals().contains(Medal.TACTICS));
        assertTrue(medal.medals().contains(Medal.SACRIFICE));
        assertTrue(medal.medals().contains(Medal.BEST_GAME));
    }

    @Test
    public void testVariationColorAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableVariationColorAnnotation.of(255, 0, 0, true, false)); // Red, moves only

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%varcolor #FF0000 M]"));
    }

    @Test
    public void testVariationColorAnnotationWithAllFlags() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableVariationColorAnnotation.of(255, 255, 0, true, true)); // Yellow, ML

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%varcolor #FFFF00 ML]"));
    }

    @Test
    public void testVariationColorAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%varcolor #0000FF L]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        VariationColorAnnotation vc = annotations.getByClass(VariationColorAnnotation.class);
        assertNotNull(vc);
        assertEquals(0, vc.red());
        assertEquals(0, vc.green());
        assertEquals(255, vc.blue());
        assertFalse(vc.onlyMoves());
        assertTrue(vc.onlyMainline());
    }

    @Test
    public void testPiecePathAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutablePiecePathAnnotation.of(3, Chess.strToSqi("e4")));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%path e4 3]"));
    }

    @Test
    public void testPawnStructureAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutablePawnStructureAnnotation.of(3));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%pawnstruct 3]"));
    }

    @Test
    public void testVideoStreamTimeAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableVideoStreamTimeAnnotation.of(12345));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%vst 12345]"));
    }

    // ========== Tests for String Annotations ==========

    @Test
    public void testWebLinkAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableWebLinkAnnotation.of("https://lichess.org/study/abc123", "See analysis"));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%weblink \"https://lichess.org/study/abc123\" \"See analysis\"]"));
    }

    @Test
    public void testWebLinkAnnotationWithEscaping() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableWebLinkAnnotation.of("https://example.com?a=1", "Click \"here\""));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("\\\"here\\\""));
    }

    @Test
    public void testWebLinkAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%weblink \"https://lichess.org\" \"Analyze\"]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        WebLinkAnnotation wl = annotations.getByClass(WebLinkAnnotation.class);
        assertNotNull(wl);
        assertEquals("https://lichess.org", wl.url());
        assertEquals("Analyze", wl.text());
    }

    // ========== Tests for Binary Annotations ==========

    @Test
    public void testTrainingAnnotation() {
        byte[] data = "Hello World".getBytes();
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTrainingAnnotation.of(data));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%train SGVsbG8gV29ybGQ=]"));
    }

    @Test
    public void testTrainingAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%train SGVsbG8gV29ybGQ=]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TrainingAnnotation train = annotations.getByClass(TrainingAnnotation.class);
        assertNotNull(train);
        assertEquals("Hello World", new String(train.rawData()));
    }

    @Test
    public void testCorrespondenceAnnotation() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        Annotations annotations = new Annotations();
        annotations.add(ImmutableCorrespondenceMoveAnnotation.of(data));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%corr AQIDBAU=]"));
    }

    // ========== Tests for Text Annotation Format ==========

    @Test
    public void testTextBeforeMoveAnnotationEncoding() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextBeforeMoveAnnotation.of("This is before the move"));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryBeforeMoveAnnotation);
        String commentary = ((CommentaryBeforeMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%pre This is before the move]"));
    }

    @Test
    public void testTextBeforeMoveAnnotationWithLanguage() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextBeforeMoveAnnotation.builder()
                .text("Dies ist auf Deutsch")
                .language(Nation.GERMANY)
                .build());

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryBeforeMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%pre:GER Dies ist auf Deutsch]"));
    }

    @Test
    public void testTextAfterMoveAnnotationWithLanguage() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextAfterMoveAnnotation.builder()
                .text("Ceci est en français")
                .language(Nation.FRANCE)
                .build());

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%post:FRA Ceci est en français]"));
    }

    @Test
    public void testTextBeforeMoveAnnotationDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%pre Important decision coming]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TextBeforeMoveAnnotation text = annotations.getByClass(TextBeforeMoveAnnotation.class);
        assertNotNull(text);
        assertEquals("Important decision coming", text.text());
    }

    @Test
    public void testTextBeforeMoveAnnotationDecodingWithLanguage() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%pre:GER Eine wichtige Entscheidung]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TextBeforeMoveAnnotation text = annotations.getByClass(TextBeforeMoveAnnotation.class);
        assertNotNull(text);
        assertEquals("Eine wichtige Entscheidung", text.text());
        assertEquals(Nation.GERMANY, text.language());
    }

    @Test
    public void testTextAfterMoveWithLanguageDecoding() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("[%post:FRA Un bon coup]"));

        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TextAfterMoveAnnotation text = annotations.getByClass(TextAfterMoveAnnotation.class);
        assertNotNull(text);
        assertEquals("Un bon coup", text.text());
        assertEquals(Nation.FRANCE, text.language());
    }

    // ========== Tests for Game Quotation ==========

    @Test
    public void testGameQuotationAnnotationEncodingHeaderOnly() {
        GameHeaderModel header = new GameHeaderModel();
        header.setWhite("Carlsen, Magnus");
        header.setBlack("Nepomniachtchi, Ian");
        header.setEvent("World Championship");
        header.setEventSite("Dubai");
        header.setDate(new Date(2021, 12, 3));
        header.setResult(GameResult.WHITE_WINS);
        header.setWhiteElo(2856);
        header.setBlackElo(2782);
        header.setEco(new Eco("C88"));

        Annotations annotations = new Annotations();
        annotations.add(new GameQuotationAnnotation(header));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%quote"));
        assertTrue(commentary.contains("Carlsen, Magnus"));
        assertTrue(commentary.contains("Nepomniachtchi, Ian"));
        assertTrue(commentary.contains("World Championship"));
        assertTrue(commentary.contains("1-0"));
        assertTrue(commentary.contains("C88"));
    }

    // ========== Tests for Round-Trip Conversion ==========

    @Test
    public void testRoundTripTextWithBrackets() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(ImmutableTextAfterMoveAnnotation.builder()
                .text("This need escaping [yarin] 1-0")
                .language(Nation.ENGLAND)
                .build());

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());

        CommentaryAfterMoveAnnotation annotation = node.getAnnotation(CommentaryAfterMoveAnnotation.class);
        System.out.println(annotation.getCommentary());

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());
        TextAfterMoveAnnotation annotation2 = node.getAnnotation(TextAfterMoveAnnotation.class);
        System.out.println(annotation2.text());

        assertEquals("This need escaping [yarin] 1-0", annotation2.text());
    }

    @Test
    public void testRoundTripPgnToChessBaseToPgn() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new NAGAnnotation(NAG.WHITE_SLIGHT_ADVANTAGE));
        node.addAnnotation(new CommentaryAfterMoveAnnotation("Excellent choice"));

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());
        assertEquals(2, node.getAnnotations().size());

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());
        assertEquals(3, node.getAnnotations().size());

        boolean hasGoodMove = false;
        boolean hasWhiteAdvantage = false;
        boolean hasCommentary = false;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof NAGAnnotation) {
                NAG nag = ((NAGAnnotation) annotation).getNag();
                if (nag == NAG.GOOD_MOVE) hasGoodMove = true;
                if (nag == NAG.WHITE_SLIGHT_ADVANTAGE) hasWhiteAdvantage = true;
            }
            if (annotation instanceof CommentaryAfterMoveAnnotation) {
                assertEquals("Excellent choice", ((CommentaryAfterMoveAnnotation) annotation).getCommentary());
                hasCommentary = true;
            }
        }

        assertTrue(hasGoodMove);
        assertTrue(hasWhiteAdvantage);
        assertTrue(hasCommentary);
    }

    @Test
    public void testRoundTripGraphicalAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        GraphicalSquaresAnnotation squares = ImmutableGraphicalSquaresAnnotation.of(
                Arrays.asList(
                        ImmutableSquare.of(GraphicalAnnotationColor.GREEN, Chess.strToSqi("e4")),
                        ImmutableSquare.of(GraphicalAnnotationColor.YELLOW, Chess.strToSqi("d5"))
                )
        );
        GraphicalArrowsAnnotation arrows = ImmutableGraphicalArrowsAnnotation.of(
                Arrays.asList(
                        ImmutableArrow.of(GraphicalAnnotationColor.RED, Chess.strToSqi("e2"), Chess.strToSqi("e4"))
                )
        );
        TextAfterMoveAnnotation text = ImmutableTextAfterMoveAnnotation.of("King's pawn opening");

        node.addAnnotation(squares);
        node.addAnnotation(arrows);
        node.addAnnotation(text);

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        GraphicalSquaresAnnotation squaresBack = node.getAnnotation(GraphicalSquaresAnnotation.class);
        GraphicalArrowsAnnotation arrowsBack = node.getAnnotation(GraphicalArrowsAnnotation.class);
        TextAfterMoveAnnotation textBack = node.getAnnotation(TextAfterMoveAnnotation.class);

        assertNotNull(squaresBack);
        assertNotNull(arrowsBack);
        assertNotNull(textBack);

        assertEquals(2, squaresBack.squares().size());
        assertEquals(1, arrowsBack.arrows().size());
        assertEquals("King's pawn opening", textBack.text());
    }

    @Test
    public void testRoundTripClockAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableWhiteClockAnnotation.of(539745));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations, Player.WHITE);

        WhiteClockAnnotation clock = annotations.getByClass(WhiteClockAnnotation.class);
        assertNotNull(clock);
        assertEquals(539700, clock.clockTime()); // Minor rounding due to seconds-only precision
    }

    @Test
    public void testRoundTripEvalAnnotation() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableComputerEvaluationAnnotation.of(150, 0, 20));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        ComputerEvaluationAnnotation eval = annotations.getByClass(ComputerEvaluationAnnotation.class);
        assertNotNull(eval);
        assertEquals(150, eval.eval());
        assertEquals(0, eval.evalType());
        assertEquals(20, eval.ply());
    }

    @Test
    public void testRoundTripMedalAnnotation() {
        EnumSet<Medal> medals = EnumSet.of(Medal.TACTICS, Medal.SACRIFICE);
        Annotations annotations = new Annotations();
        annotations.add(ImmutableMedalAnnotation.of(medals));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        MedalAnnotation medalBack = annotations.getByClass(MedalAnnotation.class);
        assertNotNull(medalBack);
        assertTrue(medalBack.medals().contains(Medal.TACTICS));
        assertTrue(medalBack.medals().contains(Medal.SACRIFICE));
    }

    @Test
    public void testRoundTripBinaryAnnotation() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTrainingAnnotation.of(data));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        TrainingAnnotation train = annotations.getByClass(TrainingAnnotation.class);
        assertNotNull(train);
        assertArrayEquals(data, train.rawData());
    }

    @Test
    public void testRoundTripGameQuotationHeaderOnly() {
        GameHeaderModel header = new GameHeaderModel();
        header.setWhite("Carlsen, Magnus");
        header.setBlack("Nepomniachtchi, Ian");
        header.setEvent("World Championship");
        header.setEventSite("Dubai");
        header.setDate(new Date(2021, 12, 3));
        header.setResult(GameResult.WHITE_WINS);
        header.setWhiteElo(2856);
        header.setBlackElo(2782);
        header.setEco(new Eco("C88"));

        Annotations annotations = new Annotations();
        annotations.add(new GameQuotationAnnotation(header));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        GameQuotationAnnotation quote = annotations.getByClass(GameQuotationAnnotation.class);
        assertNotNull(quote);
        assertEquals("Carlsen, Magnus", quote.header().getWhite());
        assertEquals("Nepomniachtchi, Ian", quote.header().getBlack());
        assertEquals("World Championship", quote.header().getEvent());
        assertEquals("Dubai", quote.header().getEventSite());
        assertEquals(new Date(2021, 12, 3), quote.header().getDate());
        assertEquals(GameResult.WHITE_WINS, quote.header().getResult());
        assertEquals(Integer.valueOf(2856), quote.header().getWhiteElo());
        assertEquals(Integer.valueOf(2782), quote.header().getBlackElo());
        assertEquals(new Eco("C88"), quote.header().getEco());
        assertEquals(0, quote.getGameModel().moves().countPly(true));
    }

    @Test
    public void testRoundTripGameQuotationWithMoves() {
        GameHeaderModel header = new GameHeaderModel();
        header.setWhite("Fischer, Bobby");
        header.setBlack("Spassky, Boris");
        header.setEvent("World Championship");
        header.setEventSite("Reykjavik");
        header.setDate(new Date(1972, 7, 11));
        header.setResult(GameResult.WHITE_WINS);
        header.setWhiteElo(2785);
        header.setBlackElo(2660);
        header.setEco(new Eco("B97"));

        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root();
        node = node.addMove(E2, E4);
        node = node.addMove(C7, C5);
        node = node.addMove(G1, F3);

        GameModel game = new GameModel(header, moves);
        Annotations annotations = new Annotations();
        annotations.add(new GameQuotationAnnotation(game));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        GameQuotationAnnotation quote = annotations.getByClass(GameQuotationAnnotation.class);
        assertNotNull(quote);
        assertEquals("Fischer, Bobby", quote.header().getWhite());
        assertEquals("Spassky, Boris", quote.header().getBlack());
        assertTrue(quote.hasGame());

        // Verify moves were preserved
        GameModel roundTrippedGame = quote.getGameModel();
        assertEquals(3, roundTrippedGame.moves().countPly(false));
    }

    @Test
    public void testRoundTripGameQuotationWithAllHeaderFields() {
        // Test that ALL header fields are preserved during round-trip conversion
        GameHeaderModel header = new GameHeaderModel();

        // Basic player info
        header.setWhite("Carlsen, Magnus");
        header.setBlack("Nepomniachtchi, Ian");
        header.setWhiteElo(2856);
        header.setBlackElo(2782);
        header.setWhiteTeam("Norway");
        header.setBlackTeam("Russia");

        // Game info
        header.setResult(GameResult.WHITE_WINS);
        header.setLineEvaluation(NAG.GOOD_MOVE);
        header.setDate(new Date(2021, 12, 3));
        header.setEco(new Eco("C88"));
        header.setRound(6);
        header.setSubRound(1);

        // Event info
        header.setEvent("World Championship");
        header.setEventDate(new Date(2021, 11, 24));
        header.setEventEndDate(new Date(2021, 12, 10));
        header.setEventSite("Dubai");
        header.setEventCountry("UAE");
        header.setEventCategory(22);
        header.setEventRounds(14);
        header.setEventType("match");
        header.setEventTimeControl("classical");

        // Source info
        header.setSource("ChessBase");
        header.setSourceTitle("TWIC 1413");
        header.setSourceDate(new Date(2021, 12, 6));
        header.setAnnotator("Yarin Morphy");
        header.setGameTag("brilliancy");

        Annotations annotations = new Annotations();
        annotations.add(new GameQuotationAnnotation(header));

        AnnotationConverter.convertToPgnAnnotations(annotations);
        AnnotationConverter.convertToChessBaseAnnotations(annotations);

        GameQuotationAnnotation quote = annotations.getByClass(GameQuotationAnnotation.class);
        assertNotNull(quote);

        // Verify ALL fields are preserved
        assertEquals("Carlsen, Magnus", quote.header().getWhite());
        assertEquals("Nepomniachtchi, Ian", quote.header().getBlack());
        assertEquals(Integer.valueOf(2856), quote.header().getWhiteElo());
        assertEquals(Integer.valueOf(2782), quote.header().getBlackElo());
        assertEquals("Norway", quote.header().getWhiteTeam());
        assertEquals("Russia", quote.header().getBlackTeam());

        assertEquals(GameResult.WHITE_WINS, quote.header().getResult());
        assertEquals(NAG.GOOD_MOVE, quote.header().getLineEvaluation());
        assertEquals(new Date(2021, 12, 3), quote.header().getDate());
        assertEquals(new Eco("C88"), quote.header().getEco());
        assertEquals(Integer.valueOf(6), quote.header().getRound());
        assertEquals(Integer.valueOf(1), quote.header().getSubRound());

        assertEquals("World Championship", quote.header().getEvent());
        assertEquals(new Date(2021, 11, 24), quote.header().getEventDate());
        assertEquals(new Date(2021, 12, 10), quote.header().getEventEndDate());
        assertEquals("Dubai", quote.header().getEventSite());
        assertEquals("UAE", quote.header().getEventCountry());
        assertEquals(Integer.valueOf(22), quote.header().getEventCategory());
        assertEquals(Integer.valueOf(14), quote.header().getEventRounds());
        assertEquals("match", quote.header().getEventType());
        assertEquals("classical", quote.header().getEventTimeControl());

        assertEquals("ChessBase", quote.header().getSource());
        assertEquals("TWIC 1413", quote.header().getSourceTitle());
        assertEquals(new Date(2021, 12, 6), quote.header().getSourceDate());
        assertEquals("Yarin Morphy", quote.header().getAnnotator());
        assertEquals("brilliancy", quote.header().getGameTag());
    }

    // ========== Tests for Mixed Annotations ==========

    @Test
    public void testMultipleAnnotationTypes() {
        Annotations annotations = new Annotations();
        annotations.add(ImmutableGraphicalSquaresAnnotation.of(
                Arrays.asList(ImmutableSquare.of(GraphicalAnnotationColor.GREEN, Chess.strToSqi("e4")))
        ));
        annotations.add(ImmutableWhiteClockAnnotation.of(360000));
        annotations.add(ImmutableComputerEvaluationAnnotation.of(25, 0, 20));
        annotations.add(ImmutableCriticalPositionAnnotation.of(CriticalPositionAnnotation.CriticalPositionType.OPENING));
        annotations.add(ImmutableTextAfterMoveAnnotation.of("A classic opening"));

        AnnotationConverter.convertToPgnAnnotations(annotations);

        assertEquals(1, annotations.size());
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();

        // Check ordering per spec
        int cslIdx = commentary.indexOf("[%csl");
        int clkIdx = commentary.indexOf("[%clk");
        int evalIdx = commentary.indexOf("[%eval");
        int critIdx = commentary.indexOf("[%crit");
        int textIdx = commentary.indexOf("A classic opening");

        assertTrue(cslIdx >= 0);
        assertTrue(clkIdx >= 0);
        assertTrue(evalIdx >= 0);
        assertTrue(critIdx >= 0);
        assertTrue(textIdx >= 0);

        // Verify order: graphical < clock < eval < metadata < text
        assertTrue(cslIdx < clkIdx);
        assertTrue(clkIdx < evalIdx);
        assertTrue(evalIdx < critIdx);
        assertTrue(critIdx < textIdx);
    }

    @Test
    public void testParseMultipleEncodedAnnotations() {
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation(
                "[%csl Ge4,Rd5] [%cal Ye2e4] [%clk 1:30:00] [%eval +0.25/22] The Ruy Lopez."
        ));

        AnnotationConverter.convertToChessBaseAnnotations(annotations, Player.WHITE);

        assertNotNull(annotations.getByClass(GraphicalSquaresAnnotation.class));
        assertNotNull(annotations.getByClass(GraphicalArrowsAnnotation.class));
        assertNotNull(annotations.getByClass(WhiteClockAnnotation.class));
        assertNotNull(annotations.getByClass(ComputerEvaluationAnnotation.class));
        assertNotNull(annotations.getByClass(TextAfterMoveAnnotation.class));

        TextAfterMoveAnnotation text = annotations.getByClass(TextAfterMoveAnnotation.class);
        assertEquals("The Ruy Lopez.", text.text());
    }

    // ========== Context-Aware Conversion ==========

    @Test
    public void testConvertToChessBaseAnnotationsWhiteContext() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node e4 = moves.root().addMove(E2, E4);
        e4.addAnnotation(new CommentaryAfterMoveAnnotation("[%clk 1:59:52]"));

        // e4 was White's move, so lastMoveBy = WHITE
        AnnotationConverter.convertToChessBaseAnnotations(e4.getAnnotations(), Player.WHITE);

        // Should create a WhiteClockAnnotation
        WhiteClockAnnotation clock = e4.getAnnotation(WhiteClockAnnotation.class);
        assertNotNull(clock);
    }

    @Test
    public void testConvertToChessBaseAnnotationsBlackContext() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node e4 = moves.root().addMove(E2, E4);
        GameMovesModel.Node e5 = e4.addMove(E7, E5);
        e5.addAnnotation(new CommentaryAfterMoveAnnotation("[%clk 1:58:30]"));

        // e5 was Black's move, so lastMoveBy = BLACK
        AnnotationConverter.convertToChessBaseAnnotations(e5.getAnnotations(), Player.BLACK);

        // Should create a BlackClockAnnotation
        BlackClockAnnotation clock = e5.getAnnotation(BlackClockAnnotation.class);
        assertNotNull(clock);
    }

    // ========== Existing Tests (kept for backward compatibility) ==========

    @Test
    public void testConvertGraphicalSquaresToPgn() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        GraphicalSquaresAnnotation squares = ImmutableGraphicalSquaresAnnotation.of(
                java.util.Arrays.asList(
                        ImmutableSquare.of(GraphicalAnnotationColor.GREEN, Chess.strToSqi("a4")),
                        ImmutableSquare.of(GraphicalAnnotationColor.RED, Chess.strToSqi("b5"))
                )
        );
        node.addAnnotation(squares);

        AnnotationConverter.convertToPgnAnnotations(node.getAnnotations());

        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);

        CommentaryAfterMoveAnnotation commentary = (CommentaryAfterMoveAnnotation) node.getAnnotations().get(0);
        String text = commentary.getCommentary();

        assertTrue(text.contains("[%csl"));
        assertTrue(text.contains("Ga4"));
        assertTrue(text.contains("Rb5"));
    }

    @Test
    public void testParseGraphicalSquaresFromGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        node.addAnnotation(new CommentaryAfterMoveAnnotation("[%csl Ga4,Rb5]"));

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        GraphicalSquaresAnnotation squares = node.getAnnotation(GraphicalSquaresAnnotation.class);
        assertNotNull(squares);
        assertEquals(2, squares.squares().size());

        List<GraphicalSquaresAnnotation.Square> squareList = new ArrayList<>(squares.squares());
        assertEquals(GraphicalAnnotationColor.GREEN, squareList.get(0).color());
        assertEquals(Chess.strToSqi("a4"), squareList.get(0).sqi());
        assertEquals(GraphicalAnnotationColor.RED, squareList.get(1).color());
        assertEquals(Chess.strToSqi("b5"), squareList.get(1).sqi());

        assertNull(node.getAnnotation(TextAfterMoveAnnotation.class));
    }

    @Test
    public void testParseGraphicalWithTextFromGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        node.addAnnotation(new CommentaryAfterMoveAnnotation("[%csl Ge4] Best opening move"));

        AnnotationConverter.convertToChessBaseAnnotations(node.getAnnotations());

        GraphicalSquaresAnnotation squares = node.getAnnotation(GraphicalSquaresAnnotation.class);
        assertNotNull(squares);
        assertEquals(1, squares.squares().size());

        TextAfterMoveAnnotation text = node.getAnnotation(TextAfterMoveAnnotation.class);
        assertNotNull(text);
        assertEquals("Best opening move", text.text());
    }
}
