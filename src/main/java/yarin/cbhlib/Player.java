package yarin.cbhlib;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class Player extends DataRecord {
    private String lastName;
    private String firstName;
    private int count;

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public int getCount() {
        return count;
    }

    public String toString() {
        if (firstName.length() == 0)
            return lastName;
        return lastName + ", " + firstName.charAt(0);
    }

    /**
     * Internal constructor used when loading a player from a CBH database.
     * @param database The database this player resides in
     * @param playerId The player number
     * @param cbpData The binary record data
     */
    Player(Database database, int playerId, ByteBuffer cbpData) {
        super(database, playerId);
//        Charset charset = Charset.forName("ISO-8859-1");
//        CharsetDecoder decoder = charset.newDecoder();


        lastName = ByteBufferUtil.getZeroTerminatedString(cbpData, 9, 30);
        firstName = ByteBufferUtil.getZeroTerminatedString(cbpData, 39, 20);
        count = cbpData.getInt(59);
    }
}