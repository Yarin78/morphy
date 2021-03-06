package se.yarin.morphy.entities;

import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;
import se.yarin.morphy.storage.ItemStorageSerializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TournamentExtraSerializer implements ItemStorageSerializer<TournamentExtraHeader, TournamentExtra> {
    @Override
    public int expectedHeaderSize() {
        return 32;
    }

    @Override
    public long itemOffset(TournamentExtraHeader header, int index) {
        return expectedHeaderSize() + index * (long) itemSize(header);
    }

    @Override
    public int itemSize(TournamentExtraHeader header) {
        return header.recordSize();
    }

    @Override
    public TournamentExtraHeader deserializeHeader(ByteBuffer buf) {
        int version = ByteBufferUtil.getIntL(buf);
        int recordSize = ByteBufferUtil.getIntL(buf);
        int numTournaments = ByteBufferUtil.getIntL(buf);
        buf.position(buf.position() + 20);

        return ImmutableTournamentExtraHeader.builder()
                .version(version)
                .recordSize(recordSize)
                .numTournaments(numTournaments)
                .build();
    }

    @Override
    public void serializeHeader(TournamentExtraHeader header, ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, header.version());
        ByteBufferUtil.putIntL(buf, header.recordSize());
        ByteBufferUtil.putIntL(buf, header.numTournaments());
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
    }

    @Override
    public TournamentExtra deserializeItem(int id, ByteBuffer buf) {
        double latitude = ByteBufferUtil.getDoubleL(buf);
        double longitude = ByteBufferUtil.getDoubleL(buf);
        buf.position(buf.position() + 34);

        ArrayList<TiebreakRule> rules = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rules.add(TiebreakRule.fromId(ByteBufferUtil.getUnsignedByte(buf)));
        }
        int numRules = ByteBufferUtil.getUnsignedByte(buf);
        Date endDate = CBUtil.decodeDate(ByteBufferUtil.getIntL(buf));

        return ImmutableTournamentExtra.builder()
                .latitude(latitude)
                .longitude(longitude)
                .tiebreakRules(rules.subList(0, numRules))
                .endDate(endDate)
                .build();
    }

    @Override
    public void serializeItem(TournamentExtra tournamentExtra, ByteBuffer buf) {
        ByteBufferUtil.putDoubleL(buf, tournamentExtra.latitude());
        ByteBufferUtil.putDoubleL(buf, tournamentExtra.longitude());

        // 34 bytes with unknown purpose, but every 11th byte is 7
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 10; i++) {
                ByteBufferUtil.putByte(buf, 0);
            }
            ByteBufferUtil.putByte(buf, 7);
        }
        ByteBufferUtil.putByte(buf, 0);

        for (TiebreakRule tiebreakRule : tournamentExtra.tiebreakRules()) {
            ByteBufferUtil.putByte(buf, tiebreakRule.id());
        }
        for (int i = 0; i < 10 - tournamentExtra.tiebreakRules().size(); i++) {
            ByteBufferUtil.putByte(buf, 0);
        }
        ByteBufferUtil.putByte(buf, tournamentExtra.tiebreakRules().size());
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(tournamentExtra.endDate()));
    }
}
