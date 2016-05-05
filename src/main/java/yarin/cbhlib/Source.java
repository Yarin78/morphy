package yarin.cbhlib;

import java.nio.ByteBuffer;

public class Source extends DataRecord {
    private String title;
    private String publisher;
    private Date publication;
    private Date sourceDate;
    private int version;
    private int quality;
    private int count;

    public String getTitle() {
        return title;
    }

    public String getPublisher() {
        return publisher;
    }

    public Date getPublication() {
        return publication;
    }

    public Date getSourceDate() {
        return sourceDate;
    }

    public int getVersion() {
        return version;
    }

    public int getQuality() {
        return quality;
    }

    public int getCount() {
        return count;
    }

    public String getQualityString() {
        switch (quality) {
            case 0:
                return "low";
            case 1:
                return "high";
            case 2:
                return "normal";
            case 3:
                return "low";
        }
        return "unknown";
    }

    public String toString() {
        return title;
    }

    /**
     * Internal constructor used when loading a source from a CBH database.
     * @param database The database this source resides in
     * @param sourceId The source number
     * @param cbsData The binary record data
     */
    Source(Database database, int sourceId, ByteBuffer cbsData) {
        super(database, sourceId);
        title = ByteBufferUtil.getZeroTerminatedString(cbsData, 9, 25);
        publisher = ByteBufferUtil.getZeroTerminatedString(cbsData, 34, 16);
        publication = new Date(ByteBufferUtil.getLittleEndian24BitValue(cbsData, 50));
        sourceDate = new Date(ByteBufferUtil.getLittleEndian24BitValue(cbsData, 54));
        version = ByteBufferUtil.getUnsignedByte(cbsData, 58);
        quality = ByteBufferUtil.getUnsignedByte(cbsData, 59);
        count = cbsData.getInt(60);
    }
}
