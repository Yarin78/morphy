package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

public interface TournamentIndexTransaction {
    @NotNull TournamentExtra getExtra(int id);
}
