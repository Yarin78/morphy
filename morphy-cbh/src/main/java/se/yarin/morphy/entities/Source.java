package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.chess.Date;
import se.yarin.morphy.util.CBUtil;

@Value.Immutable
public abstract class Source extends Entity implements Comparable<Source> {
    @Value.Default
    public String title() { return ""; }

    @Value.Default
    public String publisher() {
        return "";
    }

    @Value.Default
    public Date publication() {
        return Date.today();
    }

    @Value.Default
    public Date date() {
        return Date.today();
    }

    @Value.Default
    public int version() { return 0; }

    @Value.Default
    public SourceQuality quality() {
        return SourceQuality.UNSET;
    }

    @Override
    public Entity withCountAndFirstGameId(int count, int firstGameId) {
        return ImmutableSource.builder().from(this).count(count).firstGameId(firstGameId).build();
    }

    public static Source of(String title) {
        return ImmutableSource.builder().title(title).build();
    }

    @Override
    public String toString() {
        return title();
    }

    @Override
    public int compareTo(Source o) {
        return CBUtil.compareString(title(), o.title());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Source that = (Source) o;

        return title().equals(that.title());
    }

    @Override
    public int hashCode() {
        return title().hashCode();
    }
}
