package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.storage.ItemStorageFilter;

public class PlayerNameFilter implements ItemStorageFilter<Player> {
    private final @NotNull String lastName;
    private final @NotNull String firstName;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    private static String resolveFirstName(String name) {
        if (name.contains(",")) {
            return name.substring(name.indexOf(",") + 1).strip();
        }
        if (name.contains(" ")) {
            return name.substring(0, name.indexOf(" ")).strip();
        }
        return "";
    }

    private static String resolveLastName(String name) {
        if (name.contains(",")) {
            return name.substring(0, name.indexOf(",")).strip();
        }
        if (name.contains(" ")) {
            return name.substring(name.indexOf(" ") + 1).strip();
        }
        return name;
    }

    public PlayerNameFilter(@NotNull String name, boolean caseSensitive, boolean exactMatch) {
        this(resolveLastName(name), resolveFirstName(name), caseSensitive, exactMatch);
    }

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
