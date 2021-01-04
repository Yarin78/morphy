package se.yarin.cbhlib;

import lombok.*;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SourceEntity implements Entity, Comparable<SourceEntity> {
    @Getter
    private int id;

    @Getter
    @NonNull
    private String title;

    @Getter
    @NonNull
    @Builder.Default
    private String publisher = "";

    @Getter
    @NonNull
    @Builder.Default
    private Date publication = Date.today();

    @Getter
    @NonNull
    @Builder.Default
    private Date date = Date.today();

    @Getter
    private int version;

    @Getter
    @NonNull
    @Builder.Default
    private SourceQuality quality = SourceQuality.UNSET;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public SourceEntity(@NonNull String title) {
        this.title = title;
        this.publisher = "";
        this.publication = Date.today();
        this.date = Date.today();
        this.quality = SourceQuality.UNSET;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(SourceEntity o) {
        return CBUtil.compareString(title, o.title);
    }

    @Override
    public SourceEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public SourceEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
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
