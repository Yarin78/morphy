package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.util.CBUtil;

import java.util.Arrays;

public class RawExtendedHeaderColumn implements GameColumn {
    private final int start;
    private final int length;

    @Override
    public String getHeader() {
        if (length == 1) {
            return String.format("CBH %d", start);
        }
        return String.format("CBH %d,%d", start, length);
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
        byte[] raw = game.getDatabase().getExtendedHeaderBase().getRaw(game.getId());
        byte[] dest = Arrays.copyOfRange(raw, start, start+length);
        return CBUtil.toHexString(dest);
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
