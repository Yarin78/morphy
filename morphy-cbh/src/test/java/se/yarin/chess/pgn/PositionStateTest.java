package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.Position;

import static org.junit.Assert.*;

public class PositionStateTest {

    @Test
    public void testParseStartPosition() throws PgnFormatException {
        PositionState state = PositionState.fromFen(PositionState.START_POSITION_FEN);

        assertNotNull(state);
        assertEquals(0, state.halfMoveClock());
        assertEquals(1, state.fullMoveNumber());
        assertEquals(Position.start(), state.position());
    }

    @Test
    public void testGenerateStartPosition() {
        String fen = PositionState.toFen(Position.start(), 0);
        assertEquals(PositionState.START_POSITION_FEN, fen);
    }

    @Test
    public void testRoundTrip() throws PgnFormatException {
        String originalFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2";
        PositionState state = PositionState.fromFen(originalFen);
        String generatedFen = PositionState.toFen(state.position(), state.fullMoveNumber() * 2 - 2, state.halfMoveClock());

        assertEquals(originalFen, generatedFen);
    }

    @Test
    public void testInstanceToFen() throws PgnFormatException {
        String originalFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2";
        PositionState state = PositionState.fromFen(originalFen);
        String generatedFen = state.toFen();

        assertEquals(originalFen, generatedFen);
    }

    @Test(expected = PgnFormatException.class)
    public void testInvalidFen() throws PgnFormatException {
        PositionState.fromFen("invalid fen string");
    }

    @Test
    public void testPartialFen() throws PgnFormatException {
        // FEN without halfmove clock and fullmove number
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -";
        PositionState state = PositionState.fromFen(fen);

        assertEquals(0, state.halfMoveClock());
        assertEquals(1, state.fullMoveNumber());
    }
}
