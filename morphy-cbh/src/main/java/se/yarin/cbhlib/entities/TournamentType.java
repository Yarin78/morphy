package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;

public enum TournamentType {
    NONE("none", ""),
    SINGLE_GAME("game", "Game"),
    MATCH("match", "Match"),
    ROUND_ROBIN("tourn", "Tournament"),
    SWISS_SYSTEM("swiss", "Open"),
    TEAM("team", "Team"),
    KNOCK_OUT("k.o.", "Knockout"),
    SIMUL("simul", "Simul"),
    SCHEVENINGEN_SYSTEM("schev", "Schevening");

    @Getter
    private final String name;

    @Getter
    private final String longName;

    @NonNull
    public static TournamentType fromName(String name) {
        for (TournamentType tt : values()) {
            if (tt.getName().equals(name)) {
                return tt;
            }
        }
        return TournamentType.NONE;
    }

    TournamentType(String name, String longName) {
        this.name = name;
        this.longName = longName;
    }
}
