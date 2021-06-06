package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.GameTag;

public class GameTagTitleFilter implements EntityFilter<GameTag> {
    private final @NotNull String englishTitle;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public GameTagTitleFilter(@NotNull String englishTitle, boolean caseSensitive, boolean exactMatch) {
        this.englishTitle = caseSensitive ? englishTitle : englishTitle.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String gameTagEnglishTitle) {
        if (exactMatch) {
            return caseSensitive ? gameTagEnglishTitle.equals(englishTitle) : gameTagEnglishTitle.equalsIgnoreCase(englishTitle);
        }
        return caseSensitive ? gameTagEnglishTitle.startsWith(englishTitle) : gameTagEnglishTitle.toLowerCase().startsWith(englishTitle);
    }

    @Override
    public boolean matches(@NotNull GameTag gameTag) {
        return matches(gameTag.englishTitle());
    }

    @Override
    public String toString() {
        String titleStr = caseSensitive ? "englishTitle" : "lower(englishTitle)";

        if (exactMatch) {
            return "%s='%s'".formatted(titleStr, englishTitle);
        } else {
            return "%s like '%s%%'".formatted(titleStr, englishTitle);
        }
    }
}
