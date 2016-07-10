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
    private int noGames;
    @Getter @Setter
    private int firstGameId;

    PlayerEntity(int id, @NonNull String lastName, @NonNull String firstName, int noGames, int firstGameId) {
        this.id = id;
        this.lastName = lastName;
        this.firstName = firstName;
        this.noGames = noGames;
        this.firstGameId = firstGameId;
    }

    public PlayerEntity(@NonNull String lastName, @NonNull String firstName, int noGames, int firstGameId) {
        this.id = -1;
        this.lastName = lastName;
        this.firstName = firstName;
        this.noGames = noGames;
        this.firstGameId = firstGameId;
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
}
