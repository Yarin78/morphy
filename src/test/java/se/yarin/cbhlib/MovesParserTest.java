package se.yarin.cbhlib;

import org.junit.Before;
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
    public void parseSimpleGame() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("simplegame.moves.bin"));
        assertEquals("1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#", model.toString());
    }

    @Test
    public void parseSpecialMoves() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("specialmoves.moves.bin"));
        assertEquals("1.e4 e6 2.e5 d5 3.exd6 Nf6 4.d4 Be7 5.Nc3 O-O 6.Bg5 -- 7.Qd2 a6 8.O-O-O b5 9.dxe7 Kh8 10.exd8=N", model.toString());
    }

    @Test
    public void parseVariations() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("variations.moves.bin"));
        assertEquals("1.e4 c5 (1...e6 2.d4 d5 3.Nc3 (3.Nd2; 3.e5 c5 4.c3 Nc6 5.Nf3 Bd7 (5...Qb6)) 3...Nf6 4.Bg5 Bb4 (4...Be7 5.e5); 1...Nf6 2.e5 Nd5 3.Nc3 (3.d4) 3...Nxc3 4.dxc3) 2.Nf3 d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 g6 (5...a6 6.Bg5 e6 7.Qd2 (7.f4 Nbd7 8.Qf3) 7...Nbd7; 5...Nc6 6.Bg5 e6 7.Qd2)", model.toString());
    }

    @Test
    public void parseSetup() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("setup.moves.bin"));
        assertEquals(".....r..|.p...p..|pPbk....|P.N....P|..K.....|........|....R...|........|", model.root().position().toString("|"));
        assertEquals("63.Rf2 f5 64.Rd2+ Ke7", model.toString());
    }

    @Test
    public void parseManyIdenticalPieces() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("manyidenticalpieces.moves.bin"));
        assertEquals("1.Qcd7 Ne4 2.Qf6 Nd5 3.Qhe5 Nf5 4.Qbc5 Ncd4 5.Qb3 Ne6 6.Qed4 Nexd4 7.Qfxd4 N3xd4 8.Qxd4 Nxd4 9.Qf5 Nd6 10.Qbxd5+ Nf7 11.Qfxf7+ Kh8 12.Qxd4#", model.toString());
    }

    @Test
    public void parseMoreIdenticalPieces() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("moreidenticalpieces.moves.bin"));
        assertEquals("1.Rb1 Bbd5 2.Rdc1 Bf4 3.Red1 Bf5 4.Rfe1 Beg5 5.Rgf1 Bh5 6.Na3 Qa8 7.Nb3 Qcb7 8.Nc3 Qdb6 9.Nd3 Qeb5 10.Ne3 Qfg7", model.toString());
    }

    @Test
    public void parsePawnPromotions() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("pawnpromotions.moves.bin"));
        assertEquals("1.e8=N d1=N 2.f8=B c1=B 3.g8=R b1=R 4.h8=Q a1=Q", model.toString());
    }

    @Test
    public void parseBlackStartsWithEnPassant() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = MovesParser.parseMoveData(ResourceLoader.loadResource("blackstartswithep.moves.bin"));
        assertTrue(model.root().position().isCastles(Castles.BLACK_LONG_CASTLE));
        assertFalse(model.root().position().isCastles(Castles.BLACK_SHORT_CASTLE));
        assertEquals("53...exf3+ 54.Kf1", model.toString());
    }

}