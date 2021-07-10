package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
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
