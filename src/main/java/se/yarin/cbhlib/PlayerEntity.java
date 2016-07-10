package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class PlayerEntity implements Entity, Comparable<PlayerEntity> {

    @Getter
    private int id;

    @Getter @Setter @NonNull
    private String lastName;

    @Getter @Setter @NonNull
    private String firstName;

    @Getter @Setter
    private int count;

    @Getter @Setter
    private int firstGameId;

    PlayerEntity(int id, @NonNull String lastName, @NonNull String firstName) {
        this.id = id;
        this.lastName = lastName;
        this.firstName = firstName;
    }

    public PlayerEntity(@NonNull String lastName, @NonNull String firstName) {
        this(-1, lastName, firstName);
    }

    public String getFullName() {
        if (lastName.length() == 0) {
            return firstName;
        }
        if (firstName.length() == 0) {
            return lastName;
        }
        return lastName + ", " + firstName;
    }

    @Override
    public String toString() {
        return getFullName();
    }

    @Override
    public int compareTo(PlayerEntity o) {
        int comp = lastName.compareTo(o.lastName);
        if (comp != 0) return comp;
        return firstName.compareTo(o.firstName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlayerEntity that = (PlayerEntity) o;

        if (!lastName.equals(that.lastName)) return false;
        return firstName.equals(that.firstName);

    }

    @Override
    public int hashCode() {
        int result = lastName.hashCode();
        result = 31 * result + firstName.hashCode();
        return result;
    }
}
