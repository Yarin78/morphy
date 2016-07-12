package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import se.yarin.cbhlib.entities.Entity;

public class AnnotatorEntity implements Entity, Comparable<AnnotatorEntity> {
    @Getter
    private int id;

    @Getter @Setter
    @NonNull
    private String name;

    @Getter @Setter
    private int count;

    @Getter @Setter
    private int firstGameId;

    AnnotatorEntity(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    public AnnotatorEntity(@NonNull String name) {
        this(-1, name);
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
