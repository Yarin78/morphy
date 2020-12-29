package se.yarin.cbhlib;

import lombok.*;
import se.yarin.cbhlib.entities.Entity;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnnotatorEntity implements Entity, Comparable<AnnotatorEntity> {
    @Getter
    private int id;

    @Getter
    @NonNull
    private String name;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public AnnotatorEntity(@NonNull String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(AnnotatorEntity o) {
        return name.compareTo(o.name);
    }

    @Override
    public AnnotatorEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public AnnotatorEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnotatorEntity that = (AnnotatorEntity) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
