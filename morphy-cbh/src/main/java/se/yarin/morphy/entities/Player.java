package se.yarin.morphy.entities;

import lombok.NonNull;
import org.immutables.value.Value;
import se.yarin.cbhlib.util.CBUtil;

@Value.Immutable
public abstract class Player extends Entity implements Comparable<Player> {
    abstract String lastName();

    abstract String firstName();

    public static Player ofFullName(String fullName) {
        String firstName = "", lastName = fullName.trim();
        int comma = lastName.indexOf(",");
        if (comma > 0) {
            firstName = lastName.substring(comma + 1).trim();
            lastName = lastName.substring(0, comma).trim();
        }

        return ImmutablePlayer.builder().lastName(lastName).firstName(firstName).build();
    }

    public static Player of(String lastName, String firstName) {
        return ImmutablePlayer.builder().lastName(lastName).firstName(firstName).build();
    }

    public String getFullName() {
        if (lastName().length() == 0) {
            return firstName();
        }
        if (firstName().length() == 0) {
            return lastName();
        }
        return lastName() + ", " + firstName();
    }

    public String getFullNameShort() {
        if (lastName().length() == 0) {
            return firstName();
        }
        if (firstName().length() == 0) {
            return lastName();
        }
        return lastName() + ", " + firstName().charAt(0);
    }

    public String toString() {
        return getFullName();
    }

    public int compareTo(Player o) {
        int comp = CBUtil.compareStringUnsigned(lastName(), o.lastName());
        if (comp != 0) return comp;
        return CBUtil.compareStringUnsigned(firstName(), o.firstName());
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Player that = (Player) o;

        if (!lastName().equals(that.lastName())) return false;
        return firstName().equals(that.firstName());
    }

}
