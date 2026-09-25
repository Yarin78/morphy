package se.yarin.morphy.api;

/**
 * What a particular {@link Database} can do. A PGN-backed database, for instance, has no entities
 * of its own, and a database opened read-only cannot be written.
 *
 * @param canWrite games may be added or replaced
 * @param hasEntities the database has a separate entity store (players, tournaments, …) that can
 *     be read and searched
 * @param canEditEntities entities may be updated
 */
public record Capabilities(boolean canWrite, boolean hasEntities, boolean canEditEntities) {}
