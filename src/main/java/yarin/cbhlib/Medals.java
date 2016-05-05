package yarin.cbhlib;

import java.util.EnumSet;

public class Medals {

    public enum Medal {
        BestGame,
        DecidedTournament,
        ModelGame,
        Novelty,
        PawnStructure,
        Strategy,
        Tactics,
        WithAttack,
        Sacrifice,
        Defense,
        Material,
        PiecePlay,
        Endgame,
        TacticalBlunder,
        StrategicalBlunder,
        User
    }

    private EnumSet<Medal> medals;

    public static Medals decode(int value) {
        Medals medals = new Medals();
        for (Medal medal : Medal.values()) {
            if (((1<<medal.ordinal()) & value) > 0) {
                medals.add(medal);
            }
        }
        return medals;
    }

    public int encode() {
        int value = 0;
        for (Medal medal : medals) {
            value |= medal.ordinal();
        }
        return value;
    }

    public int size() {
        return medals.size();
    }

    public Medals() {
        medals = EnumSet.noneOf(Medal.class);
    }

    public Medals(EnumSet<Medal> medals) {
        this.medals = medals;
    }

    public void add(Medal medal) {
        medals.add(medal);
    }

    public boolean contains(Medal medal) {
        return medals.contains(medal);
    }
}
