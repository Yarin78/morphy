package se.yarin.cbhlib.entities;

import lombok.*;
import se.yarin.cbhlib.util.CBUtil;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerEntity implements Entity, Comparable<PlayerEntity> {

    @Getter
    private int id;

    @Getter private byte[] raw;  // For debugging purposes

    @Getter @NonNull
    private String lastName;

    @Getter @NonNull
    private String firstName;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public PlayerEntity(@NonNull String lastName, @NonNull String firstName) {
        this.lastName = lastName;
        this.firstName = firstName;
    }

    public static PlayerEntity fromFullName(@NonNull String fullName) {
        String firstName = "", lastName = fullName.trim();
        int comma = lastName.indexOf(",");
        if (comma > 0) {
            firstName = lastName.substring(comma + 1).trim();
            lastName = lastName.substring(0, comma).trim();
        }

        return new PlayerEntity(lastName, firstName);
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
        int comp = CBUtil.compareStringUnsigned(lastName, o.lastName);
        if (comp != 0) return comp;
        return CBUtil.compareStringUnsigned(firstName, o.firstName);
    }

    @Override
    public PlayerEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public PlayerEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
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
