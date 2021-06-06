package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Team;

public class TeamTitleFilter implements EntityFilter<Team> {
    private final @NotNull String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TeamTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.title = caseSensitive ? title : title.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String teamTitle) {
        if (exactMatch) {
            return caseSensitive ? teamTitle.equals(title) : teamTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? teamTitle.startsWith(title) : teamTitle.toLowerCase().startsWith(title);
    }

    @Override
    public boolean matches(@NotNull Team team) {
        return matches(team.title());
    }

    @Override
    public String toString() {
        String titleStr = caseSensitive ? "title" : "lower(title)";

        if (exactMatch) {
            return "%s='%s'".formatted(titleStr, title);
        } else {
            return "%s like '%s%%'".formatted(titleStr, title);
        }
    }
}
