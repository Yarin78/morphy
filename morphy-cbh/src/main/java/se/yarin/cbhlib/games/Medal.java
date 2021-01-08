package se.yarin.cbhlib.games;

import java.util.EnumSet;

public enum Medal {
    BEST_GAME,
    DECIDED_TOURNAMENT,
    MODEL_GAME,
    NOVELTY,
    PAWN_STRUCTURE,
    STRATEGY,
    TACTICS,
    WITH_ATTACK,
    SACRIFICE,
    DEFENSE,
    MATERIAL,
    PIECE_PLAY,
    ENDGAME,
    TACTICAL_BLUNDER,
    STRATEGICAL_BLUNDER,
    USER;

    public static EnumSet<Medal> decode(int data) {
        EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
        if (data != 0) {
            for (Medal medal : Medal.values()) {
                if (((1 << medal.ordinal()) & data) > 0) {
                    medals.add(medal);
                }
            }
        }
        return medals;
    }

    public static int encode(EnumSet<Medal> medals) {
        int value = 0;
        for (Medal medal : medals) {
            value += (1 << medal.ordinal());
        }
        return value;
    }
}
