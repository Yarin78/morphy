package se.yarin.morphy.queries;

/**
 * Selects which query engine {@link se.yarin.morphy.DatabaseCbhFacade} uses to execute searches.
 *
 * <p>{@link #LEGACY} is the cost-based planner in this package ({@code se.yarin.morphy.queries}),
 * used by default. {@link #NODE} refers to the node-based physical-plan engine in {@code
 * se.yarin.morphy.query} (singular); it is not yet reachable through {@code findGames}/{@code
 * findEntities} because the logical-query-to-physical-plan translator for it hasn't been built
 * yet (see {@code morphy-cbh/docs/NODE-QUERY-ENGINE.md}).
 */
public enum QueryEngineKind {
  LEGACY,
  NODE
}
