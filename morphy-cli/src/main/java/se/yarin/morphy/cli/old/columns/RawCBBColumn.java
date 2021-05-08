package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.util.CBUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class RawCBBColumn implements GameColumn {
    private final int start;
    private final int length;

    public RawCBBColumn(int start, int length) {
        if (start < 0 || start+length > 52) {
            throw new IllegalArgumentException("CBB record length is 52 bytes");
        }

        this.start = start;
        this.length = length;
    }

    @Override
    public String getHeader() {
        return "CBB";
    }

    @Override
    public String getValue(Game game) {
        try {
            FileChannel cbbChannel = null; // TODO
            cbbChannel.position(game.getId() * 52);
            ByteBuffer buf = ByteBuffer.allocate(52);
            cbbChannel.read(buf);
            String s = "";
            for (int i = 0; i < length; i+=8) {
                byte[] dest = Arrays.copyOfRange(buf.array(), start+i,start + Math.min(i + 8, length));
                s += CBUtil.toHexString(dest) + " ";
            }
            return s;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from cbb file");
        }
    }
    @Override
    public int width() {
        return this.length == 1 ? 7 : Math.max(10, this.length * 3 + this.length/8);
    }

    @Override
    public String getId() {
        return "raw-cbb";
    }
}
