package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.chess.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains various utility functions for reading and parsing ChessBase data files.
 */
public final class CBUtil {
    private CBUtil() { }

    // This is the character set that CB uses
    static Charset cbCharSet = Charset.forName("ISO-8859-1");

    static Map<Symbol, Byte> symbolToByte = new HashMap<>();
    static Map<Byte, Symbol> byteToSymbol = new HashMap<>();

    static {
        // TODO: Put in array instead?
//        byteToSymbol.put((byte) 0x00, LineEvaluation.NO_EVALUATION);
        byteToSymbol.put((byte) 0x0B, LineEvaluation.EQUAL);
        byteToSymbol.put((byte) 0x0D, LineEvaluation.UNCLEAR);
        byteToSymbol.put((byte) 0x0E, LineEvaluation.WHITE_SLIGHT_ADVANTAGE);
        byteToSymbol.put((byte) 0x0F, LineEvaluation.BLACK_SLIGHT_ADVANTAGE);
        byteToSymbol.put((byte) 0x10, LineEvaluation.WHITE_CLEAR_ADVANTAGE);
        byteToSymbol.put((byte) 0x11, LineEvaluation.BLACK_CLEAR_ADVANTAGE);
        byteToSymbol.put((byte) 0x12, LineEvaluation.WHITE_DECISIVE_ADVANTAGE);
        byteToSymbol.put((byte) 0x13, LineEvaluation.BLACK_DECISIVE_ADVANTAGE);
        byteToSymbol.put((byte) 0x20, LineEvaluation.DEVELOPMENT_ADVANTAGE);
        byteToSymbol.put((byte) 0x24, LineEvaluation.WITH_INITIATIVE);
        byteToSymbol.put((byte) 0x28, LineEvaluation.WITH_ATTACK);
        byteToSymbol.put((byte) 0x2C, LineEvaluation.WITH_COMPENSATION);
        byteToSymbol.put((byte) 0x84, LineEvaluation.WITH_COUNTERPLAY);
        byteToSymbol.put((byte) 0x8A, LineEvaluation.ZEITNOT);
        byteToSymbol.put((byte) 0x92, LineEvaluation.THEORETICAL_NOVELTY);

//        byteToSymbol.put((byte) 0x00, MovePrefix.NOTHING);
        byteToSymbol.put((byte) 0x8C, MovePrefix.WITH_THE_IDEA);
        byteToSymbol.put((byte) 0x8D, MovePrefix.DIRECTED_AGAINST);
        byteToSymbol.put((byte) 0x8E, MovePrefix.BETTER_IS);
        byteToSymbol.put((byte) 0x8F, MovePrefix.WORSE_IS);
        byteToSymbol.put((byte) 0x90, MovePrefix.EQUIVALENT_IS);
        byteToSymbol.put((byte) 0x91, MovePrefix.EDITORIAL_ANNOTATION);

//        byteToSymbol.put((byte) 0x00, MoveComment.NOTHING);
        byteToSymbol.put((byte) 0x01, MoveComment.GOOD_MOVE);
        byteToSymbol.put((byte) 0x02, MoveComment.BAD_MOVE);
        byteToSymbol.put((byte) 0x03, MoveComment.EXCELLENT_MOVE);
        byteToSymbol.put((byte) 0x04, MoveComment.BLUNDER);
        byteToSymbol.put((byte) 0x05, MoveComment.INTERESTING_MOVE);
        byteToSymbol.put((byte) 0x06, MoveComment.DUBIOUS_MOVE);
        byteToSymbol.put((byte) 0x08, MoveComment.ONLY_MOVE);
        byteToSymbol.put((byte) 0x16, MoveComment.ZUGZWANG);
        byteToSymbol.put((byte) 0x18, MoveComment.ZUGZWANG); // Seem to be two different codings for zugzwang

        for (Map.Entry<Byte, Symbol> entry : byteToSymbol.entrySet()) {
            symbolToByte.put(entry.getValue(), entry.getKey());
        }
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
    public static int encodeDate(@NonNull Date date) {
        return (date.year() * 512 + date.month() * 32 + date.day()) % (1<<21);
    }

    /**
     * Decodes a CBH encoded Eco code to a {@link Eco}
     * @param ecoValue an integer containing an encoded Eco value
     * @return the decoded Eco
     */
    public static Eco decodeEco(int ecoValue) {
        // TODO: This will ignore the sub eco as Eco doesn't support it yet
        int eco = ecoValue / 128 - 1;
        int subEco = ecoValue % 128;
        return eco < 0 ? Eco.unset() : Eco.fromInt(eco);
    }

    /**
     * Converts a {@link Eco} to a CBH encoded Eco.
     * @param eco the Eco to encode
     * @return the encoded Eco
     */
    public static int encodeEco(Eco eco) {
        // TODO: Add support for sub eco when Eco contains that data
        if (!eco.isSet()) {
            return 0;
        }
        return (eco.getInt() + 1) * 128;
    }

    /**
     * Converts a {@link Symbol} to a byte using the CBH encoding
     * @param symbol the symbol to encode
     * @return a byte representing the symbol, or 0 if an unknown symbol
     */
    public static byte encodeSymbol(@NonNull Symbol symbol) {
        Byte b = symbolToByte.get(symbol);
        return b == null ? 0 : b;
    }

    /**
     * Converts a byte to a {@link Symbol} using the CBH encoding
     * @param data the byte to convert
     * @return a symbol, or null if data is 0 or no symbol matches the byte
     */
    public static Symbol decodeSymbol(byte data) {
        // If data is 0, we can't determine what kind of symbol we are decoding,
        // so we have to return null instead.
        // Maybe it should be a common enum type for all symbols?
        return byteToSymbol.get(data);
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
