package yarin.cbhlib;

import java.nio.ByteBuffer;

public class Annotator extends DataRecord {
    private String name;
    private int count;

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public String toString() {
        return name;
    }

    /**
     * Internal constructor used when loading an annotator from a CBH database.
     * @param database The database this annotator resides in
     * @param annotatorId The annotator number
     * @param cbcData The binary record data
     */
    Annotator(Database database, int annotatorId, ByteBuffer cbcData) {
        super(database, annotatorId);
        name = ByteBufferUtil.getZeroTerminatedString(cbcData, 9, 45);
        count = cbcData.getInt(54);
    }
}