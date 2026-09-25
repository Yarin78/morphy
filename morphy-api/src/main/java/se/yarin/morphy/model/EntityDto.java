package se.yarin.morphy.model;

/** A DTO for an entity that games refer to: a player, tournament, annotator, source, team or tag. */
public interface EntityDto {

  /** The entity's id in its database. */
  Long id();
}
