package se.yarin.morphy.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.entities.TournamentType;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Contains various utility functions for reading and parsing ChessBase data files.
 */
public final class CBUtil {
    private static final Logger log = LoggerFactory.getLogger(CBUtil.class);

    private CBUtil() { }

    // This is the character set that CB uses
    public static Charset cbCharSet = StandardCharsets.ISO_8859_1;

    public static int compareString(String s1, String s2) {
        // Ordering is done on byte level
        ByteBuffer b1 = cbCharSet.encode(s1 + "\0");
        ByteBuffer b2 = cbCharSet.encode(s2 + "\0");

        return b1.compareTo(b2);
    }

    public static int compareStringUnsigned(String s1, String s2) {
        // Same as compareString but treat each byte as unsigned instead of signed
        ByteBuffer b1 = cbCharSet.encode(s1 + "\0");
        ByteBuffer b2 = cbCharSet.encode(s2 + "\0");

        int thisPos = b1.position();
        int thisRem = b1.limit() - thisPos;
        int thatPos = b2.position();
        int thatRem = b2.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = b1.mismatch(b2);
        if (i >= 0) {
            if (i < thisRem && i < thatRem) {
                // This is the only real difference compared to compareString above
                return Byte.compareUnsigned(b1.get(thisPos + i), b2.get(thatPos + i));
            }
            return thisRem - thatRem;
        }
        return 0;
    }

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
    public static int encodeDate(@NotNull Date date) {
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
    public static int encodeEco(@NotNull Eco eco) {
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

    public static int encodeTournamentType(TournamentType type, TournamentTimeControl timeControl) {
        // bit 0-3: type
        // bit 5: blitz
        // bit 6: rapid
        // bit 7: correspondence
        // But only one of bit 5-7 is actually set
        int typeValue = 0;
        switch (timeControl) {
            case BLITZ: typeValue = 32; break;
            case RAPID: typeValue = 64; break;
            case CORRESPONDENCE: typeValue = 128; break;
        }
        typeValue += type.ordinal();
        return typeValue;
    }

    public static TournamentType decodeTournamentType(int data) {
        if ((data & 31) >= TournamentType.values().length) {
            log.warn("Unknown tournament type: " + (data & 31));
            return TournamentType.NONE;
        }
        return TournamentType.values()[data & 31];
    }

    public static TournamentTimeControl decodeTournamentTimeControl(int data) {
        if ((data & 32) > 0) return TournamentTimeControl.BLITZ;
        if ((data & 64) > 0) return TournamentTimeControl.RAPID;
        if ((data & 128) > 0) return TournamentTimeControl.CORRESPONDENCE;
        return TournamentTimeControl.NORMAL;
    }

    public static Nation decodeNation(int data) {
        // TODO: Should save this value raw instead to make it more future proof
        if (data < 0 || data >= Nation.values().length) {
            return Nation.NONE;
        }
        return Nation.values()[data];
    }

    public static int encodeNation(Nation nation) {
        return nation.ordinal();
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

    public static byte[] fromHexString(String s) {
        s = s.replace(" ", "");
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid length of hex string");
        }
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
        }
        return bytes;
    }

    public static File fileWithExtension(@NotNull File file, @NotNull String extension) {
        if (!extension.startsWith(".")) {
            throw new IllegalArgumentException("Extension should start with a .");
        }
        int extensionStart = file.getPath().lastIndexOf(".");
        if (extensionStart < 0) {
            throw new IllegalArgumentException("The file must have an extension");
        }
        String base = file.getPath().substring(0, extensionStart);
        return new File(base + extension);
    }
}
