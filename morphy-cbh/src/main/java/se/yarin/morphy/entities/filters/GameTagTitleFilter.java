package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.storage.ItemStorageFilter;

public class GameTagTitleFilter implements ItemStorageFilter<GameTag> {
    private final @NotNull String englishTitle;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public GameTagTitleFilter(@NotNull String englishTitle, boolean caseSensitive, boolean exactMatch) {
        this.englishTitle = englishTitle;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String gameTagEnglishTitle) {
        if (exactMatch) {
            return caseSensitive ? gameTagEnglishTitle.equals(englishTitle) : gameTagEnglishTitle.equalsIgnoreCase(englishTitle);
        }
        return caseSensitive ? gameTagEnglishTitle.startsWith(englishTitle) : gameTagEnglishTitle.toLowerCase().startsWith(englishTitle.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull GameTag gameTag) {
        return matches(gameTag.englishTitle());
    }
}
