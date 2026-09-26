package se.yarin.morphy.cb2;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityFile;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;

/**
 * Finds an existing entity equal to one about to be written, so that a game refers to it rather
 * than to a new copy. Two entities are equal when their identity fields are: a player's names, a
 * tournament's title, place and start date, a source's title and publisher, a team's title, number,
 * year and nation, and a game tag's titles.
 *
 * <p>The ids of each type are read from the entity file the first time they're needed, and kept up
 * to date as entities are written. When the file holds equal entities, the first is used.
 */
final class EntityLookup {
  private final @NotNull EntityFile entities;
  private final Map<EntityType, Map<String, Integer>> ids = new EnumMap<>(EntityType.class);

  EntityLookup(@NotNull EntityFile entities) {
    this.entities = entities;
  }

  /** The identity of an entity: equal identities make equal entities. */
  static @NotNull String identity(@NotNull Entity entity) {
    return switch (entity) {
      case Player p -> p.lastName() + "\u0000" + p.firstName();
      case Tournament t -> t.title() + "\u0000" + t.place() + "\u0000" + t.startDate();
      case Source s -> s.title() + "\u0000" + s.publisher();
      case Team t -> t.title() + "\u0000" + t.number() + "\u0000" + t.year() + "\u0000" + t.nation();
      case GameTag g ->
          g.titles().stream()
              .filter(t -> !t.text().isEmpty())
              .map(t -> t.language() + ":" + t.text())
              .sorted()
              .collect(Collectors.joining("\u0000"));
    };
  }

  private Map<String, Integer> ids(EntityType type) {
    return ids.computeIfAbsent(
        type,
        t -> {
          Map<String, Integer> map = new HashMap<>();
          for (int id = 0; id < entities.count(t); id++) {
            Entity entity = entities.get(t, id);
            if (entity != null) {
              map.putIfAbsent(identity(entity), id);
            }
          }
          return map;
        });
  }

  /** The id of an existing entity equal to this one, or null. */
  @Nullable
  Integer find(@NotNull Entity entity) {
    return ids(entity.type()).get(identity(entity));
  }

  /** Records that an entity now has an id. */
  void added(int id, @NotNull Entity entity) {
    if (ids.containsKey(entity.type())) {
      ids.get(entity.type()).putIfAbsent(identity(entity), id);
    }
  }

  /** Records that an entity no longer has an id, or no longer these fields. */
  void removed(int id, @NotNull Entity entity) {
    if (ids.containsKey(entity.type())) {
      ids.get(entity.type()).remove(identity(entity), id);
    }
  }

  /** Forgets everything, to be read again when needed. */
  void clear() {
    ids.clear();
  }
}
