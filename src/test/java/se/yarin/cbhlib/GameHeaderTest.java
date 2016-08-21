package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.chess.LineEvaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameHeaderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameHeaderFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameHeaderFile = materializeStream(this.getClass().getResourceAsStream("cbhlib_test.cbh"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbh");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = stream.read(buf);
            if (r == -1) {
                break;
            }
            fos.write(buf, 0, r);
        }
        fos.close();
        return file;
    }

    @Test
    public void testSerializeDeserializeGameHeader() {
        GameHeaderBase base = new GameHeaderBase();
        GameHeader before = GameHeader.builder()
                .id(3)
                .game(true)
                .deleted(false)
                .guidingText(false)
                .movesOffset(7)
                .annotationOffset(100)
                .whitePlayerId(3)
                .blackPlayerId(7)
                .tournamentId(10)
                .annotationOffset(73)
                .sourceId(1)
                .playedDate(new Date(2015,3,1))
                .result(GameResult.BOTH_LOST)
                .round(2)
                .subRound(1)
                .whiteElo(2014)
                .blackElo(2037)
                .eco(new Eco("C01"))
                .lineEvaluation(LineEvaluation.DEVELOPMENT_ADVANTAGE)
                .medals(EnumSet.of(Medal.MODEL_GAME, Medal.USER))
                .flags(EnumSet.of(
                        GameHeaderFlags.VARIATIONS,
                        GameHeaderFlags.SYMBOLS,
                        GameHeaderFlags.GRAPHICAL_SQUARES,
                        GameHeaderFlags.TIME_SPENT))
                .variationsMagnitude(1)
                .commentariesMagnitude(0)
                .symbolsMagnitude(2)
                .graphicalArrowsMagnitude(0)
                .graphicalSquaresMagnitude(1)
                .trainingMagnitude(0)
                .timeAnnotationsMagnitude(1)
                .noMoves(13)
                .build();

        ByteBuffer serialized = base.serialize(before);
        GameHeader after = base.deserialize(3, serialized);

        assertEquals(before, after);
    }

    @Test
    public void testDeserializeSimpleGame() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);

        GameHeader header = base.getGameHeader(1);
        assertTrue(header.isGame());
        assertFalse(header.isGuidingText());
        assertFalse(header.isDeleted());
        assertEquals(26, header.getMovesOffset());
        assertEquals(0, header.getAnnotationOffset());
        assertEquals(0, header.getWhitePlayerId());
        assertEquals(1, header.getBlackPlayerId());
        assertEquals(0, header.getTournamentId());
        assertEquals(0, header.getAnnotatorId());
        assertEquals(0, header.getSourceId());
        assertEquals(new Date(2016, 5, 6), header.getPlayedDate());
        assertEquals(GameResult.WHITE_WINS, header.getResult());
        assertEquals(0, header.getRound());
        assertEquals(0, header.getSubRound());
        assertEquals(0, header.getWhiteElo());
        assertEquals(0, header.getBlackElo());
        assertEquals(new Eco("C20"), header.getEco());
        assertEquals(LineEvaluation.NO_EVALUATION, header.getLineEvaluation());
        assertEquals(0, header.getMedals().size());
        assertEquals(0, header.getFlags().size());
        assertEquals(0, header.getVariationsMagnitude());
        assertEquals(0, header.getSymbolsMagnitude());
        assertEquals(0, header.getGraphicalSquaresMagnitude());
        assertEquals(0, header.getGraphicalArrowsMagnitude());
        assertEquals(4, header.getNoMoves());
    }

    @Test
    public void testDeserializeGame2() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);

        GameHeader header = base.getGameHeader(2);
        assertTrue(header.isGame());
        assertFalse(header.isGuidingText());
        assertFalse(header.isDeleted());
        assertEquals(38, header.getMovesOffset());
        assertEquals(0, header.getAnnotationOffset());
        assertEquals(2, header.getWhitePlayerId());
        assertEquals(3, header.getBlackPlayerId());
        assertEquals(2, header.getTournamentId());
        assertEquals(1, header.getAnnotatorId());
        assertEquals(2, header.getSourceId());
        assertEquals(new Date(2016, 5, 9), header.getPlayedDate());
        assertEquals(GameResult.NOT_FINISHED, header.getResult());
        assertEquals(1, header.getRound());
        assertEquals(2, header.getSubRound());
        assertEquals(2150, header.getWhiteElo());
        assertEquals(2000, header.getBlackElo());
        assertEquals(new Eco("A00"), header.getEco());
        assertEquals(LineEvaluation.WITH_COMPENSATION, header.getLineEvaluation());
        assertEquals(0, header.getMedals().size());
        assertEquals(0, header.getFlags().size());
        assertEquals(0, header.getVariationsMagnitude());
        assertEquals(0, header.getSymbolsMagnitude());
        assertEquals(0, header.getGraphicalSquaresMagnitude());
        assertEquals(0, header.getGraphicalArrowsMagnitude());
        assertEquals(0, header.getNoMoves());
    }

    @Test
    public void testDeserializeGameSimpleAnnotations() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);

        GameHeader header = base.getGameHeader(3);
        assertTrue(header.isGame());
        assertFalse(header.isGuidingText());
        assertFalse(header.isDeleted());
        assertEquals(43, header.getMovesOffset());
        assertEquals(26, header.getAnnotationOffset());
        assertEquals(new Date(2016, 5, 9), header.getPlayedDate());
        assertEquals(GameResult.NOT_FINISHED, header.getResult());
        assertEquals(LineEvaluation.NO_EVALUATION, header.getLineEvaluation());
        assertEquals(0, header.getMedals().size());
        assertEquals(EnumSet.of(GameHeaderFlags.COMMENTARY, GameHeaderFlags.SYMBOLS), header.getFlags());
        assertEquals(0, header.getVariationsMagnitude());
        assertEquals(1, header.getSymbolsMagnitude());
        assertEquals(0, header.getGraphicalSquaresMagnitude());
        assertEquals(0, header.getGraphicalArrowsMagnitude());
    }

    @Test
    public void testDeserializeDeleteGame() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        GameHeader header = base.getGameHeader(4);
        assertTrue(header.isGame());
        assertFalse(header.isGuidingText());
        assertTrue(header.isDeleted());
    }

    @Test
    public void testDeserializeSetupPositionGame() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        GameHeader header = base.getGameHeader(7);
        assertTrue(header.isGame());
        assertEquals(EnumSet.of(GameHeaderFlags.SETUP_POSITION), header.getFlags());
    }

    @Test
    public void testDeserializeText() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        GameHeader header = base.getGameHeader(8);
        assertTrue(header.isGame());
        assertTrue(header.isGuidingText());
        assertEquals(2, header.getTournamentId());
        assertEquals(1, header.getAnnotatorId());
        assertEquals(2, header.getRound());
    }
}
