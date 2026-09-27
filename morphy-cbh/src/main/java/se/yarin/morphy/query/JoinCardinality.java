package se.yarin.morphy.query;

/**
 * Describes the expected cardinality of a join from the left side's perspective.
 *
 * <ul>
 *   <li>{@link #ONE_TO_ONE} — each left row matches at most one right row.
 *   <li>{@link #ONE_TO_MANY} — each left row may match multiple right rows.
 * </ul>
 */
public enum JoinCardinality {
  ONE_TO_ONE,
  ONE_TO_MANY
}
