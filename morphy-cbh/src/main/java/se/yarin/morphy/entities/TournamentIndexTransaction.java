package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

public interface TournamentIndexTransaction {
  @NotNull
  Tournament get(int id);

  @NotNull
  TournamentExtra getExtra(int id);

  void close();
}
