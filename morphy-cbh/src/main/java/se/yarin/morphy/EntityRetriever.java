package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.*;

public interface EntityRetriever {
    @NotNull Player getPlayer(int id);

    @NotNull Annotator getAnnotator(int id);

    @NotNull Source getSource(int id);

    @NotNull Tournament getTournament(int id);

    @NotNull TournamentExtra getTournamentExtra(int id);

    @NotNull Team getTeam(int id);

    @NotNull GameTag getGameTag(int id);
}
