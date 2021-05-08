package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.util.CBUtil;

import java.util.Arrays;

public class RawHeaderColumn implements GameColumn {
    private final int start;
    private final int length;

    @Override
    public String getHeader() {
        if (length == 1) {
            return String.format("CBH %d", start);
        }
        return String.format("CBH %d,%d", start, length);
    }

    public RawHeaderColumn(int start, int length) {
        if (start < 0 || start+length > 46) {
            throw new IllegalArgumentException("Header length is 46 bytes");
        }
        this.start = start;
        this.length = length;
    }

    @Override
    public String getValue(Game game) {
        byte[] raw = game.getDatabase().getHeaderBase().getRaw(game.getId());
        byte[] dest = Arrays.copyOfRange(raw, start, Math.min(raw.length, start+length));
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
        return this.length == 1 ? 6 : Math.max(9, this.length * 3);
    }

    @Override
    public boolean trimValueToWidth() { return true; }

    @Override
    public String getId() {
        return "raw";
    }

}
