package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import se.yarin.chess.Castles;
import se.yarin.chess.GameMovesModel;

import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MovesParserTest {

    @Before
    public void setup() {
        MovesParser.INTEGRITY_CHECKS_ENABLED = true;
    }

    @Test
    public void parseSimpleGame() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("simplegame.moves.bin"));
        assertEquals("1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#", model.toString());
    }

    @Test
    public void parseSpecialMoves() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("specialmoves.moves.bin"));
        assertEquals("1.e4 e6 2.e5 d5 3.exd6 Nf6 4.d4 Be7 5.Nc3 O-O 6.Bg5 -- 7.Qd2 a6 8.O-O-O b5 9.dxe7 Kh8 10.exd8=N", model.toString());
    }

    @Test
    public void parseVariations() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("variations.moves.bin"));
        assertEquals("1.e4 c5 (1...e6 2.d4 d5 3.Nc3 (3.Nd2; 3.e5 c5 4.c3 Nc6 5.Nf3 Bd7 (5...Qb6)) 3...Nf6 4.Bg5 Bb4 (4...Be7 5.e5); 1...Nf6 2.e5 Nd5 3.Nc3 (3.d4) 3...Nxc3 4.dxc3) 2.Nf3 d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 g6 (5...a6 6.Bg5 e6 7.Qd2 (7.f4 Nbd7 8.Qf3) 7...Nbd7; 5...Nc6 6.Bg5 e6 7.Qd2)", model.toString());
    }

    @Test
    public void parseSetup() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("setup.moves.bin"));
        assertEquals(".....r..|.p...p..|pPbk....|P.N....P|..K.....|........|....R...|........|", model.root().position().toString("|"));
        assertEquals("63.Rf2 f5 64.Rd2+ Ke7", model.toString());
    }

    @Test
    public void parseManyIdenticalPieces() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("manyidenticalpieces.moves.bin"));
        assertEquals("1.Qcd7 Ne4 2.Qf6 Nd5 3.Qhe5 Nf5 4.Qbc5 Ncd4 5.Qb3 Ne6 6.Qed4 Nexd4 7.Qfxd4 N3xd4 8.Qxd4 Nxd4 9.Qf5 Nd6 10.Qbxd5+ Nf7 11.Qfxf7+ Kh8 12.Qxd4#", model.toString());
    }

    @Test
    public void parseMoreIdenticalPieces() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("moreidenticalpieces.moves.bin"));
        assertEquals("1.Rb1 Bbd5 2.Rdc1 Bf4 3.Red1 Bf5 4.Rfe1 Beg5 5.Rgf1 Bh5 6.Na3 Qa8 7.Nb3 Qcb7 8.Nc3 Qdb6 9.Nd3 Qeb5 10.Ne3 Qfg7", model.toString());
    }

    @Test
    public void parsePawnPromotions() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("pawnpromotions.moves.bin"));
        assertEquals("1.e8=N d1=N 2.f8=B c1=B 3.g8=R b1=R 4.h8=Q a1=Q", model.toString());
    }

    @Test
    public void parseBlackStartsWithEnPassant() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("blackstartswithep.moves.bin"));
        assertTrue(model.root().position().isCastles(Castles.BLACK_LONG_CASTLE));
        assertFalse(model.root().position().isCastles(Castles.BLACK_SHORT_CASTLE));
        assertEquals("53...exf3+ 54.Kf1", model.toString());
    }

    @Test
    @Ignore
    public void parseSpecialEncoding() throws IOException, ChessBaseException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("specialencoding.moves.bin"));
        String expected = "1. Nf3 f5 2. d4 e6 3. Bf4 d5 4. e3 Nf6 5. Bd3 c6 6. O-O Be7 7. Nbd2 O-O 8. c4 " +
                "Ne4 9. Bxe4 fxe4 10. Ne5 Nd7 11. c5 Nxe5 12. Bxe5 Bf6 13. Bd6 Be7 14. Bg3 b6 " +
                "15. b4 Bh4 16. Qa4 Bxg3 17. fxg3 Rxf1+ 18. Rxf1 Bb7 19. Nb3 Qg5 20. Rf4 Qh5 21. " +
                "g4 Qe8 22. Qa3 Qd7 23. h3 Qe8 24. b5 Qd7 25. bxc6 Bxc6 26. Qb4 Bb5 27. g5 Rc8 " +
                "28. h4 a5 29. Qc3 a4 30. Nd2 bxc5 31. dxc5 Qc7 32. h5 Qxc5 33. Qe5 Qxe3+ 34. " +
                "Kh2 Bd7 35. g6 Qc3 36. gxh7+ Kxh7 37. Qg5 Be8 38. Nf1 Rc7 39. Ng3 Rf7 40. Qg6+ " +
                "Kg8 41. Qxe6 Qc6 42. Qe5 Rxf4 43. Qxf4 Qf6 44. Qb8 Qe6 45. Ne2 e3 46. a3 Kh7 " +
                "47. g4 Qxg4 48. Qxe8 Qxe2+ 49. Kg3 Qf2+ 50. Kg4 Qg2+ 51. Kf4 e2";
        System.out.println(model.toString());
    }

}
