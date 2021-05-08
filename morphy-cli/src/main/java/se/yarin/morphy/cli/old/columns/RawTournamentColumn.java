package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.util.CBUtil;

import java.util.Arrays;

public class RawTournamentColumn implements TournamentColumn {
    private final int start;
    private final int length;

    @Override
    public String getHeader() {
        if (length == 1) {
            return String.format("CBT %d", start);
        }
        return String.format("CBT %d,%d", start, length);
    }

    public RawTournamentColumn(int start, int length) {
        if (start < 0 || start+length > 90) {
            throw new IllegalArgumentException("Tournament entity length is 90 bytes");
        }
        this.start = start;
        this.length = length;
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        byte[] raw = db.getTournamentBase().getRaw(tournament.getId());
        byte[] dest = Arrays.copyOfRange(raw, start, Math.min(raw.length, start+length));
        return CBUtil.toHexString(dest);
    }

    @Override
    public String getTournamentId() {
        return "raw";
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
}
