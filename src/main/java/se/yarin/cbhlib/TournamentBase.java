package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.EntityStorage;
import se.yarin.cbhlib.entities.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TournamentBase extends EntityBase<TournamentEntity> {

    private static final Logger log = LoggerFactory.getLogger(TournamentBase.class);

    private int SERIALIZED_TOURNAMENT_SIZE = 90;

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
        return new TournamentBase(EntityStorageImpl.create(file, new TournamentBase()));
    }

    public ByteBuffer serialize(@NonNull TournamentEntity tournament) {
        int typeByte = tournament.getType().ordinal();
        switch (tournament.getTimeControl()) {
            case BLITZ:           typeByte += 32; break;
            case RAPID:           typeByte += 64; break;
            case CORRESPONDENCE:  typeByte += 128; break;
        }

        // TODO: The purpose of bit 0 is not yet known
        int optionByte =
                (tournament.isComplete() ? 2 : 0) +
                (tournament.isBoardPoints() ? 4 : 0) +
                (tournament.isThreePointsWin() ? 8 : 0);


        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_TOURNAMENT_SIZE);
        ByteBufferUtil.putByteString(buf, tournament.getTitle(), 40);
        ByteBufferUtil.putByteString(buf, tournament.getPlace(), 30);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(tournament.getDate()));
        ByteBufferUtil.putByte(buf, typeByte);
        ByteBufferUtil.putByte(buf, tournament.isTeamTournament() ? 1 : 0);
        ByteBufferUtil.putByte(buf, tournament.getNation().ordinal());
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
        String title = ByteBufferUtil.getFixedSizeByteString(buf, 40);
        TournamentEntity tournament = new TournamentEntity(entityId, title);

        tournament.setPlace(ByteBufferUtil.getFixedSizeByteString(buf, 30));
        tournament.setDate(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));
        int typeByte = ByteBufferUtil.getUnsignedByte(buf);
        tournament.setTeamTournament((ByteBufferUtil.getUnsignedByte(buf) & 1) == 1);
        tournament.setNation(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)]);
        int unknownByte1 = ByteBufferUtil.getUnsignedByte(buf);
        tournament.setCategory(ByteBufferUtil.getUnsignedByte(buf));
        int optionByte = ByteBufferUtil.getUnsignedByte(buf);
        tournament.setRounds(ByteBufferUtil.getUnsignedByte(buf));
        int unknownByte2 = ByteBufferUtil.getUnsignedByte(buf);
        tournament.setCount(ByteBufferUtil.getIntL(buf));
        tournament.setFirstGameId(ByteBufferUtil.getIntL(buf));

        tournament.setType(TournamentType.values()[typeByte & 31]);
        // Only one of these bits ought to be set
        tournament.setTimeControl(TournamentTimeControl.NORMAL);
        if ((typeByte & 32) > 0) tournament.setTimeControl(TournamentTimeControl.BLITZ);
        if ((typeByte & 64) > 0) tournament.setTimeControl(TournamentTimeControl.RAPID);
        if ((typeByte & 128) > 0) tournament.setTimeControl(TournamentTimeControl.CORRESPONDENCE);

        tournament.setComplete((optionByte & 2) > 0);
        tournament.setBoardPoints((optionByte & 4) > 0);
        tournament.setThreePointsWin((optionByte & 8) > 0);

        if (unknownByte1 != 0) {
            log.debug("unknownByte1 = " + unknownByte1 + " when deserializing tournament with id " + entityId);
        }
        if (unknownByte2 != 0) {
            log.debug("unknownByte2 = " + unknownByte2 + " when deserializing tournament with id " + entityId);
        }
        if ((optionByte & ~14) != 0) {
            log.debug("optionByte = " + optionByte + " when deserializing tournament with id " + entityId);
        }

        return tournament;
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_TOURNAMENT_SIZE;
    }
}
