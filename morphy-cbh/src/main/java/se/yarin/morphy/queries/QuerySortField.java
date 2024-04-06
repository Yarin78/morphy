package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.queries.operations.QueryData;

import java.util.Comparator;
import java.util.Objects;

public class QuerySortField<T extends IdObject> {
    private final @NotNull Comparator<QueryData<T>> comparator;
    private final boolean requiresData;
    private final @NotNull String name; // also unique identifier (within T)

    public static <T extends IdObject> QuerySortField<T> id() {
        return new QuerySortField<>(Comparator.comparingInt(QueryData::id), "id", false);
    }

    public static <T extends IdObject> QuerySortField<T> weight() {
        return new QuerySortField<>(Comparator.comparingDouble(QueryData::weight), "weight", false);
    }

    public static QuerySortField<Game> playedDate() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().playedDate()), "playedDate", true);
    }

    public static QuerySortField<Player> playerName() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().getFullName()), "name", true);
    }

    public static QuerySortField<Tournament> tournamentYear() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().date().year()), "year", true);
    }

    public static QuerySortField<Tournament> tournamentStartDate() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().date()), "startDate", true);
    }

    public static QuerySortField<Tournament> tournamentTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().title()), "title", true);
    }

    public static QuerySortField<Tournament> tournamentPlace() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().place()), "place", true);
    }

    public static QuerySortField<Annotator> annotatorName() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().name()), "name", true);
    }

    public static QuerySortField<GameTag> gameTagEnglishTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().englishTitle()), "englishTitle", true);
    }

    public static QuerySortField<GameTag> gameTagGermanTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().germanTitle()), "germanTitle", true);
    }

    public static QuerySortField<GameTag> gameTagDutchTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().dutchTitle()), "dutchTitle", true);
    }

    public static QuerySortField<GameTag> gameTagFrenchTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().frenchTitle()), "frenchTitle", true);
    }

    public static QuerySortField<GameTag> gameTagItalianTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().italianTitle()), "italianTitle", true);
    }

    public static QuerySortField<GameTag> gameTagSlovenianTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().slovenianTitle()), "slovenianTitle", true);
    }

    public static QuerySortField<GameTag> gameTagSpanishTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().spanishTitle()), "spanishTitle", true);
    }

    public static QuerySortField<Source> sourceTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().title()), "title", true);
    }

    public static QuerySortField<Team> teamTitle() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().title()), "title", true);
    }

    public static QuerySortField<Team> teamNumber() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().teamNumber()), "number", true);
    }

    public static QuerySortField<Team> teamSeason() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().season()), "season", true);
    }

    public static QuerySortField<Team> teamYear() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().year()), "year", true);
    }

    public static QuerySortField<Team> teamNation() {
        return new QuerySortField<>(Comparator.comparing(o -> o.data().nation()), "nation", true);
    }

    private QuerySortField(@NotNull Comparator<QueryData<T>> comparator, String name, boolean requiresData) {
        this.comparator = comparator;
        this.name = name;
        this.requiresData = requiresData;
    }

    public int compare(QueryData<T> data1, QueryData<T> data2) {
        return comparator.compare(data1, data2);
    }

    public boolean requiresData() {
        return requiresData;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuerySortField<?> that = (QuerySortField<?>) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
