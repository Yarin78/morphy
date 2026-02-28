package se.yarin.morphy.queries;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.queries.operations.GameEntityLookup;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.queries.operations.TournamentExtraLookup;

public class QuerySortField<T extends IdObject> {
  private final @NotNull Comparator<QueryData<T>> comparator;
  private final boolean requiresData;
  private final @NotNull String name; // also unique identifier (within T)
  private final @NotNull QuerySortOrder.Direction defaultDirection;
  private final @Nullable BiFunction<QueryContext, QueryOperator<T>, QueryOperator<T>>
      requiredLookup;

  private static final BiFunction<QueryContext, QueryOperator<Tournament>, QueryOperator<Tournament>>
      TOURNAMENT_EXTRA_LOOKUP = TournamentExtraLookup::new;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_WHITE_PLAYER_LOOKUP = GameEntityLookup::whitePlayer;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_BLACK_PLAYER_LOOKUP = GameEntityLookup::blackPlayer;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_TOURNAMENT_LOOKUP = GameEntityLookup::tournament;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_SOURCE_LOOKUP = GameEntityLookup::source;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_ANNOTATOR_LOOKUP = GameEntityLookup::annotator;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_TAG_LOOKUP = GameEntityLookup::gameTag;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_WHITE_TEAM_LOOKUP = GameEntityLookup::whiteTeam;

  private static final BiFunction<QueryContext, QueryOperator<Game>, QueryOperator<Game>>
      GAME_BLACK_TEAM_LOOKUP = GameEntityLookup::blackTeam;

  public static <T extends IdObject> QuerySortField<T> id() {
    return new QuerySortField<>(Comparator.comparingInt(QueryData::id), "id", false);
  }

  public static <T extends Entity> QuerySortField<T> entityCount() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().count()),
        "count",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static <T extends Entity> QuerySortField<Game> entityExtendedSort(QuerySortField<T> extraSort) {
    return new QuerySortField<>(
        (o1, o2) -> extraSort.compare(new QueryData<T>((T) o1.extra()), new QueryData<T>((T) o2.extra())),
        "extraSort",
        true,
        QuerySortOrder.Direction.ASCENDING
    );
  }

  public static QuerySortField<Game> playedDate() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().playedDate()),
        "playedDate",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameWhitePlayerName() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Player.class).getFullName()),
        "whitePlayerName",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_WHITE_PLAYER_LOOKUP);
  }

  public static QuerySortField<Game> gameBlackPlayerName() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Player.class).getFullName()),
        "blackPlayerName",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_BLACK_PLAYER_LOOKUP);
  }

  public static QuerySortField<Game> gameResult() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().result().ordinal()),
        "result",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameEco() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().eco().getInt()), "eco", true);
  }

  public static QuerySortField<Game> gameRound() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().round()), "round", true);
  }

  public static QuerySortField<Game> gameTournamentTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Tournament.class).title()),
        "tournament",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_TOURNAMENT_LOOKUP);
  }

  public static QuerySortField<Game> gameSourceTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Source.class).title()),
        "source",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_SOURCE_LOOKUP);
  }

  public static QuerySortField<Game> gameAnnotatorName() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Annotator.class).name()),
        "annotator",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_ANNOTATOR_LOOKUP);
  }

  public static QuerySortField<Game> gameGameTagTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(GameTag.class).title()),
        "gameTag",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_TAG_LOOKUP);
  }

  public static QuerySortField<Game> gameWhiteElo() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().whiteElo()),
        "whiteElo",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameBlackElo() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().blackElo()),
        "blackElo",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameNoMoves() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().noMoves()),
        "noMoves",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameWhiteTeamTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Team.class).title()),
        "whiteTeam",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_WHITE_TEAM_LOOKUP);
  }

  public static QuerySortField<Game> gameBlackTeamTitle() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(Team.class).title()),
        "blackTeam",
        true,
        QuerySortOrder.Direction.ASCENDING,
        GAME_BLACK_TEAM_LOOKUP);
  }

  public static QuerySortField<Game> gameVcs() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().variationsMagnitude() * 100 + o.data().commentariesMagnitude() * 10 + o.data().symbolsMagnitude()),
        "vcs",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameFinalMaterial() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.data().finalMaterialString()),
        "finalMaterial",
        true);
  }

  public static QuerySortField<Game> gameVersion() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().gameVersion()),
        "gameVersion",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameCreationTimestamp() {
    return new QuerySortField<>(
        Comparator.comparingLong(o -> o.data().creationTimestamp()),
        "creationTimestamp",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameLastChanged() {
    return new QuerySortField<>(
        Comparator.comparingLong(o -> o.data().lastChangedTimestamp()),
        "lastChanged",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gamePlayedYear() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> o.data().playedDate().year()),
        "playedYear",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameEloAvg() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> {
          int w = o.data().whiteElo();
          int b = o.data().blackElo();
          if (w > 0 && b > 0) return (w + b) / 2;
          return Math.max(w, b);
        }),
        "eloAvg",
        true,
        QuerySortOrder.Direction.DESCENDING);
  }

  public static QuerySortField<Game> gameEloMax() {
    return new QuerySortField<>(
        Comparator.comparingInt(o -> Math.max(o.data().whiteElo(), o.data().blackElo())),
        "eloMax",
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

  public static QuerySortField<Tournament> tournamentEndDate() {
    return new QuerySortField<>(
        Comparator.comparing(o -> o.extra(TournamentExtra.class).endDate()),
        "endDate",
        true,
        QuerySortOrder.Direction.DESCENDING,
        TOURNAMENT_EXTRA_LOOKUP);
  }

  public static QuerySortField<Tournament> tournamentCoordinates() {
    Comparator<QueryData<Tournament>> comp =
        Comparator.comparingInt(
                (QueryData<Tournament> o) -> o.extra(TournamentExtra.class).latitude() == 0.0 ? 0 : 1)
            .thenComparingDouble(o -> o.extra(TournamentExtra.class).latitude());
    return new QuerySortField<>(
        comp, "coordinates", true, QuerySortOrder.Direction.ASCENDING, TOURNAMENT_EXTRA_LOOKUP);
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

  public static QuerySortField<Tournament> tournamentTiebreak() {
    return new QuerySortField<>(
        Comparator.comparing(
            o -> {
              var rules = o.extra(TournamentExtra.class).tiebreakRules();
              return rules.isEmpty() ? "" : rules.getFirst().tiebreakName();
            }),
        "tiebreak",
        true,
        QuerySortOrder.Direction.ASCENDING,
        TOURNAMENT_EXTRA_LOOKUP);
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
    this(comparator, name, requiresData, QuerySortOrder.Direction.ASCENDING, null);
  }

  private QuerySortField(
      @NotNull Comparator<QueryData<T>> comparator,
      String name,
      boolean requiresData,
      @NotNull QuerySortOrder.Direction defaultDirection) {
    this(comparator, name, requiresData, defaultDirection, null);
  }

  private QuerySortField(
      @NotNull Comparator<QueryData<T>> comparator,
      String name,
      boolean requiresData,
      @NotNull QuerySortOrder.Direction defaultDirection,
      @Nullable BiFunction<QueryContext, QueryOperator<T>, QueryOperator<T>> requiredLookup) {
    this.comparator = comparator;
    this.name = name;
    this.requiresData = requiresData;
    this.defaultDirection = defaultDirection;
    this.requiredLookup = requiredLookup;
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

  public @Nullable BiFunction<QueryContext, QueryOperator<T>, QueryOperator<T>> requiredLookup() {
    return requiredLookup;
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
