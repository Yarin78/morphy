package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

public enum TournamentTimeControl {
  NORMAL("normal", "Normal"),
  BLITZ("blitz", "Blitz"),
  RAPID("rapid", "Rapid"),
  CORRESPONDENCE("corr", "Correspondence");

  private final String name;

  private final String longName;

  public String getName() {
    return name;
  }

  public String getLongName() {
    return longName;
  }

  @NotNull
  public static TournamentTimeControl fromName(String name) {
    for (TournamentTimeControl ttc : values()) {
      if (ttc.name.equals(name)) {
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
