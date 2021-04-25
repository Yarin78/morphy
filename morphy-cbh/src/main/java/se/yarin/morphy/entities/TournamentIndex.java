package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public class TournamentIndex extends EntityIndex<Tournament> {
    private static final Logger log = LoggerFactory.getLogger(TournamentIndex.class);

    private static final int SERIALIZED_TOURNAMENT_SIZE = 90;

    public TournamentIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_TOURNAMENT_SIZE), null));
    }

    protected TournamentIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_TOURNAMENT_SIZE), EntityIndexHeader.empty(SERIALIZED_TOURNAMENT_SIZE), openOptions));
    }

    protected TournamentIndex(
            @NotNull ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Tournament");
    }

    public static @NotNull TournamentIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return new TournamentIndex(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static @NotNull TournamentIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    public static @NotNull TournamentIndex open(@NotNull File file, @NotNull DatabaseMode mode)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            TournamentIndex source = open(file, DatabaseMode.READ_ONLY);
            TournamentIndex target = new TournamentIndex();
            source.copyEntities(target);
            return target;
        }
        return new TournamentIndex(file, mode.openOptions());

        /*
        File extraFile = CBUtil.fileWithExtension(file, ".cbtt");

        Set<OpenOption> extraOptions = new HashSet<>(options);
        if (options.contains(StandardOpenOption.WRITE)) {
            // Always create the extra tournament file if it's missing and we're in WRITE mode
            extraOptions.add(StandardOpenOption.CREATE);
        }
        TournamentExtraStorage extraStorage;
        if (!extraFile.exists() && !extraOptions.contains(CREATE) && !extraOptions.contains(CREATE_NEW)) {
            // If the extra storage file is missing and we can't create it, use an empty in-memory version instead
            extraStorage = new TournamentExtraStorage();
        } else {
            extraStorage = TournamentExtraStorage.open(extraFile, extraOptions);
        }
        */
    }

    @Override
    public @NotNull EntityIndexTransaction<Tournament> beginTransaction() {
        // TODO: Acquire read lock
        return new TournamentIndexTransaction(this);
    }

    public @NotNull TournamentIndexTransaction beginTransaction(@NotNull TournamentExtraStorage extraStorage) {
        // TODO: Acquire read lock
        return new TournamentIndexTransaction(this, extraStorage);
    }

    /**
     * Searches for tournaments using a case sensitive prefix search.
     * The exact year must also be specified as the primary key starts with the year.
     * @param year the exact year of the tournament
     * @param name a prefix of the title of the tournament
     * @return a stream over matching tournaments
     */
    public @NotNull Stream<Tournament> prefixSearch(int year, @NotNull String name) {
        Tournament startKey = Tournament.of(name, new Date(year));
        Tournament endKey = Tournament.of(name + "zzz", new Date(year));
        return streamOrderedAscending(startKey, endKey);
    }

    /**
     * Searches for tournaments in the specified year range
     * @param fromYear the start year, inclusive
     * @param toYear the end year, inclusive
     * @return a stream over matching tournaments, with the most recent tournament first
     */
    public @NotNull Stream<Tournament> rangeSearch(int fromYear, int toYear) {
        // Tournaments are sorted by year in reverse
        Tournament startKey = Tournament.of("", new Date(toYear));
        Tournament endKey = Tournament.of("", new Date(fromYear-1));
        return streamOrderedAscending(startKey, endKey);
    }

    protected @NotNull Tournament deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);

        ImmutableTournament.Builder builder = ImmutableTournament.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                // .raw(serializedData)
                .title(ByteBufferUtil.getFixedSizeByteString(buf, 40))
                .place(ByteBufferUtil.getFixedSizeByteString(buf, 30))
                .date(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));
        int typeByte = ByteBufferUtil.getUnsignedByte(buf);
        int teamByte = ByteBufferUtil.getUnsignedByte(buf);
        builder.teamTournament((teamByte & 1) == 1);
        if ((teamByte & ~1) > 0) {
            // Never seen in the wild
            log.debug("Unused bits set in team byte when deserializing tournament with id " + entityId);
        }
        builder.nation(CBUtil.decodeNation(ByteBufferUtil.getUnsignedByte(buf)));
        int unusedByte1 = ByteBufferUtil.getUnsignedByte(buf);
        builder.category(ByteBufferUtil.getUnsignedByte(buf));
        int tournamentFlags = ByteBufferUtil.getUnsignedByte(buf);
        builder.rounds(ByteBufferUtil.getUnsignedByte(buf));
        int unusedByte2 = ByteBufferUtil.getUnsignedByte(buf);

        builder.type(CBUtil.decodeTournamentType(typeByte));
        builder.timeControl(CBUtil.decodeTournamentTimeControl(typeByte));
        // bit 0 and 1 both refers to the complete flag, and from tournaments 2005 and onwards it's always the same value
        builder.legacyComplete((tournamentFlags & 1) > 0);
        builder.complete((tournamentFlags & 2) > 0);
        builder.boardPoints((tournamentFlags & 4) > 0);
        builder.threePointsWin((tournamentFlags & 8) > 0);

        if (unusedByte1 != 0) {
            // Never seen in the wild
            log.debug("unknownByte1 = " + unusedByte1 + " when deserializing tournament with id " + entityId);
        }
        if (unusedByte2 != 0) {
            // Never seen in the wild
            log.debug("unknownByte2 = " + unusedByte2 + " when deserializing tournament with id " + entityId);
        }
        if ((tournamentFlags & ~15) != 0) {
            log.debug("optionByte = " + tournamentFlags + " when deserializing tournament with id " + entityId);
        }

        return builder.build();
    }

    @Override
    protected void serialize(@NotNull Tournament tournament, @NotNull ByteBuffer buf) {
        int typeByte = CBUtil.encodeTournamentType(tournament.type(), tournament.timeControl());

        int optionByte =
                (tournament.legacyComplete() ? 1 : 0) +
                (tournament.complete() ? 2 : 0) +
                (tournament.boardPoints() ? 4 : 0) +
                (tournament.threePointsWin() ? 8 : 0);

        ByteBufferUtil.putFixedSizeByteString(buf, tournament.title(), 40);
        ByteBufferUtil.putFixedSizeByteString(buf, tournament.place(), 30);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(tournament.date()));
        ByteBufferUtil.putByte(buf, typeByte);
        ByteBufferUtil.putByte(buf, tournament.teamTournament() ? 1 : 0);
        ByteBufferUtil.putByte(buf, CBUtil.encodeNation(tournament.nation()));
        ByteBufferUtil.putByte(buf, 0); // Or is nation 2 bytes?
        ByteBufferUtil.putByte(buf, tournament.category());
        ByteBufferUtil.putByte(buf, optionByte);
        ByteBufferUtil.putByte(buf, tournament.rounds());
        ByteBufferUtil.putByte(buf, 0); // Or is rounds 2 bytes?
    }

    public static void upgrade(@NotNull File file) throws IOException {
        EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_TOURNAMENT_SIZE));
    }
}
