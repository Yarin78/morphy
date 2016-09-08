package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;

public enum TournamentTimeControl {
    NORMAL("normal"),
    BLITZ("blitz"),
    RAPID("rapid"),
    CORRESPONDENCE("corr");

    @Getter
    private String name;

    @NonNull
    public static TournamentTimeControl fromName(String name) {
        for (TournamentTimeControl ttc : values()) {
            if (ttc.getName().equals(name)) {
                return ttc;
            }
        }
        return TournamentTimeControl.NORMAL;
    }

    TournamentTimeControl(String name) {
        this.name = name;
    }
}
