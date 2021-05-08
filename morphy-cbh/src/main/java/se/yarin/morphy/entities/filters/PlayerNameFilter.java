package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.storage.ItemStorageFilter;

public class PlayerNameFilter implements ItemStorageFilter<Player> {
    private final @NotNull String lastName;
    private final @NotNull String firstName;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public PlayerNameFilter(@NotNull String lastName, @NotNull String firstName, boolean caseSensitive, boolean exactMatch) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String playerName, @NotNull String searchName) {
        if (exactMatch) {
            return caseSensitive ? playerName.equals(searchName) : playerName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? playerName.startsWith(searchName) : playerName.toLowerCase().startsWith(searchName.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull Player player) {
        return matches(player.lastName(), lastName) && matches(player.firstName(), firstName);
    }

    // TODO: matchesSerialized (or not, can we compare serialized strings safely?)
}
