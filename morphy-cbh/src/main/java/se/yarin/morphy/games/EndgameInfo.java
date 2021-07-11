package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents different types of Endgame that a game contained at various stages.
 */
@Value.Immutable
public abstract class EndgameInfo {
    private static final Logger log = LoggerFactory.getLogger(EndgameInfo.class);

    /**
     * Gets the endgame type that occurred for the longest time
     * @return a type of an endgame, or EndgameType.NONE if no endgame found
     */
    @Value.Default
    public @NotNull EndgameType longestType() {
        return EndgameType.NONE;
    }

    /**
     * Gets a list of the different endgame types and at what ply they start at
     * @return a list of endgames types with associated ply's
     */
    @Value.Default
    public @NotNull List<PlyType> plyTypes() {
        return List.of();
    }

    @Value.Immutable
    public interface PlyType {
        @Value.Parameter
        EndgameType type();

        @Value.Parameter
        int ply();
    }

    public static EndgameInfo empty() {
        return ImmutableEndgameInfo.builder().build();
    }

    public void serialize(ByteBuffer buf) {
        ByteBufferUtil.putShortB(buf, 1);
        ByteBufferUtil.putShortB(buf, EndgameType.encode(longestType()));
        for (int i = 0; i < 4; i++) {
            if (i < plyTypes().size()) {
                ByteBufferUtil.putShortB(buf, EndgameType.encode(plyTypes().get(i).type()));
                ByteBufferUtil.putShortB(buf, plyTypes().get(i).ply());
            } else {
                ByteBufferUtil.putShortB(buf, 0);
                ByteBufferUtil.putShortB(buf, 0);
            }
        }
    }

    /**
     * Deserializes endgame information
     * @return an instance of {@link EndgameInfo}
     */
    public static @NotNull EndgameInfo deserialize(ByteBuffer buf) {
        try {
            int version = ByteBufferUtil.getUnsignedShortB(buf);
            if (version != 1) {
                return EndgameInfo.empty();
            }
            EndgameType longestType = EndgameType.decode(ByteBufferUtil.getUnsignedShortB(buf));
            List<PlyType> plyTypes = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                plyTypes.add(ImmutablePlyType.of(EndgameType.decode(ByteBufferUtil.getUnsignedShortB(buf)), ByteBufferUtil.getUnsignedShortB(buf)));
            }
            return ImmutableEndgameInfo.builder().longestType(longestType).plyTypes(plyTypes).build();
        } catch (BufferUnderflowException e) {
            log.warn("Unexpected end of buffer", e);
            return EndgameInfo.empty();
        }
    }
}
