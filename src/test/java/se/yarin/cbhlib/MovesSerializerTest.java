package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class MovesSerializerTest {

    @Before
    public void setup() {
        CompactMoveEncoder.INTEGRITY_CHECKS_ENABLED = true;
    }

    @Test
    public void parseSimpleGame() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("simplegame.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseSpecialMoves() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("specialmoves.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.e4 e6 2.e5 d5 3.exd6 Nf6 4.d4 Be7 5.Nc3 O-O 6.Bg5 -- 7.Qd2 a6 8.O-O-O b5 9.dxe7 Kh8 10.exd8=N", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseVariations() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("variations.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.e4 c5 (1...e6 2.d4 d5 3.Nc3 (3.Nd2; 3.e5 c5 4.c3 Nc6 5.Nf3 Bd7 (5...Qb6)) 3...Nf6 4.Bg5 Bb4 (4...Be7 5.e5); 1...Nf6 2.e5 Nd5 3.Nc3 (3.d4) 3...Nxc3 4.dxc3) 2.Nf3 d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 g6 (5...a6 6.Bg5 e6 7.Qd2 (7.f4 Nbd7 8.Qf3) 7...Nbd7; 5...Nc6 6.Bg5 e6 7.Qd2)", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseSetup() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("setup.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals(".....r..|.p...p..|pPbk....|P.N....P|..K.....|........|....R...|........|", model.root().position().toString("|"));
        assertEquals("63.Rf2 f5 64.Rd2+ Ke7", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseManyIdenticalPieces() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("manyidenticalpieces.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.Qcd7 Ne4 2.Qf6 Nd5 3.Qhe5 Nf5 4.Qbc5 Ncd4 5.Qb3 Ne6 6.Qed4 Nexd4 7.Qfxd4 N3xd4 8.Qxd4 Nxd4 9.Qf5 Nd6 10.Qbxd5+ Nf7 11.Qfxf7+ Kh8 12.Qxd4#", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseMoreIdenticalPieces() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("moreidenticalpieces.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.Rb1 Bbd5 2.Rdc1 Bf4 3.Red1 Bf5 4.Rfe1 Beg5 5.Rgf1 Bh5 6.Na3 Qa8 7.Nb3 Qcb7 8.Nc3 Qdb6 9.Nd3 Qeb5 10.Ne3 Qfg7", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parsePawnPromotions() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("pawnpromotions.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertEquals("1.e8=N d1=N 2.f8=B c1=B 3.g8=R b1=R 4.h8=Q a1=Q", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void parseBlackStartsWithEnPassant() throws IOException, ChessBaseException {
        ByteBuffer buf = ResourceLoader.loadResource("blackstartswithep.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(buf);
        assertTrue(model.root().position().isCastles(Castles.BLACK_LONG_CASTLE));
        assertFalse(model.root().position().isCastles(Castles.BLACK_SHORT_CASTLE));
        assertEquals("53...exf3+ 54.Kf1", model.toString());

        ByteBuffer serialized = MovesSerializer.serializeMoves(model);
        buf.rewind();
        assertEquals(serialized, buf);
    }

    @Test
    public void serializeDeserializeEmptyGame() throws ChessBaseMoveDecodingException {
        ByteBuffer buf = MovesSerializer.serializeMoves(new GameMovesModel());
        assertEquals(5, buf.limit());
        GameMovesModel gameMovesModel = MovesSerializer.deserializeMoves(buf);
        assertEquals(0, gameMovesModel.countPly(true));

        buf = MovesSerializer.serializeMoves(new GameMovesModel(), 1);
        assertEquals(4, buf.limit());
        gameMovesModel = MovesSerializer.deserializeMoves(buf);
        assertEquals(0, gameMovesModel.countPly(true));
    }

    @Test
    public void serializeDeserializeGeneratedGames() throws ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        GameGenerator gameGenerator = new GameGenerator();
        for (int noMoves = 0; noMoves < 80; noMoves+=3) {
            GameMovesModel inputMoves = gameGenerator.getRandomGameMoves(noMoves);
            gameGenerator.addRandomVariationMoves(inputMoves, noMoves*2);

            for (int mode = 0; mode < 8; mode++) {
                ByteBuffer buf = MovesSerializer.serializeMoves(inputMoves, mode);
                GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
                assertEquals(inputMoves.toString(), outputMoves.toString());
            }
        }
    }

    @Test
    public void parseSpecialEncoding() throws IOException, ChessBaseException {
        // This is mode 5
        GameMovesModel model = MovesSerializer.deserializeMoves(ResourceLoader.loadResource("specialencoding.moves.bin"));
        String expected = "1.Nf3 f5 2.d4 e6 3.Bf4 d5 4.e3 Nf6 5.Bd3 c6 6.O-O Be7 7.Nbd2 O-O 8.c4 " +
                "Ne4 9.Bxe4 fxe4 10.Ne5 Nd7 11.c5 Nxe5 12.Bxe5 Bf6 13.Bd6 Be7 14.Bg3 b6 " +
                "15.b4 Bh4 16.Qa4 Bxg3 17.fxg3 Rxf1+ 18.Rxf1 Bb7 19.Nb3 Qg5 20.Rf4 Qh5 21." +
                "g4 Qe8 22.Qa3 Qd7 23.h3 Qe8 24.b5 Qd7 25.bxc6 Bxc6 26.Qb4 Bb5 27.g5 Rc8 " +
                "28.h4 a5 29.Qc3 a4 30.Nd2 bxc5 31.dxc5 Qc7 32.h5 Qxc5 33.Qe5 Qxe3+ 34." +
                "Kh2 Bd7 35.g6 Qc3 36.gxh7+ Kxh7 37.Qg5 Be8 38.Nf1 Rc7 39.Ng3 Rf7 40.Qg6+ " +
                "Kg8 41.Qxe6 Qc6 42.Qe5 Rxf4 43.Qxf4 Qf6 44.Qb8 Qe6 45.Ne2 e3 46.a3 Kh7 " +
                "47.g4 Qxg4 48.Qxe8 Qxe2+ 49.Kg3 Qf2+ 50.Kg4 Qg2+ 51.Kf4 e2";
        assertEquals(expected, model.toString());
    }


    @Test
    public void serializeDeserializeCrazyGame() throws ChessBaseMoveDecodingException {
        // Serializes and deserializes a game in all supported modes
        // that exploits many of the corner cases in the ChessBase encodings

        for (int mode = 0; mode < 8; mode++) {
            GameMovesModel inputMoves = TestGames.getCrazyGame();
            ByteBuffer buf = MovesSerializer.serializeMoves(inputMoves, mode);
            GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
            assertEquals(inputMoves.toString(), outputMoves.toString());
        }
    }

    @Test
    public void serializeDeserializeSetupPositionGame() throws ChessBaseMoveDecodingException {
        // Serializes and deserializes a game in all supported modes
        // that uses setup positions

        for (int mode = 0; mode < 8; mode++) {
            GameMovesModel inputMoves = TestGames.getEndGame();
            ByteBuffer buf = MovesSerializer.serializeMoves(inputMoves, mode);
            GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
            assertEquals(inputMoves.toString(), outputMoves.toString());
        }
    }

    @Test
    public void serializeDeserializeVariationsGame() throws ChessBaseMoveDecodingException {
        // Serializes and deserializes a game in all supported modes
        // that has variations

        for (int mode = 0; mode < 8; mode++) {
            GameMovesModel inputMoves = TestGames.getVariationGame();
            ByteBuffer buf = MovesSerializer.serializeMoves(inputMoves, mode);
            GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
            assertEquals(inputMoves.toString(), outputMoves.toString());
        }
    }

    @Test
    public void deserializeBrokenChess960Game() throws IOException, ChessBaseMoveDecodingException {
        // Game 3730252 in Mega Database 2016
        GameMovesModel model = MovesSerializer.deserializeMoves(ResourceLoader.loadResource("brokenchess960.moves.bin"));
        String expected = "1.b3 b6 2.e4 e5 3.Nf3 f6 4.Bc4 Bc5 5.Qf1 Ne7 6.O-O-O Nbc6 7.Ba6 Rb8 8.Bc4 Qf8 9.Nc3 Nd4 10.Nxd4 Bxd4 11.Kb1 Nc8 12.f4 Nd6 13.Bd3 b5 14.Nxb5 Nxb5 15.Bxb5 Bxa1 16.Kxa1 exf4 17.Qd3 Qd6 18.Qxd6 cxd6 19.Bd3 f5 20.exf5 Bxg2 21.Rg1 f3 22.Bf1 O-O 23.Bxg2 fxg2 24.Rxg2 Rxf5 25.Rg4 Kf7 26.Kb2 g5 27.Rd4 Rb6 28.h4 h6 29.hxg5 hxg5 30.Rh1 Kg7 31.c4 d5 32.c5 Rc6 33.b4 d6 34.cxd6 Rxd6 35.a4 a6 36.Kc3 Rb6 37.b5 axb5 38.a5 Rc6+ 39.Kb4 Rc4+ 40.Rxc4 bxc4 41.a6 Rf8 42.Kc5 Kg6 43.a7 g4 44.Kxd5 Ra8 45.Ra1 g3 46.Kc6 Kf5 47.Kb7 Rxa7+ 48.Rxa7 Ke4 49.Ra3 Kf4 50.Rc3";
        assertEquals(expected, model.toString());

        // Game 3730254 in Mega Database 2016
        model = MovesSerializer.deserializeMoves(ResourceLoader.loadResource("brokenchess960_2.moves.bin"));
        expected = "1.c4 c5 2.b3 b6 3.Nf3 e6 4.d4 cxd4 5.Nxd4 Nc6 6.Nf3 Be7 7.Nc3 O-O 8.g3 Nf6 9.Bg2 Nb4 10.O-O d5 11.a3 Na6 12.cxd5 Nxd5 13.b4 Nxc3 14.Bxc3 Rxd1 15.Rxd1 Rd8 16.Rxd8+ Bxd8 17.Qb2 f6 18.Bh3 Nc7 19.Nd4 Qd7 20.Bg4 f5 21.Bf3 Nd5 22.Qb3 Nxc3 23.Qxc3 Bf6 24.Bxa8 Bxd4 25.Qd3 Qd8 26.Bb7 Kf7 27.Ba6 Bf6 28.Qb3 Ke7 29.Bc4 Qd6 30.Qf3 Be5 31.Bb3 Kf6 32.h4 Bb2 33.g4 Bxa3 34.Qc3+ Kg6 35.h5+ Kh6 36.g5+ Kxh5 37.Qxg7 Kg4 38.g6 h5 39.Qc3";
        assertEquals(expected, model.toString());
    }

    @Test
    public void serializeDeserializeChess960Game() throws ChessBaseMoveDecodingException {
        int sp = Chess960.getStartPositionNo("BNRKRBNQ");
        GameMovesModel moves = new GameMovesModel(Chess960.getStartPosition(sp), 1);
        moves.root().addMove(B2, B3).addMove(B7, B6).addMove(E2, E4).addMove(E7, E5)
                .addMove(G1, F3).addMove(F7, F6).addMove(F1, C4).addMove(F8, C5)
                .addMove(H1, F1).addMove(G8, E7).addMove(ShortMove.longCastles())
                .addMove(B8, C6);

        ByteBuffer buf = MovesSerializer.serializeMoves(moves);
        GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
        assertEquals(moves.toString(), outputMoves.toString());
    }

    @Test
    public void serializeDeserializeGameWithNullMove() throws ChessBaseMoveDecodingException {
        GameMovesModel moves = new GameMovesModel();
        moves.root().addMove(E2, E4)
                .addMove(Move.nullMove(moves.root().mainNode().position()))
                .addMove(D2, D4)
                .addMove(ShortMove.nullMove());

        for (int encodingMode = 0; encodingMode < 8; encodingMode++) {
            ByteBuffer buf = MovesSerializer.serializeMoves(moves, encodingMode);
            GameMovesModel outputMoves = MovesSerializer.deserializeMoves(buf);
            assertEquals(moves.toString(), outputMoves.toString());
        }
    }
}
