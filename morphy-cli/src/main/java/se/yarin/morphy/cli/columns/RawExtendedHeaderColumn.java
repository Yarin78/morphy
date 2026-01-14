package se.yarin.morphy.cli.columns;

import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.Game;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class RawExtendedHeaderColumn implements GameColumn {
    private final int start;
    private final int length;

    @Override
    public String getHeader() {
        if (length == 1) {
            return String.format("CBJ %d", start);
        }
        return String.format("CBJ %d,%d", start, length);
    }

    public RawExtendedHeaderColumn(int start, int length) {
        if (start < 0 || start+length > 120) {
            throw new IllegalArgumentException("Extended header length is 120 bytes");
        }
        this.start = start;
        this.length = length;
    }

    @Override
    public String getValue(Game game) {
        ByteBuffer buf = game.database().extendedGameHeaderStorage().getRaw(game.id());
        if (start >= buf.limit()) {
            return "";
        }
        buf = buf.slice(start, Math.min(length, buf.limit() - start));
        return CBUtil.toHexString(buf);
    }

    @Override
    public int marginLeft() {
        return 2;
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public int width() {
        return this.length == 1 ? 7 : Math.max(10, this.length * 3);
    }

    @Override
    public String getId() {
        return "raw";
    }
}
