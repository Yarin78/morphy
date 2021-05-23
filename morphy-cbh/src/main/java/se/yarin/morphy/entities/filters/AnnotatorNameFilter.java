package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Annotator;

public class AnnotatorNameFilter implements EntityFilter<Annotator> {
    private final @NotNull String name;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public AnnotatorNameFilter(@NotNull String name, boolean caseSensitive, boolean exactMatch) {
        this.name = name;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String annotatorName) {
        if (exactMatch) {
            return caseSensitive ? annotatorName.equals(name) : annotatorName.equalsIgnoreCase(name);
        }
        return caseSensitive ? annotatorName.startsWith(name) : annotatorName.toLowerCase().startsWith(name.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull Annotator annotator) {
        return matches(annotator.name());
    }
}
