package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class Endgame {
    private static final Logger log = LoggerFactory.getLogger(Endgame.class);

    private @NonNull EndgameType longestType;
    private @NonNull EndgameType[] types; // TODO: Make immutable
    private int[] startAtPly;

    public Endgame() {
        this(EndgameType.TODO_00, new EndgameType[0], new int[0]);
    }

    public Endgame(EndgameType longestType, EndgameType[] types, int[] startAtPly) {
        this.longestType = longestType;
        this.types = types;
        this.startAtPly = startAtPly;
    }

    public boolean isSet() {
        return longestType != EndgameType.TODO_00;
    }

    public EndgameType getLongestType() {
        return longestType;
    }

    public void serialize(ByteBuffer buf) {
        // TODO: Implement this
        for (int i = 0; i < 20; i++) {
            buf.put((byte) 0);
        }
    }

    public static Endgame deserialize(ByteBuffer buf) {
        try {
            int version = ByteBufferUtil.getUnsignedShortB(buf);
            if (version != 1) {
                return new Endgame();
            }
            EndgameType longestType = CBUtil.decodeEndgameType(ByteBufferUtil.getUnsignedShortB(buf));
            int[] ply = new int[4];
            EndgameType[] types = new EndgameType[4];
            for (int i = 0; i < 4; i++) {
                types[i] = CBUtil.decodeEndgameType(ByteBufferUtil.getUnsignedShortB(buf));
                ply[i] = ByteBufferUtil.getUnsignedShortB(buf);
            }
            return new Endgame(longestType, types, ply);
        } catch (BufferUnderflowException e) {
            log.warn("Unexpected end of buffer", e);
            return new Endgame();
        }
    }


}
