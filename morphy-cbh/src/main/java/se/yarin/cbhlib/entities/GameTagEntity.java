package se.yarin.cbhlib.entities;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GameTagEntity implements Entity, Comparable<GameTagEntity> {
    @Getter
    private int id;

    @Getter
    @NonNull
    private String englishTitle;

    @Getter
    @NonNull
    private String germanTitle;

    @Getter
    @NonNull
    private String frenchTitle;

    @Getter
    @NonNull
    private String spanishTitle;

    @Getter
    @NonNull
    private String italianTitle;

    @Getter
    @NonNull
    private String dutchTitle;

    @Getter
    @NonNull
    private String slovenianTitle;

    @Getter
    @NonNull
    private String resTitle;

    @Getter
    private int count;

    @Getter
    private int firstGameId;

    public GameTagEntity(@NonNull String englishTitle) {
        this.englishTitle = englishTitle;
        this.germanTitle = "";
        this.frenchTitle = "";
        this.spanishTitle = "";
        this.italianTitle = "";
        this.dutchTitle = "";
        this.slovenianTitle = "";
        this.resTitle = "";
    }

    @Override
    public int compareTo(GameTagEntity o) {
        int comp = englishTitle.compareTo(o.englishTitle);
        if (comp != 0) return comp;
        comp = germanTitle.compareTo(o.germanTitle);
        if (comp != 0) return comp;
        comp = frenchTitle.compareTo(o.frenchTitle);
        if (comp != 0) return comp;
        comp = spanishTitle.compareTo(o.spanishTitle);
        if (comp != 0) return comp;
        comp = italianTitle.compareTo(o.italianTitle);
        if (comp != 0) return comp;
        comp = dutchTitle.compareTo(o.dutchTitle);
        if (comp != 0) return comp;
        comp = slovenianTitle.compareTo(o.slovenianTitle);
        if (comp != 0) return comp;
        return resTitle.compareTo(o.resTitle);
    }

    @Override
    public GameTagEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public GameTagEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameTagEntity that = (GameTagEntity) o;

        return englishTitle.equals(that.englishTitle)
                && germanTitle.equals(that.germanTitle)
                && frenchTitle.equals(that.frenchTitle)
                && spanishTitle.equals(that.spanishTitle)
                && italianTitle.equals(that.italianTitle)
                && dutchTitle.equals(that.dutchTitle)
                && slovenianTitle.equals(that.slovenianTitle)
                && resTitle.equals(that.resTitle);
    }

    @Override
    public int hashCode() {
        return englishTitle.hashCode() ^
                germanTitle.hashCode() ^
                frenchTitle.hashCode() ^
                spanishTitle.hashCode() ^
                italianTitle.hashCode() ^
                dutchTitle.hashCode() ^
                slovenianTitle.hashCode() ^
                resTitle.hashCode();
    }
}
