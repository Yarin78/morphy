package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;

@Value.Immutable
public abstract class Annotator extends Entity implements Comparable<Annotator> {
    public abstract String name();

    @Override
    public Entity withCountAndFirstGameId(int count, int firstGameId) {
        return ImmutableAnnotator.builder().from(this).count(count).firstGameId(firstGameId).build();
    }

    public static Annotator of(String name) {
        return ImmutableAnnotator.builder().name(name).build();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(Annotator o) {
        return CBUtil.compareString(name(), o.name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Annotator that = (Annotator) o;

        return name().equals(that.name());
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }
}
