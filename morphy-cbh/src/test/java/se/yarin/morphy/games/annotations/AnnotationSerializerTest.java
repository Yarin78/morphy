package se.yarin.morphy.games.annotations;

import org.junit.Test;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.games.Medal;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;

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
        BlackClockAnnotation before = ImmutableBlackClockAnnotation.of(123456);
        BlackClockAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testChessBaseSymbolAnnotation() {
        SymbolAnnotation before = ImmutableSymbolAnnotation.of(
                NAG.DUBIOUS_MOVE,
                NAG.EDITORIAL_COMMENT,
                NAG.BLACK_SLIGHT_ADVANTAGE
        );
        SymbolAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testComputerEvaluationAnnotationSerialization() {
        ComputerEvaluationAnnotation before = ImmutableComputerEvaluationAnnotation.of(-73, 0, 3);
        ComputerEvaluationAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testCriticalPositionAnnotationSerialization() {
        CriticalPositionAnnotation before = ImmutableCriticalPositionAnnotation.of(CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME);
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
        GraphicalArrowsAnnotation before = ImmutableGraphicalArrowsAnnotation.of(
                Arrays.asList(
                        ImmutableArrow.of(GraphicalAnnotationColor.GREEN, 7, 9),
                        ImmutableArrow.of(GraphicalAnnotationColor.RED, 60, 12))
        );
        GraphicalArrowsAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testGraphicalSquaresAnnotationSerialization() {
        GraphicalSquaresAnnotation before = ImmutableGraphicalSquaresAnnotation.of(
                Arrays.asList(
                        ImmutableSquare.of(GraphicalAnnotationColor.GREEN, 3),
                        ImmutableSquare.of(GraphicalAnnotationColor.RED, 7))
        );
        GraphicalSquaresAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testInvalidAnnotationSerialization() {
        // An InvalidAnnotation is a special case that occurs when we fail to parse a known annotation
        InvalidAnnotation before = ImmutableInvalidAnnotation.of(93, new byte[] { 0x63 });
        ByteBuffer buf = ByteBuffer.allocate(1024);
        AnnotationsSerializer.serializeAnnotation(before, buf);
        buf.flip();
        UnknownAnnotation after = (UnknownAnnotation) AnnotationsSerializer.deserializeAnnotation(buf);

        assertEquals(before.annotationType(), after.annotationType());
        assertArrayEquals(before.rawData(), after.rawData());
    }

    @Test
    public void testMedalAnnotationSerialization() {
        MedalAnnotation before = ImmutableMedalAnnotation.of(EnumSet.of(Medal.DECIDED_TOURNAMENT, Medal.ENDGAME));
        MedalAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPawnStructureAnnotationSerialization() {
        PawnStructureAnnotation before = ImmutablePawnStructureAnnotation.of(3);
        PawnStructureAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPictureAnnotationSerialization() {
        PictureAnnotation before = ImmutablePictureAnnotation.of(new byte[] { 0x30, (byte) 0xE2 });
        PictureAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testPiecePathAnnotationSerialization() {
        PiecePathAnnotation before = ImmutablePiecePathAnnotation.of(3, 15);
        PiecePathAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testSoundAnnotationSerialization() {
        SoundAnnotation before = ImmutableSoundAnnotation.of(new byte[] { 0x23 } );
        SoundAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTextAfterMoveAnnotationSerialization() {
        TextAfterMoveAnnotation before = ImmutableTextAfterMoveAnnotation.builder()
                .unknown(2)
                .text("hello world")
                .language(Nation.SWEDEN)
                .build();
        TextAfterMoveAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTextBeforeMoveAnnotationSerialization() {
        TextBeforeMoveAnnotation before = ImmutableTextBeforeMoveAnnotation.builder()
                .text("foobar")
                .language(Nation.ENGLAND)
                .build();
        TextBeforeMoveAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTimeControlAnnotationSerialization() {
        TimeControlAnnotation before = ImmutableTimeControlAnnotation.of(
                Arrays.asList(
                    ImmutableTimeSerie.of(120 * 60 * 100, 15 * 100, 40, 1),
                    ImmutableTimeSerie.of(30 * 60 * 100, 15 * 100, 20, 1),
                    ImmutableTimeSerie.of(0, 0, 1000, 3)
                )
        );
        TimeControlAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTimeSpentAnnotationSerialization() {
        TimeSpentAnnotation before = ImmutableTimeSpentAnnotation.of(0, 13, 5, 7);
        TimeSpentAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testTrainingAnnotationSerialization() {
        TrainingAnnotation before = ImmutableTrainingAnnotation.of(new byte[] { 0x01, (byte) 0xFF});
        TrainingAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testUnknownAnnotationSerialization() {
        UnknownAnnotation before = ImmutableUnknownAnnotation.of(91, new byte[] { 0x1A, 0x73, 0x51 });
        UnknownAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testVariationColorAnnotationSerialization() {
        VariationColorAnnotation before = ImmutableVariationColorAnnotation.of(12, 34, 56, true, false);
        VariationColorAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testVideoAnnotationSerialization() {
        VideoAnnotation before = ImmutableVideoAnnotation.of(new byte[] {(byte) 0xA9} );
        VideoAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testWebLinkAnnotationSerialization() {
        WebLinkAnnotation before = ImmutableWebLinkAnnotation.of("myuri", "some text");
        WebLinkAnnotation after = serialize(before);
        assertEquals(before, after);
    }

    @Test
    public void testWhiteClockAnnotationSerialization() {
        WhiteClockAnnotation before = ImmutableWhiteClockAnnotation.of(987654);
        WhiteClockAnnotation after = serialize(before);
        assertEquals(before, after);
    }
}
