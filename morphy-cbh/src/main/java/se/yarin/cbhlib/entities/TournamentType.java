package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;

public enum TournamentType {
    NONE("none"),
    SINGLE_GAME("game"),
    MATCH("match"),
    ROUND_ROBIN("tourn"),
    SWISS_SYSTEM("swiss"),
    TEAM("team"),
    KNOCK_OUT("k.o."),
    SIMUL("simul"),
    SCHEVENINGEN_SYSTEM("schev");

    @Getter
    private String name;

    @NonNull
    public static TournamentType fromName(String name) {
        for (TournamentType tt : values()) {
            if (tt.getName().equals(name)) {
                return tt;
            }
        }
        return TournamentType.NONE;
    }

    TournamentType(String name) {
        this.name = name;
    }
}
