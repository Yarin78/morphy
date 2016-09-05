package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.EnumSet;

/**
 * Contains various utility functions for reading and parsing ChessBase data files.
 */
public final class CBUtil {
    private static final Logger log = LoggerFactory.getLogger(CBUtil.class);

    private CBUtil() { }

    // This is the character set that CB uses
    static Charset cbCharSet = Charset.forName("ISO-8859-1");

    /**
     * Decodes a 21 bit CBH encoded date to a {@link Date}.
     * @param dateValue an integer containing an encoded date value
     * @return the decoded date
     */
    public static Date decodeDate(int dateValue) {
        // Bit 0-4 is day, bit 5-8 is month, bit 9-20 is year
        dateValue %= (1<<21);
        int day = dateValue % 32;
        int month = (dateValue / 32) % 16;
        int year = dateValue / 512;
        return new Date(year, month, day);
    }

    /**
     * Converts a {@link Date} to a 21 bit CBH encoded date.
     * @param date the date to encode
     * @return the encoded date
     */
    public static int encodeDate(@NonNull Date date) {
        return (date.year() * 512 + date.month() * 32 + date.day()) % (1<<21);
    }

    /**
     * Decodes a CBH encoded Eco code to a {@link Eco}
     * @param ecoValue an integer containing an encoded Eco value
     * @return the decoded Eco
     */
    public static Eco decodeEco(int ecoValue) {
        int eco = ecoValue / 128 - 1;
        int subEco = ecoValue % 128;
        return eco < 0 ? Eco.unset() : Eco.fromInt(eco, subEco);
    }

    /**
     * Converts a {@link Eco} to a CBH encoded Eco.
     * @param eco the Eco to encode
     * @return the encoded Eco
     */
    public static int encodeEco(@NonNull Eco eco) {
        if (!eco.isSet()) {
            return 0;
        }
        return (eco.getInt() + 1) * 128 + eco.getSubEco();
    }

    public static GameResult decodeGameResult(int data) {
        return GameResult.values()[data];
    }

    public static int encodeGameResult(GameResult data) {
        return data.ordinal();
    }

    public static EnumSet<Medal> decodeMedals(int data) {
        EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
        for (Medal medal : Medal.values()) {
            if (((1<<medal.ordinal()) & data) > 0) {
                medals.add(medal);
            }
        }
        return medals;
    }

    public static int encodeMedals(EnumSet<Medal> medals) {
        int value = 0;
        for (Medal medal : medals) {
            value += (1 << medal.ordinal());
        }
        return value;
    }

    // Debug code

    public static String toHexString(ByteBuffer buf) {
        int oldPos = buf.position();
        byte[] bytes = new byte[buf.limit() - oldPos];
        buf.get(bytes);
        buf.position(oldPos);
        return toHexString(bytes);
    }

    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i];
            if (v < 0) v += 256;
            sb.append(String.format("%02X ", v));
        }
        return sb.toString();

    }
}
