package se.yarin.morphy;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.queries.operations.QueryOperator;

/**
 * Diagnostics specific to the v1 ({@code .cbh}) format, reached through {@link
 * se.yarin.morphy.api.Database#extension(Class)}: how the query planner executes a search, and the
 * raw records behind games and entities.
 */
public interface CbhDiagnostics {

  /**
   * Explains how a game search is executed: every candidate query plan, cheapest first. The
   * cheapest plan, which is the one a search uses, is always executed with profiling.
   *
   * @param query the search; its sort order is part of the plans, its window is ignored
   * @param executeAllPlans also execute the other plans, unless too expensive, and compare their
   *     results with the cheapest plan's
   */
  @NotNull
  QueryExplanation explainGames(@NotNull Query query, boolean executeAllPlans);

  /** As {@link #explainGames}, for a search for entities of a kind. */
  @NotNull
  QueryExplanation explainEntities(
      @NotNull EntityKind<?> kind, @NotNull Query query, boolean executeAllPlans);

  /**
   * The raw records behind a game: its {@code .cbh} header, its {@code .cbj} extended header, its
   * moves (or guiding text) in {@code .cbg}, and its annotations in {@code .cba} if it has any.
   *
   * @throws IllegalArgumentException if there is no game with that id
   */
  @NotNull
  List<RawRecord> rawGame(long id);

  /**
   * The raw records behind an entity: its record in the entity's index file ({@code .cbp},
   * {@code .cbt}, …), and for a tournament also its {@code .cbtt} record.
   *
   * @throws IllegalArgumentException if there is no entity of that kind with that id
   */
  @NotNull
  List<RawRecord> rawEntity(@NotNull EntityKind<?> kind, long id);

  /**
   * One stored record.
   *
   * @param file the extension of the file it is stored in, e.g. {@code ".cbh"}
   * @param bytes the record's bytes
   */
  record RawRecord(@NotNull String file, byte @NotNull [] bytes) {}

  /**
   * The candidate plans for a search.
   *
   * @param description a readable description of the query
   * @param allPlansAgree whether all executed plans found the same items; null unless all plans
   *     were asked to be executed
   * @param plans the plans, cheapest first
   */
  record QueryExplanation(
      @NotNull String description,
      @Nullable Boolean allPlansAgree,
      @NotNull List<ExplainedPlan> plans) {}

  /**
   * One candidate plan.
   *
   * @param operator the root operator of the plan, carrying estimated (and, if executed, actual)
   *     costs
   * @param executed whether the plan was executed
   * @param resultCount the number of items the plan found, if executed
   * @param differsFromBest whether the plan found different items than the cheapest plan, if both
   *     were executed
   */
  record ExplainedPlan(
      @NotNull QueryOperator<?> operator,
      boolean executed,
      @Nullable Integer resultCount,
      @Nullable Boolean differsFromBest) {}
}
