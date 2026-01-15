package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public class PlayerIndex extends EntityIndex<Player> {
  private static final Logger log = LoggerFactory.getLogger(PlayerIndex.class);

  public static final int SERIALIZED_PLAYER_SIZE = 58;

  public PlayerIndex() {
    this(null);
  }

  public PlayerIndex(@Nullable DatabaseContext context) {
    this(
        new InMemoryItemStorage<>(
            context, "Player", EntityIndexHeader.empty(SERIALIZED_PLAYER_SIZE)),
        context);
  }

  protected PlayerIndex(
      @NotNull File file, @NotNull Set<OpenOption> openOptions, @NotNull DatabaseContext context)
      throws IOException {
    this(
        new FileItemStorage<>(
            file,
            context,
            "Player",
            new EntityIndexSerializer(SERIALIZED_PLAYER_SIZE),
            EntityIndexHeader.empty(SERIALIZED_PLAYER_SIZE),
            openOptions),
        context);
  }

  protected PlayerIndex(
      @NotNull ItemStorage<EntityIndexHeader, EntityNode> storage,
      @Nullable DatabaseContext context) {
    super(storage, EntityType.PLAYER, context);
  }

  public static @NotNull PlayerIndex create(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return new PlayerIndex(
        file, Set.of(READ, WRITE, CREATE_NEW), context == null ? new DatabaseContext() : context);
  }

  public static @NotNull PlayerIndex open(@NotNull File file) throws IOException {
    return PlayerIndex.open(file, (DatabaseContext) null);
  }

  public static @NotNull PlayerIndex open(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return open(file, DatabaseMode.READ_WRITE, context);
  }

  public static @NotNull PlayerIndex open(@NotNull File file, @NotNull DatabaseMode mode)
      throws IOException {
    return open(file, mode, null);
  }

  public static @NotNull PlayerIndex open(
      @NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    if (mode == DatabaseMode.IN_MEMORY) {
      PlayerIndex source = open(file, DatabaseMode.READ_ONLY, context);
      PlayerIndex target = new PlayerIndex(context);
      source.copyEntities(target);
      return target;
    }

    return new PlayerIndex(
        file, mode.openOptions(), context == null ? new DatabaseContext() : context);
  }

  /**
   * Searches for players using a case sensitive prefix search.
   *
   * @param name a prefix of the last name of the player; first name can be specified after a comma
   * @return a stream over matching players
   */
  public @NotNull List<Player> prefixSearch(@NotNull String name) {
    try (EntityIndexReadTransaction<Player> txn = beginReadTransaction()) {
      return prefixStream(txn, name).collect(Collectors.toList());
    }
  }

  /**
   * Searches for players using a case sensitive prefix search. If first name is specified, last
   * name will have to match exactly.
   *
   * @param lastName a prefix of the last name of the player
   * @param firstName a prefix of the first name of the player (or null/empty).
   * @return a list of matching players
   */
  public @NotNull List<Player> prefixSearch(@NotNull String lastName, String firstName) {
    try (EntityIndexReadTransaction<Player> txn = beginReadTransaction()) {
      return prefixStream(txn, lastName, firstName).collect(Collectors.toList());
    }
  }

  @NotNull
  Stream<Player> prefixStream(
      @NotNull EntityIndexReadTransaction<Player> transaction, @NotNull String name) {
    if (name.contains(",")) {
      String[] parts = name.split(",", 2);
      return prefixStream(transaction, parts[0].strip(), parts[1].strip());
    }
    return prefixStream(transaction, name, null);
  }

  @NotNull
  Stream<Player> prefixStream(
      @NotNull EntityIndexReadTransaction<Player> transaction,
      @NotNull String lastName,
      String firstName) {
    if (transaction.index() != this) {
      throw new IllegalArgumentException("Mismatching index in transaction");
    }
    Player startKey = Player.of(lastName, firstName == null ? "" : firstName);
    Player endKey =
        firstName == null
            ? Player.of(lastName + "zzz", "")
            : Player.of(lastName, firstName + "zzz");
    return transaction.streamOrderedAscending(startKey, endKey);
  }

  @Override
  protected @NotNull Player deserialize(
      int entityId, int count, int firstGameId, byte[] serializedData) {
    itemMetricsRef().update(metrics -> metrics.addDeserialization(1));
    ByteBuffer buf = ByteBuffer.wrap(serializedData);
    return ImmutablePlayer.builder()
        .id(entityId)
        .count(count)
        .firstGameId(firstGameId)
        .lastName(ByteBufferUtil.getFixedSizeByteString(buf, 30))
        .firstName(ByteBufferUtil.getFixedSizeByteString(buf, 20))
        .build();
  }

  @Override
  protected void serialize(@NotNull Player player, @NotNull ByteBuffer buf) {
    itemMetricsRef().update(metrics -> metrics.addSerialization(1));
    ByteBufferUtil.putFixedSizeByteString(buf, player.lastName(), 30);
    ByteBufferUtil.putFixedSizeByteString(buf, player.firstName(), 20);
  }

  public static void upgrade(@NotNull File file) throws IOException {
    EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_PLAYER_SIZE));
  }
}
