package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class ResultsFilter extends GameStorageFilter {
    private @NotNull GameResult result;

    public ResultsFilter(@NotNull String results) {
        switch (results.toLowerCase()) {
            case "1-0" :
            case "white":
                result = GameResult.WHITE_WINS;
                break;
            case "i-o":
                result = GameResult.WHITE_WINS_ON_FORFEIT;
                break;
            case "0-1":
            case "black":
                result = GameResult.BLACK_WINS;
            case "o-i":
                result = GameResult.BLACK_WINS_ON_FORFEIT;
                break;
            case "draw":
                result = GameResult.DRAW;
                break;
            case "0-0":
            case "o-o":
                result = GameResult.BOTH_LOST;
                break;
            default:
                throw new IllegalArgumentException("Invalid result: " + results);
        }
    }

    @Override
    public boolean matches(@NotNull GameHeader header) {
        return super.matches(header) && result.equals(header.result());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return super.matchesSerialized(buf) &&
                result.equals(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf, 27)));
    }
}
