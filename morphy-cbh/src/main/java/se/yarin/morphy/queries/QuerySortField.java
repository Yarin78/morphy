package se.yarin.morphy.queries;

import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.queries.operations.QueryData;

public class QuerySortField<T extends IdObject> {
  private final @NotNull Comparator<QueryData<T>> comparator;
  private final boolean requiresData;
  private final @NotNull String name; // also unique identifier (within T)
  private final @NotNull QuerySortOrder.Direction defaultDirection;

  public static <T extends IdObject> QuerySortField<T> id() {
    return new QuerySortField<>(Comparator.comparingInt(QueryData::id), "id", false);
  }

  public static <T extends IdObject> QuerySortField<T> weight() {
    return new QuerySortField<>(
        Comparator.comparingDouble(QueryData::weight),
        "weight",
        false,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static <T extends Entity> QuerySortField<T> entityCount() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().count()),
        "count",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> playedDate() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().playedDate()),
        "playedDate",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Player> playerName() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().getFullName()), "name", true);
  }

  public static QuerySortField<Player> playerFirstName() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().firstName()), "firstName", true);
  }

  public static QuerySortField<Player> playerLastName() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().lastName()), "lastName", true);
  }

  public static QuerySortField<Tournament> tournamentYear() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().date().year()),
        "year",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Tournament> tournamentStartDate() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().date()),
        "startDate",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Tournament> tournamentTitle() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().title()), "title", true);
  }

  public static QuerySortField<Tournament> tournamentPlace() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().place()), "place", true);
  }

  public static QuerySortField<Tournament> tournamentCombinedType() {
    Comparator<QueryData<Tournament>> comp =
        Comparator.comparingInt((QueryData<Tournament> o) -> o.data().teamTournament() ? 1 : 0)
            .thenComparingInt(o -> o.data().timeControl().ordinal())
            .thenComparingInt(o -> o.data().type().ordinal());
    return new QuerySortField<>(comp, "combinedType", true);
  }

  public static QuerySortField<Tournament> tournamentNation() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().nation().getIocCode()), "nation", true);
  }

  public static QuerySortField<Tournament> tournamentCategory() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().category()),
        "category",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Tournament> tournamentRounds() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().rounds()),
        "rounds",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Tournament> tournamentComplete() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().complete() ? 1 : 0), "complete", true);
  }

  public static QuerySortField<Annotator> annotatorName() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().name()), "name", true);
  }

  public static QuerySortField<GameTag> gameTagTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().title()), "title", true);
  }

  public static QuerySortField<GameTag> gameTagLanguages() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().languages()), "languages", true);
  }

  public static QuerySortField<GameTag> gameTagLanguageCount() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().languageCount()),
        "languageCount",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<GameTag> gameTagEnglishTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().englishTitle()), "englishTitle", true);
  }

  public static QuerySortField<GameTag> gameTagGermanTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().germanTitle()), "germanTitle", true);
  }

  public static QuerySortField<GameTag> gameTagDutchTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().dutchTitle()), "dutchTitle", true);
  }

  public static QuerySortField<GameTag> gameTagFrenchTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().frenchTitle()), "frenchTitle", true);
  }

  public static QuerySortField<GameTag> gameTagItalianTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().italianTitle()), "italianTitle", true);
  }

  public static QuerySortField<GameTag> gameTagSlovenianTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().slovenianTitle()), "slovenianTitle", true);
  }

  public static QuerySortField<GameTag> gameTagSpanishTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().spanishTitle()), "spanishTitle", true);
  }

  public static QuerySortField<Source> sourceTitle() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().title()), "title", true);
  }

  public static QuerySortField<Source> sourcePublisher() {
    return new QuerySortField<>(Comparator.comparing(o -> o.data().publisher()), "publisher", true);
  }

  public static QuerySortField<Source> sourceDate() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().date()),
        "date",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Source> sourcePublication() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().publication()),
        "publication",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Source> sourceVersion() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().version()), "version", true);
  }

  public static QuerySortField<Source> sourceQuality() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().quality()), "quality", true);
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

  private QuerySortField(
      @NotNull Comparator<QueryData<T>> comparator, String name, boolean requiresData) {
    this(comparator, name, requiresData, QuerySortOrder.Direction.ASCENDING);
  }

  private QuerySortField(
      @NotNull Comparator<QueryData<T>> comparator,
      String name,
      boolean requiresData,
      @NotNull QuerySortOrder.Direction defaultDirection) {
    this.comparator = comparator;
    this.name = name;
    this.requiresData = requiresData;
    this.defaultDirection = defaultDirection;
  }

  public @NotNull String name() {
    return name;
  }

  public @NotNull QuerySortOrder.Direction defaultDirection() {
    return defaultDirection;
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
