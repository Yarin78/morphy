package yarin.cbhlib;

import java.nio.ByteBuffer;

public class Team extends DataRecord {
    private String title;
    private int teamNumber;
    private boolean season;
    private int year;
    private int nation; // TODO: Make class
    private int count;

    public String getTitle() {
        return title;
    }

    public int getTeamNumber() {
        return teamNumber;
    }

    public boolean isSeason() {
        return season;
    }

    public int getYear() {
        return year;
    }

    public int getNation() {
        return nation;
    }

    public int getCount() {
        return count;
    }

    public String getRank()
    {
        if (teamNumber == 0)
            return "";
        return ByteBufferUtil.getRomanNumber(teamNumber);
    }

    public String getYearString()
    {
            if (season)
                return String.format("%02d / %02", year % 100, (year + 1) % 100);
            return Integer.toString(year);
    }

    public String getNationString()
    {
        return nation == 0 ? "" : ("#" + Integer.toString(nation));
    }

    public String toString() {
        if (teamNumber != 0)
            return title + " " + getRank();
        return title;
    }

    /**
     * Internal constructor used when loading a team from a CBH database.
     * @param database The database this team resides in
     * @param teamId The team number
     * @param cbeData The binary record data
     */
    Team(Database database, int teamId, ByteBuffer cbeData) {
        super(database, teamId);
        title = ByteBufferUtil.getZeroTerminatedString(cbeData, 9, 45);
        teamNumber = cbeData.getShort(54);
        season = (cbeData.get(58) & 1) > 0;
        year = cbeData.getShort(59);
        nation = cbeData.get(63);
        count =  cbeData.getInt(64);
    }
}
