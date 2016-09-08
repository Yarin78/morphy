package se.yarin.chess;

import java.util.Calendar;

/**
 * Represents a date where each of the year, month and day parts are optional.
 *
 * Date is immutable.
 */
public final class Date {
    private int year, month, day;

    public Date(int year) {
        this(year, 0, 0);
    }

    public Date(int year, int month) {
        this(year, month, 0);
    }

    public Date(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public static Date unset() {
        return new Date(0, 0, 0);
    }

    public static Date today() {
        Calendar cal = Calendar.getInstance();
        return new Date(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public int day() {
        return day;
    }

    @Override
    public String toString() {
        // This is the PGN format
        StringBuilder sb = new StringBuilder();

        if (year == 0) {
            sb.append("????");
        } else {
            sb.append(String.format("%04d", year));
        }
        sb.append('.');

        if (month == 0) {
            sb.append("??");
        } else {
            sb.append(String.format("%02d", month));
        }
        sb.append('.');

        if (day == 0) {
            sb.append("??");
        } else {
            sb.append(String.format("%02d", day));
        }

        return sb.toString();
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
