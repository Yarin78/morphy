package se.yarin.cbhlib;

import lombok.*;
import se.yarin.cbhlib.entities.Entity;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TeamEntity implements Entity, Comparable<TeamEntity> {
    @Getter
    private int id;

    @Getter @Setter
    @NonNull
    private String title;

    @Getter
    private int teamNumber;

    @Getter
    private boolean season;

    @Getter
    private int year;

    @Getter
    @NonNull
    private Nation nation = Nation.NONE;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public TeamEntity(@NonNull String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(TeamEntity o) {
        return title.compareTo(o.title);
    }

    @Override
    public TeamEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeamEntity that = (TeamEntity) o;

        return title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }
}
