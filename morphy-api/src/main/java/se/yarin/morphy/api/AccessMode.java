package se.yarin.morphy.api;

/** How a {@link Database} is opened. Maps onto each format's own finer-grained modes. */
public enum AccessMode {
  /** Open for reading only; writes are rejected. */
  READ_ONLY,
  /** Open for reading and writing. */
  READ_WRITE,
  /** Load into memory; changes are not persisted to disk. */
  IN_MEMORY
}
