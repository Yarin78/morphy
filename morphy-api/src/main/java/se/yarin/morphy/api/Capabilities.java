package se.yarin.morphy.api;

/**
 * What a particular {@link Database} implementation can do. A PGN-backed database, for
 * instance, is read-only and has no raw record bytes to expose.
 *
 * @param canWrite games may be added or replaced
 * @param hasRawData game and entity records can be returned with their raw storage bytes
 * @param hasEntities the database has a separate entity store (players, tournaments, …)
 */
public record Capabilities(boolean canWrite, boolean hasRawData, boolean hasEntities) {

  /** A fully featured, writable database with entities and raw record access. */
  public static Capabilities full() {
    return new Capabilities(true, true, true);
  }

  /** A read-only database with entities but no raw record access. */
  public static Capabilities readOnlyWithEntities() {
    return new Capabilities(false, false, true);
  }
}
