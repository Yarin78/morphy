package se.yarin.morphy.cb2.entities;

import org.jetbrains.annotations.NotNull;

/**
 * The kinds of entity in the {@code .2lid} file, in the order their containers appear in a block.
 * An annotator is a {@link #PLAYER}, and the titles of guiding texts and analyses are {@link
 * #GAME_TAG game tags}. The type at index 3 has never held an entity.
 */
public enum EntityType {
  PLAYER(1024),
  TOURNAMENT(1120),
  SOURCE(220),
  UNKNOWN(1024),
  TEAM(314),
  GAME_TAG(532);

  private final int standardContainerSize;

  EntityType(int standardContainerSize) {
    this.standardContainerSize = standardContainerSize;
  }

  /** The container size ChessBase uses; a file's own sizes are read from its header. */
  public int standardContainerSize() {
    return standardContainerSize;
  }

  /** The index of the type in the file header and in a block. */
  public int index() {
    return ordinal();
  }

  public static @NotNull EntityType of(int index) {
    return values()[index];
  }
}
