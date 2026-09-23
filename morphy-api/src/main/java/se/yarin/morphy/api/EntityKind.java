package se.yarin.morphy.api;

/**
 * The kinds of entity a game header can refer to, shared by every database format.
 *
 * <p>This is the neutral counterpart of the v1 {@code se.yarin.morphy.entities.EntityType}. An
 * annotator is a player in v2, but is addressed as its own kind here because consumers still speak
 * of annotators.
 */
public enum EntityKind {
  PLAYER,
  TOURNAMENT,
  ANNOTATOR,
  SOURCE,
  TEAM,
  GAME_TAG
}
