package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.chess.Date;

public class SourceEntity implements Entity, Comparable<SourceEntity> {
    @Getter
    private int id;

    @Getter @Setter
    @NonNull
    private String title;

    @Getter @Setter
    @NonNull
    private String publisher = "";

    @Getter @Setter
    @NonNull
    private Date publication = Date.today();

    @Getter @Setter
    @NonNull
    private Date date = Date.today();

    @Getter @Setter
    private int version;

    @Getter @Setter
    @NonNull
    private SourceQuality quality = SourceQuality.UNSET;

    @Getter @Setter
    private int count;

    @Getter @Setter
    private int firstGameId;

    SourceEntity(int id, @NonNull String title) {
        this.id = id;
        this.title = title;
    }

    public SourceEntity(@NonNull String title) {
        this(-1, title);
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(SourceEntity o) {
        return title.compareTo(o.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceEntity that = (SourceEntity) o;

        return title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }
}
