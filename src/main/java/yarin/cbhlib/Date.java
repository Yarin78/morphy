package yarin.cbhlib;

/**
 * Represents a chess base date, where each of the components year, month and day are optional
 */
public class Date {
    private int year; // 1-4000 or 0 if unset
    private int month; // 1-12 or 0 if unset
    private int day; // 1-31 or 0 if unset

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    /**
     * Creates a new date from a bitstream value
     * @param dateValue the date value. Bit 0-4 is day, bit 5-8 is month, bit 9-20
     */
    Date(int dateValue) {
        setDay(dateValue % 32);
        setMonth((dateValue / 32) % 16);
        setYear(dateValue / 512);
    }

    /**
     * Creates a new date from a unix timestamp
     */
    public Date(int year, int month, int day) {
        setYear(year);
        setMonth(month);
        setDay(day);
    }

    /**
     * Creates a new date where the year, month and day is unset
     */
    public Date() {
        setYear(0);
        setMonth(0);
        setDay(0);
    }

    /**
     * Outputs the date in ChessBase style format.
     *
     * @return this date value in the format DAY.MONTH.YEAR if all components set, otherwise MONTH.YEAR or just YEAR
     */
    public String toString() {
        if (getYear() == 0)
            return "";
        if (getMonth() == 0)
            return String.format("%04d", getYear());
        if (getDay() == 0)
            return String.format("%02d.%04d", getMonth(), getYear());
        return String.format("%02d.%02d.%04d", getDay(), getMonth(), getYear());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Date date = (Date) o;

        if (year != date.year) return false;
        if (month != date.month) return false;
        return day == date.day;

    }

    @Override
    public int hashCode() {
        int result = year;
        result = 31 * result + month;
        result = 31 * result + day;
        return result;
    }
}
