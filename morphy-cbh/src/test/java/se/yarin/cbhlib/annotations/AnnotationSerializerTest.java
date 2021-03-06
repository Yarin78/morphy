package se.yarin.cbhlib.annotations;

import org.junit.Test;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.cbhlib.games.Medal;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AnnotationSerializerTest {
    // Test serialize and deserialize individual annotations

    private <T extends Annotation> T serialize(T annotation) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        AnnotationsSerializer.serializeAnnotation(annotation, buf);
        buf.flip();
        return (T) AnnotationsSerializer.deserializeAnnotation(buf);
    }

    @Test
    public void testBlackClockAnnotationSerialization() {
        BlackClockAnnotation before = new BlackClockAnnotation(123456);
        BlackClockAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testChessBaseSymbolAnnotation() {
        SymbolAnnotation before = new SymbolAnnotation(
                NAG.DUBIOUS_MOVE,
                NAG.EDITORIAL_COMMENT,
                NAG.BLACK_SLIGHT_ADVANTAGE
        );
        SymbolAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testComputerEvaluationAnnotationSerialization() {
        ComputerEvaluationAnnotation before = new ComputerEvaluationAnnotation(-73, 0, 3);
        ComputerEvaluationAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testCriticalPositionAnnotationSerialization() {
        CriticalPositionAnnotation before = new CriticalPositionAnnotation(CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME);
        CriticalPositionAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testGameQuotationAnnotationSerialization() {
        GameHeaderModel header = new GameHeaderModel();
        header.setWhite("Mardell, Jimmy");
        header.setBlack("Kasparov, Garry");
        header.setWhiteElo(2100);
        header.setBlackElo(2830);
        header.setDate(new Date(2016, 8, 20));
        header.setEco(new Eco("A57"));
        header.setEvent("unit test");
        header.setResult(GameResult.BLACK_WINS);
        header.setRound(1);
        header.setSubRound(2);
        header.setEventSite("source code");
        header.setEventCategory(3);
        header.setEventCountry("SWE");
        header.setEventRounds(5);
        header.setEventTimeControl(TournamentTimeControl.BLITZ.getName());
        header.setEventType(TournamentType.MATCH.getName());

        GameQuotationAnnotation before = new GameQuotationAnnotation(header);
        GameQuotationAnnotation after = serialize(before);

        assertEquals(before, after);
    }

    @Test
    public void testGraphicalArrowsAnnotationSerialization() {
        GraphicalArrowsAnnotation before = new GraphicalArrowsAnnotation(
                Arrays.asList(
                        new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.GREEN, 7, 9),
                        new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.RED, 60, 12))
        );
        GraphicalArrowsAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testGraphicalSquaresAnnotationSerialization() {
        GraphicalSquaresAnnotation before = new GraphicalSquaresAnnotation(
                Arrays.asList(
                        new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.GREEN, 3),
                        new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.RED, 7))
        );
        GraphicalSquaresAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testInvalidAnnotationSerialization() {
        // An InvalidAnnotation is a special case that occurs when we fail to parse a known annotation
        InvalidAnnotation before = new InvalidAnnotation(93, new byte[] { 0x63 });
        ByteBuffer buf = ByteBuffer.allocate(1024);
        AnnotationsSerializer.serializeAnnotation(before, buf);
        buf.flip();
        UnknownAnnotation after = (UnknownAnnotation) AnnotationsSerializer.deserializeAnnotation(buf);

        assertEquals(before.getAnnotationType(), after.getAnnotationType());
        assertArrayEquals(before.getRawData(), after.getRawData());
    }

    @Test
    public void testMedalAnnotationSerialization() {
        MedalAnnotation before = new MedalAnnotation(Medal.DECIDED_TOURNAMENT, Medal.ENDGAME);
        MedalAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPawnStructureAnnotationSerialization() {
        PawnStructureAnnotation before = new PawnStructureAnnotation(3);
        PawnStructureAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPictureAnnotationSerialization() {
        PictureAnnotation before = new PictureAnnotation(new byte[] { 0x30, (byte) 0xE2 });
        PictureAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPiecePathAnnotationSerialization() {
        PiecePathAnnotation before = new PiecePathAnnotation(3, 15);
        PiecePathAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testSoundAnnotationSerialization() {
        SoundAnnotation before = new SoundAnnotation(new byte[] { 0x23 } );
        SoundAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTextAfterMoveAnnotationSerialization() {
        TextAfterMoveAnnotation before = new TextAfterMoveAnnotation(2, "hello world", Nation.SWEDEN);
        TextAfterMoveAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTextBeforeMoveAnnotationSerialization() {
        TextBeforeMoveAnnotation before = new TextBeforeMoveAnnotation(0, "foobar", Nation.ENGLAND);
        TextBeforeMoveAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTimeControlAnnotationSerialization() {
        TimeControlAnnotation before = new TimeControlAnnotation(
                new TimeControlAnnotation.TimeSerie[] {
                    new TimeControlAnnotation.TimeSerie(120 * 60 * 100, 15 * 100, 40, 1),
                    new TimeControlAnnotation.TimeSerie(30 * 60 * 100, 15 * 100, 20, 1),
                    new TimeControlAnnotation.TimeSerie(0, 0, 1000, 3)
                }
        );
        TimeControlAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTimeSpentAnnotationSerialization() {
        TimeSpentAnnotation before = new TimeSpentAnnotation(0, 13, 5, 7);
        TimeSpentAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTrainingAnnotationSerialization() {
        TrainingAnnotation before = new TrainingAnnotation(new byte[] { 0x01, (byte) 0xFF});
        TrainingAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testUnknownAnnotationSerialization() {
        UnknownAnnotation before = new UnknownAnnotation(91, new byte[] { 0x1A, 0x73, 0x51 });
        UnknownAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testVariationColorAnnotationSerialization() {
        VariationColorAnnotation before = new VariationColorAnnotation(12, 34, 56, true, false);
        VariationColorAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testVideoAnnotationSerialization() {
        VideoAnnotation before = new VideoAnnotation(new byte[] {(byte) 0xA9} );
        VideoAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testWebLinkAnnotationSerialization() {
        WebLinkAnnotation before = new WebLinkAnnotation("myuri", "some text");
        WebLinkAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testWhiteClockAnnotationSerialization() {
        WhiteClockAnnotation before = new WhiteClockAnnotation(987654);
        WhiteClockAnnotation after = serialize(before);
        assertEquals(before, after);
    }
}
