package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import static java.nio.file.StandardOpenOption.*;

public class TeamIndex extends EntityIndex<Team> {

  private static final int SERIALIZED_TEAM_SIZE = 63;

  public TeamIndex() {
    this(null);
  }

  public TeamIndex(@Nullable DatabaseContext context) {
    this(
        new InMemoryItemStorage<>(context, "Team", EntityIndexHeader.empty(SERIALIZED_TEAM_SIZE)),
        context);
  }

  protected TeamIndex(
      @NotNull File file, @NotNull Set<OpenOption> openOptions, @NotNull DatabaseContext context)
      throws IOException {
    this(
        new FileItemStorage<>(
            file,
            context,
            "Team",
            new EntityIndexSerializer(SERIALIZED_TEAM_SIZE),
            EntityIndexHeader.empty(SERIALIZED_TEAM_SIZE),
            openOptions),
        context);
  }

  protected TeamIndex(
      @NotNull ItemStorage<EntityIndexHeader, EntityNode> storage,
      @Nullable DatabaseContext context) {
    super(storage, EntityType.TEAM, context);
  }

  public static @NotNull TeamIndex create(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return new TeamIndex(
        file, Set.of(READ, WRITE, CREATE_NEW), context == null ? new DatabaseContext() : context);
  }

  public static @NotNull TeamIndex open(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return open(file, DatabaseMode.READ_WRITE, context);
  }

  public static @NotNull TeamIndex open(
      @NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    if (mode == DatabaseMode.IN_MEMORY) {
      TeamIndex source = open(file, DatabaseMode.READ_ONLY, context);
      TeamIndex target = new TeamIndex(context);
      source.copyEntities(target);
      return target;
    }
    return new TeamIndex(
        file, mode.openOptions(), context == null ? new DatabaseContext() : context);
  }

  /**
   * Searches for teams using a case sensitive prefix search.
   *
   * @param title a prefix of the team name
   * @return a list of matching teams
   */
  public @NotNull List<Team> prefixSearch(@NotNull String title) {
    Team startKey = Team.of(title);
    Team endKey = Team.of(title + "zzz");

    try (EntityIndexReadTransaction<Team> txn = beginReadTransaction()) {
      return txn.streamOrderedAscending(startKey, endKey).collect(Collectors.toList());
    }
  }

  @Override
  protected @NotNull Team deserialize(
      int entityId, int count, int firstGameId, byte[] serializedData) {
    itemMetricsRef().update(metrics -> metrics.addDeserialization(1));
    ByteBuffer buf = ByteBuffer.wrap(serializedData);
    return ImmutableTeam.builder()
        .id(entityId)
        .count(count)
        .firstGameId(firstGameId)
        .title(ByteBufferUtil.getFixedSizeByteString(buf, 45))
        .teamNumber(ByteBufferUtil.getIntL(buf))
        .season((ByteBufferUtil.getUnsignedByte(buf) & 1) > 0)
        .year(ByteBufferUtil.getIntL(buf))
        .nation(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)])
        .build();
  }

  @Override
  protected void serialize(@NotNull Team team, @NotNull ByteBuffer buf) {
    itemMetricsRef().update(metrics -> metrics.addSerialization(1));
    ByteBufferUtil.putFixedSizeByteString(buf, team.title(), 45);
    ByteBufferUtil.putIntL(buf, team.teamNumber());
    ByteBufferUtil.putByte(buf, team.season() ? 1 : 0);
    ByteBufferUtil.putIntL(buf, team.year());
    ByteBufferUtil.putByte(buf, team.nation().ordinal());
  }

  public static void upgrade(@NotNull File file) throws IOException {
    EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_TEAM_SIZE));
  }
}
