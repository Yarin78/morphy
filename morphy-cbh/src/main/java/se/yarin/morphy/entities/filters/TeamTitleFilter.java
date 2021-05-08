package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TeamTitleFilter implements ItemStorageFilter<Team> {
    private final @NotNull String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TeamTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.title = title;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String teamTitle) {
        if (exactMatch) {
            return caseSensitive ? teamTitle.equals(title) : teamTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? teamTitle.startsWith(title) : teamTitle.toLowerCase().startsWith(title.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull Team team) {
        return matches(team.title());
    }

    // TODO: matchesSerialized (or not, can we compare serialized strings safely?)
}
