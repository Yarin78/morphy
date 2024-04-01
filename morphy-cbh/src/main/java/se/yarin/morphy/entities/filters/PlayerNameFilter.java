package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class PlayerNameFilter implements EntityIndexFilter<Player> {
    private final @NotNull String lastName;
    private final @NotNull String firstName;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public @NotNull String lastName() {
        return lastName;
    }

    public @NotNull String firstName() {
        return firstName;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isExactMatch() {
        return exactMatch;
    }

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
        this.lastName = caseSensitive ? lastName : lastName.toLowerCase();
        this.firstName = caseSensitive ? firstName : firstName.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String playerName, @NotNull String searchName) {
        if (exactMatch) {
            return caseSensitive ? playerName.equals(searchName) : playerName.equalsIgnoreCase(searchName);
        }
        return caseSensitive ? playerName.startsWith(searchName) : playerName.toLowerCase().startsWith(searchName);
    }

    @Override
    public boolean matches(@NotNull Player player) {
        return matches(player.lastName(), lastName) && matches(player.firstName(), firstName);
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        // Only partial matching done here; deserializing players is not that much slower
        ByteBuffer buf = ByteBuffer.wrap(serializedItem);
        String playerName = ByteBufferUtil.getFixedSizeByteString(buf, 30);
        return matches(playerName, lastName);
    }

    @Override
    public String toString() {
        String lastNameStr = caseSensitive ? "lastName" : "lower(lastName)";
        String firstNameStr = caseSensitive ? "firstName" : "lower(firstName)";

        if (exactMatch) {
            return "%s='%s' and %s='%s'".formatted(lastNameStr, lastName, firstNameStr, firstName);
        } else if (firstName.length() == 0) {
            return "%s like '%s%%'".formatted(lastNameStr, lastName);
        } else {
            return "%s like '%s%%' and %s like '%s%%'".formatted(lastNameStr, lastName, firstNameStr, firstName);
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.PLAYER;
    }

    @Override
    public @Nullable Player start() {
        return caseSensitive ? Player.of(lastName(), "") : null;
    }

    @Override
    public @Nullable Player end() {
        return caseSensitive ? Player.of(lastName() + "zzz", "") : null;
    }
}
