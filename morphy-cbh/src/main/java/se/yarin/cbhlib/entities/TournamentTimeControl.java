package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;

public enum TournamentTimeControl {
    NORMAL("normal", "Normal"),
    BLITZ("blitz", "Blitz"),
    RAPID("rapid", "Rapid"),
    CORRESPONDENCE("corr", "Correspondence");

    @Getter
    private final String name;

    @Getter
    private final String longName;

    @NonNull
    public static TournamentTimeControl fromName(String name) {
        for (TournamentTimeControl ttc : values()) {
            if (ttc.getName().equals(name)) {
                return ttc;
            }
        }
        return TournamentTimeControl.NORMAL;
    }

    TournamentTimeControl(String name, String longName) {
        this.name = name;
        this.longName = longName;
    }
}
