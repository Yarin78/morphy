package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHFormatException;

import java.nio.ByteBuffer;

public class RatingDetails {
    public enum RatingType { Normal, Blitz, Rapid, Corr };

    private boolean international;
    private RatingType type;
    private int country; // TODO: Create map between integer and country

    public RatingDetails() {
        this(true, RatingDetails.RatingType.Normal, 0);
    }

    public RatingDetails(boolean international, RatingType type, int country) {
        this.international = international;
        this.type = type;
        this.country = country;
    }

    public boolean isInternational() {
        return international;
    }

    public RatingType getType() {
        return type;
    }

    public int getCountry() {
        return country;
    }

    RatingDetails(ByteBuffer buffer, int pos) throws CBHFormatException {
        int c = ByteBufferUtil.getUnsignedByte(buffer, pos + 2);
        if (c >= 1 && c <= 4) {
            this.international = true;
            this.type = RatingType.values()[c - 1];
        } else {
            this.international = false;
            this.country = ByteBufferUtil.getUnsignedByte(buffer, pos + 3);
            switch (c) {
                case 0x64 :
                    this.type = RatingType.Normal;
                    break;
                case 0x90 :
                    this.type = RatingType.Blitz;
                    break;
                case 0xBC :
                    this.type = RatingType.Rapid;
                    break;
                case 0xE8 :
                    this.type = RatingType.Corr;
                    break;
                default :
                    throw new CBHFormatException(String.format("Unknown rating type: %02X", c));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RatingDetails that = (RatingDetails) o;

        if (international != that.international) return false;
        if (country != that.country) return false;
        return type == that.type;

    }

    @Override
    public int hashCode() {
        int result = (international ? 1 : 0);
        result = 31 * result + type.hashCode();
        result = 31 * result + country;
        return result;
    }

    @Override
    public String toString() {
        return "RatingDetails{" +
                "international=" + international +
                ", type=" + type +
                ", country=" + country +
                '}';
    }
}
