package se.yarin.cbhlib;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@AllArgsConstructor
public class PlayerEntity {
    @Getter
    private final int id;

    @Getter @Setter @NonNull
    private String lastName;
    @Getter @Setter @NonNull
    private String firstName;
    @Getter @Setter
    private int noGames;
    @Getter @Setter
    private int firstGameId;

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
}
