package se.yarin.morphy.api;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * A kind of entity that games refer to, typed by the DTO that represents it: {@link #PLAYER} is an
 * {@code EntityKind<PlayerDto>}, so {@code database.getEntity(EntityKind.PLAYER, id)} returns a
 * {@link PlayerDto}.
 *
 * <p>An annotator is a player in the v2 format, but is addressed as its own kind here because
 * consumers speak of annotators.
 *
 * @param <T> the DTO type of this kind of entity
 */
public final class EntityKind<T> {

  public static final EntityKind<PlayerDto> PLAYER = new EntityKind<>("PLAYER", PlayerDto.class);
  public static final EntityKind<TournamentDto> TOURNAMENT =
      new EntityKind<>("TOURNAMENT", TournamentDto.class);
  public static final EntityKind<AnnotatorDto> ANNOTATOR =
      new EntityKind<>("ANNOTATOR", AnnotatorDto.class);
  public static final EntityKind<SourceDto> SOURCE = new EntityKind<>("SOURCE", SourceDto.class);
  public static final EntityKind<TeamDto> TEAM = new EntityKind<>("TEAM", TeamDto.class);
  public static final EntityKind<GameTagDto> GAME_TAG =
      new EntityKind<>("GAME_TAG", GameTagDto.class);

  private static final List<EntityKind<?>> ALL =
      List.of(PLAYER, TOURNAMENT, ANNOTATOR, SOURCE, TEAM, GAME_TAG);

  private final @NotNull String name;
  private final @NotNull Class<T> dtoType;

  private EntityKind(@NotNull String name, @NotNull Class<T> dtoType) {
    this.name = name;
    this.dtoType = dtoType;
  }

  /** The kind's name, e.g. {@code "PLAYER"}. */
  public @NotNull String name() {
    return name;
  }

  /** The DTO class representing entities of this kind. */
  public @NotNull Class<T> dtoType() {
    return dtoType;
  }

  /** All entity kinds. */
  public static @NotNull List<EntityKind<?>> values() {
    return ALL;
  }

  /**
   * Looks up a kind by name.
   *
   * @throws IllegalArgumentException if there is no kind with that name
   */
  public static @NotNull EntityKind<?> valueOf(@NotNull String name) {
    for (EntityKind<?> kind : ALL) {
      if (kind.name.equals(name)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("No entity kind " + name);
  }

  @Override
  public String toString() {
    return name;
  }
}
