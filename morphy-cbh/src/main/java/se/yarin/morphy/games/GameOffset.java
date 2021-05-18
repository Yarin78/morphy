package se.yarin.morphy.games;

import org.immutables.value.Value;

@Value.Immutable
public interface GameOffset {
    int gameId();
    int moveOffset();

    static GameOffset of(int gameId, int moveOffset) {
        return ImmutableGameOffset.builder().gameId(gameId).moveOffset(moveOffset).build();
    }
}

