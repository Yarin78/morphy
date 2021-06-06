package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Source;

public class SourceTitleFilter implements EntityFilter<Source>  {
    private final @NotNull String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public SourceTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.title = caseSensitive ? title : title.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String sourceTitle) {
        if (exactMatch) {
            return caseSensitive ? sourceTitle.equals(title) : sourceTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? sourceTitle.startsWith(title) : sourceTitle.toLowerCase().startsWith(title);
    }

    @Override
    public boolean matches(@NotNull Source source) {
        return matches(source.title());
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
