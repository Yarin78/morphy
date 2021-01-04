package se.yarin.cbhlib;

import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Represents different types of Endgame that a game contained at various stages.
 *
 */
public class EndgameInfo {
    private static final Logger log = LoggerFactory.getLogger(EndgameInfo.class);

    private final @NonNull EndgameType longestType;
    private final @NonNull PlyType[] plyTypes;

    @Data
    public class PlyType {
        private final EndgameType type;
        private final int ply;
    }

    public EndgameInfo() {
        this(EndgameType.NONE, new EndgameType[0], new int[0]);
    }

    EndgameInfo(@NonNull EndgameType longestType, @NonNull EndgameType[] types, @NonNull int[] startAtPly) {
        this.longestType = longestType;
        this.plyTypes = new PlyType[types.length];
        for (int i = 0; i < types.length; i++) {
            this.plyTypes[i] = new PlyType(types[i], startAtPly[i]);
        }
    }

    /**
     * Gets the endgame type that occurred for the longest time
     * @return a type of an endgame, or EndgameType.NONE if no endgame found
     */
    public EndgameType getLongestType() {
        return longestType;
    }

    /**
     * Gets a list of the different endgame types and at what ply they start at
     * @return a list of endgames types with associated ply's
     */
    public List<PlyType> getEndgameTypes() {
        return Arrays.asList(plyTypes);
    }

    public void serialize(ByteBuffer buf) {
        ByteBufferUtil.putShortB(buf, 1);
        ByteBufferUtil.putShortB(buf, CBUtil.encodeEndgameType(longestType));
        for (int i = 0; i < 4; i++) {
            if (i < plyTypes.length) {
                ByteBufferUtil.putShortB(buf, CBUtil.encodeEndgameType(plyTypes[i].type));
                ByteBufferUtil.putShortB(buf, plyTypes[i].getPly());
            } else {
                ByteBufferUtil.putShortB(buf, 0);
                ByteBufferUtil.putShortB(buf, 0);
            }
        }
    }

    /**
     * Deserializes endgame information
     * @return an instance of {@link EndgameInfo}, or null if not set
     */
    public static EndgameInfo deserialize(ByteBuffer buf) {
        try {
            int version = ByteBufferUtil.getUnsignedShortB(buf);
            if (version != 1) {
                return null;
            }
            EndgameType longestType = CBUtil.decodeEndgameType(ByteBufferUtil.getUnsignedShortB(buf));
            int[] ply = new int[4];
            EndgameType[] types = new EndgameType[4];
            for (int i = 0; i < 4; i++) {
                types[i] = CBUtil.decodeEndgameType(ByteBufferUtil.getUnsignedShortB(buf));
                ply[i] = ByteBufferUtil.getUnsignedShortB(buf);
            }
            return new EndgameInfo(longestType, types, ply);
        } catch (BufferUnderflowException e) {
            log.warn("Unexpected end of buffer", e);
            return new EndgameInfo();
        }
    }


}
