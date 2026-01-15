package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

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

  private final String name;

  private final String longName;

  public String getName() {
    return name;
  }

  public String getLongName() {
    return longName;
  }

  @NotNull
  public static TournamentType fromName(String name) {
    for (TournamentType tt : values()) {
      if (tt.name.equals(name)) {
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
