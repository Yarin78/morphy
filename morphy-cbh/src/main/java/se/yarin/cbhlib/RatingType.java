package se.yarin.cbhlib;

import lombok.Data;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;

import java.nio.ByteBuffer;

@Data
public class RatingType {
    private final TournamentTimeControl international;
    private final TournamentTimeControl national;
    private final Nation nation;
    private final String name;

    public boolean isInternational() {
        return international != null;
    }

    public boolean isNational() {
        return national != null;
    }

    public static RatingType international(TournamentTimeControl timeControl) {
        String name = timeControl == TournamentTimeControl.CORRESPONDENCE ? "ICCF" : "FIDE";
        return new RatingType(timeControl, null, null, name);
    }

    public static RatingType national(TournamentTimeControl timeControl, Nation nation) {
        return new RatingType(null, timeControl, nation, null);
    }

    public void serialize(ByteBuffer buf) {
        // TODO: Verify that it's enough to set this data for ChessBase
        // or if the values in the comments below are necessary
        buf.put((byte) 0);
        buf.put((byte) (isInternational() ? 1 : 2));
        buf.put((byte) (national == null ? 0 : national.ordinal()));
        buf.put((byte) (international == null ? 0 : (international.ordinal() + 1)));
        buf.put((byte) CBUtil.encodeNation(nation));
        ByteBufferUtil.putFixedSizeByteString(buf, name == null ? "" : name, 11);
    }

    public static RatingType deserialize(ByteBuffer buf) {
        byte b = buf.get();
        int type = buf.get() & 7;
        int nationalTC = buf.get();
        int internationalTC = buf.get() - 1;
        Nation nation = CBUtil.decodeNation(ByteBufferUtil.getUnsignedByte(buf));
        String name = ByteBufferUtil.getFixedSizeByteString(buf, 11);

        // Some checks to avoid setting wrong values in case of trash data
        if (b == 0 && type == 1 && nation == Nation.NONE) {
            if (internationalTC >= 0 && internationalTC < TournamentTimeControl.values().length) {
                TournamentTimeControl timeControl = TournamentTimeControl.values()[internationalTC];
                return new RatingType(timeControl, null, Nation.NONE, name);
            }
        }
        if (b == 0 && type == 2 && nation != Nation.NONE) {
            if (nationalTC >= 0 && nationalTC < TournamentTimeControl.values().length) {
                TournamentTimeControl timeControl = TournamentTimeControl.values()[nationalTC];
                return new RatingType(null, timeControl, nation, name);
            }
        }

        return RatingType.international(TournamentTimeControl.NORMAL);
    }

    @Override
    public String toString() {
        if (isInternational()) {
            return "International " + international.getName() + " " + name;
        } else {
            return "National " + national.getName() + " " + nation.getName() + " " + name;
        }
    }
}


/*

byte 0:
   01 => International Elo Normal
   11 => International Elo Blitz
   19 => International Elo Rapid
   21 => International ICCF Corresp Chess
   02 => National Normal
   1a => National Blitz
   12 => National Rapid
   22 => National Corresp Chess
byte 1:
   00 => International OR "National Normal"
   01 => National Blitz
   02 => National Rapid
   03 => National Correspondence Chess
byte 2:
   01 => International Elo Normal
   02 => International Elo Blitz
   03 => International Elo Rapid
   04 => International ICCF Corresp Chess
   64 => National Normal
   90 => National Blitz
   bc => National Rapid
   e8 => National Corresp Chess
byte 3: Country code for national ratings (
   00 => International
   rest => Same number system as Nations
byte 4-7: Name of rating
   00 00 00 00  => National
   46 49 44 45  => International ELO ("FIDE")
   49 43 43 46  => International Corresp Ch ("ICCF")

 */