package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class GameTag extends Entity implements Comparable<GameTag> {
    @Value.Default
    public String englishTitle() { return ""; };

    @Value.Default
    public String germanTitle() { return ""; };

    @Value.Default
    public String frenchTitle() { return ""; };

    @Value.Default
    public String spanishTitle() { return ""; };

    @Value.Default
    public String italianTitle() { return ""; };

    @Value.Default
    public String dutchTitle() { return ""; };

    @Value.Default
    public String slovenianTitle() { return ""; };

    @Value.Default
    public String resTitle() { return ""; };

    @Override
    public Entity withCountAndFirstGameId(int count, int firstGameId) {
        return ImmutableGameTag.builder().from(this).count(count).firstGameId(firstGameId).build();
    }

    public static GameTag of(String englishTitle) {
        return ImmutableGameTag.builder().englishTitle(englishTitle).build();
    }

    @Override
    public int compareTo(GameTag o) {
        int comp = englishTitle().compareTo(o.englishTitle());
        if (comp != 0) return comp;
        comp = germanTitle().compareTo(o.germanTitle());
        if (comp != 0) return comp;
        comp = frenchTitle().compareTo(o.frenchTitle());
        if (comp != 0) return comp;
        comp = spanishTitle().compareTo(o.spanishTitle());
        if (comp != 0) return comp;
        comp = italianTitle().compareTo(o.italianTitle());
        if (comp != 0) return comp;
        comp = dutchTitle().compareTo(o.dutchTitle());
        if (comp != 0) return comp;
        comp = slovenianTitle().compareTo(o.slovenianTitle());
        if (comp != 0) return comp;
        return resTitle().compareTo(o.resTitle());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameTag that = (GameTag) o;

        return englishTitle().equals(that.englishTitle())
                && germanTitle().equals(that.germanTitle())
                && frenchTitle().equals(that.frenchTitle())
                && spanishTitle().equals(that.spanishTitle())
                && italianTitle().equals(that.italianTitle())
                && dutchTitle().equals(that.dutchTitle())
                && slovenianTitle().equals(that.slovenianTitle())
                && resTitle().equals(that.resTitle());
    }

    @Override
    public int hashCode() {
        return englishTitle().hashCode() ^
                germanTitle().hashCode() ^
                frenchTitle().hashCode() ^
                spanishTitle().hashCode() ^
                italianTitle().hashCode() ^
                dutchTitle().hashCode() ^
                slovenianTitle().hashCode() ^
                resTitle().hashCode();
    }
}
