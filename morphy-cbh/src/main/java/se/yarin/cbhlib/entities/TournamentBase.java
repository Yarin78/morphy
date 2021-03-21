package se.yarin.cbhlib.entities;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.storage.EntityNodeStorageBase;
import se.yarin.cbhlib.storage.PersistentEntityNodeStorage;
import se.yarin.cbhlib.storage.TreePath;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

public class TournamentBase extends EntityBase<TournamentEntity> {

    private static final Logger log = LoggerFactory.getLogger(TournamentBase.class);

    private static final int SERIALIZED_TOURNAMENT_SIZE = 90;

    /**
     * Creates a new in-memory tournament database that is initially empty.
     */
    public TournamentBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private TournamentBase(@NonNull EntityStorage<TournamentEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory tournament base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory tournament base
     */
    public static TournamentBase openInMemory(@NonNull File file) throws IOException {
        return new TournamentBase(loadInMemoryStorage(file, new TournamentBase()));
    }

    /**
     * Opens a tournament database from disk
     * @param file the tournament databases to open
     * @return the opened tournament database
     * @throws IOException if something went wrong when opening the database
     */
    public static TournamentBase open(@NonNull File file) throws IOException {
        return new TournamentBase(EntityStorageImpl.open(file, new TournamentBase()));
    }

    /**
     * Creates a new tournament database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened tournament database
     * @throws IOException if something went wrong when creating the database
     */
    public static TournamentBase create(@NonNull File file) throws IOException {
        return create(file, false);
    }

    /**
     * Creates a new tournament database on disk, optionally keeping everything in memory until the base is closed.
     * This is much more performant if adding a lot of entities immediately after creating the database.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @param createOnClose if true, the base is not persisted to disk until it's closed
     * @return the opened tournament database
     * @throws IOException if something went wrong when creating the database
     */
    public static TournamentBase create(@NonNull File file, boolean createOnClose) throws IOException {
        if (!createOnClose) {
            return new TournamentBase(EntityStorageImpl.create(file, new TournamentBase()));
        } else {
            if (file.exists()) throw new IllegalArgumentException("The file " + file + " already exists");
            TournamentBase tournamentBase = new TournamentBase();
            tournamentBase.addOnCloseHandler(closingBase -> closingBase.duplicate(file));
            return tournamentBase;
        }
    }

    /**
     * Creates a clone of this tournament database on disk
     * @param targetFile the file of the new tournament database to create
     * @return the opened, cloned, tournament database
     * @throws IOException if something went wrong went cloning the database
     */
    @Override
    public TournamentBase duplicate(@NonNull File targetFile) throws IOException {
        return new TournamentBase(getStorage().duplicate(targetFile, new TournamentBase()));
    }

    /**
     * Searches for tournaments using a case sensitive prefix search.
     * The exact year must also be specified as the primary key starts with the year.
     * @param year the exact year of the tournament
     * @param name a prefix of the title of the tournament
     * @return a stream over matching tournaments
     */
    public Stream<TournamentEntity> prefixSearch(int year, @NonNull String name) {
        TournamentEntity startKey = new TournamentEntity(name, new Date(year));
        TournamentEntity endKey = new TournamentEntity(name + "zzz", new Date(year));

        TreePath<TournamentEntity> start = getStorage().lowerBound(startKey);
        TreePath<TournamentEntity> end = getStorage().upperBound(endKey);
        return getStorage().streamOrderedAscending(start, end);
    }

    /**
     * Searches for tournaments in the specified year range
     * @param fromYear the start year, inclusive
     * @param toYear the end year, inclusive
     * @return a stream over matching tournaments
     */
    public Stream<TournamentEntity> rangeSearch(int fromYear, int toYear) {
        // Tournaments are sorted by year in reverse
        TournamentEntity startKey = new TournamentEntity("", new Date(toYear));
        TournamentEntity endKey = new TournamentEntity("", new Date(fromYear-1));

        TreePath<TournamentEntity> start = getStorage().lowerBound(startKey);
        TreePath<TournamentEntity> end = getStorage().upperBound(endKey);
        return getStorage().streamOrderedAscending(start, end);
    }

    /**
     * Gets the underlying raw data for a game header.
     * For debugging purposes only.
     * @param tournamentId the id of the game to get
     * @return a byte array containing the underlying data
     */
    public byte[] getRaw(int tournamentId) {
        EntityNodeStorageBase<TournamentEntity> nodeStorage = getStorage().getNodeStorage();
        if (!(nodeStorage instanceof PersistentEntityNodeStorage)) {
            throw new IllegalStateException("The underlying storage is not a persistent storage");
        }

        return ((PersistentEntityNodeStorage<TournamentEntity>) nodeStorage).getRaw(tournamentId);
    }

    public ByteBuffer serialize(@NonNull TournamentEntity tournament) {
        int typeByte = CBUtil.encodeTournamentType(tournament.getType(), tournament.getTimeControl());

        // The purpose of bit 0 is not yet known
        int optionByte =
                (tournament.isComplete() ? 2 : 0) +
                (tournament.isBoardPoints() ? 4 : 0) +
                (tournament.isThreePointsWin() ? 8 : 0);


        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_TOURNAMENT_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, tournament.getTitle(), 40);
        ByteBufferUtil.putFixedSizeByteString(buf, tournament.getPlace(), 30);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(tournament.getDate()));
        ByteBufferUtil.putByte(buf, typeByte);
        ByteBufferUtil.putByte(buf, tournament.isTeamTournament() ? 1 : 0);
        ByteBufferUtil.putByte(buf, CBUtil.encodeNation(tournament.getNation()));
        ByteBufferUtil.putByte(buf, 0); // Or is nation 2 bytes?
        ByteBufferUtil.putByte(buf, tournament.getCategory());
        ByteBufferUtil.putByte(buf, optionByte);
        ByteBufferUtil.putByte(buf, tournament.getRounds());
        ByteBufferUtil.putByte(buf, 0); // Or is rounds 2 bytes?
        ByteBufferUtil.putIntL(buf, tournament.getCount());
        ByteBufferUtil.putIntL(buf, tournament.getFirstGameId());

        return buf;
    }

    public TournamentEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        byte[] raw = buf.array().clone();

        String title = ByteBufferUtil.getFixedSizeByteString(buf, 40);
        String place = ByteBufferUtil.getFixedSizeByteString(buf, 30);
        TournamentEntity.TournamentEntityBuilder builder = TournamentEntity.builder()
            .id(entityId)
            .raw(raw)
            .title(title)
            .place(place)
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
        builder.count(ByteBufferUtil.getIntL(buf));
        builder.firstGameId(ByteBufferUtil.getIntL(buf));

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

    public int getSerializedEntityLength() {
        return SERIALIZED_TOURNAMENT_SIZE;
    }
}
