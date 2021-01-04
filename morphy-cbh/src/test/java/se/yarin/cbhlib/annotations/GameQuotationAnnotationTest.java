package se.yarin.cbhlib.annotations;

import org.junit.Test;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.chess.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static se.yarin.chess.Chess.*;

public class GameQuotationAnnotationTest {

    @Test
    public void testDeserializeGameQuotation() throws IOException, ChessBaseInvalidDataException {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("gamequotation.cba");
        resourceAsStream.skip(26);
        ByteBuffer buf = ByteBuffer.allocate(200);
        int b;
        while ((b = resourceAsStream.read()) >= 0) {
            buf.put((byte) b);
        }
        buf.flip();

        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3).addMove(B8, C6);

        AnnotationsSerializer.deserializeAnnotations(buf, moves);

        GameQuotationAnnotation q = node.getAnnotation(GameQuotationAnnotation.class);
        assertNotNull(q);


        GameModel model = q.getGameModel();

        assertEquals("1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.d3 Be7 5.O-O O-O", model.moves().toString());

        GameHeaderModel header = model.header();

        assertEquals("Mårdell,Jimmy", header.getWhite());
        assertEquals("Carlsen,Magnus", header.getBlack());
        assertEquals(2100, (int) header.getWhiteElo());
        assertEquals(2800, (int) header.getBlackElo());
        assertEquals("Test", header.getEvent());
        assertEquals(2, (int) header.getRound());
        assertEquals(1, (int) header.getSubRound());
        assertEquals("C55", header.getEco().toString());
    }

    @Test
    public void testSerializeDeserializeGameQuotation() {
        GameModel model = new GameModel();
        model.header().setWhite("Mårdell, Jimmy");
        model.header().setBlack("Carlsen, Magnus");
        model.header().setWhiteElo(2100);
        model.header().setBlackElo(2800);
        model.header().setEvent("my event");
        model.header().setEco(new Eco("A33"));
        model.header().setDate(new Date(2016, 9, 5));
        model.header().setEventTimeControl(TournamentTimeControl.BLITZ.getName());
        model.header().setEventType(TournamentType.MATCH.getName());
        model.header().setEventCategory(15);
        model.header().setEventSite("Stockholm");
        model.header().setResult(GameResult.DRAW);

        model.moves().root().addMove(E2, E4).addMove(E7, E6).addMove(D2, D4)
                .addMove(D7, D5).parent().addMove(C7, C5);
        model.moves().root().mainNode().addAnnotation(new SymbolAnnotation(NAG.GOOD_MOVE));

        GameQuotationAnnotation before = new GameQuotationAnnotation(model);

        GameQuotationAnnotation.Serializer serializer = new GameQuotationAnnotation.Serializer();
        ByteBuffer buf = ByteBuffer.allocate(200);
        serializer.serialize(buf, before);
        buf.flip();
        GameQuotationAnnotation after = serializer.deserialize(buf, buf.limit());

        GameModel modelAfter = after.getGameModel();

        assertEquals("1.e4 e6 2.d4 d5", modelAfter.moves().toString());

        assertEquals("Mårdell, Jimmy", modelAfter.header().getWhite());
        assertEquals("Carlsen, Magnus", modelAfter.header().getBlack());
        assertEquals(2100, (int) modelAfter.header().getWhiteElo());
        assertEquals(2800, (int) modelAfter.header().getBlackElo());
        assertEquals("my event", modelAfter.header().getEvent());
        assertEquals("A33", modelAfter.header().getEco().toString());
        assertEquals(new Date(2016, 9, 5), modelAfter.header().getDate());
        assertEquals("blitz", modelAfter.header().getEventTimeControl());
        assertEquals("match", modelAfter.header().getEventType());
        assertEquals(15, (int) modelAfter.header().getEventCategory());
        assertEquals("Stockholm", modelAfter.header().getEventSite());
        assertEquals(GameResult.DRAW, modelAfter.header().getResult());
    }
}
