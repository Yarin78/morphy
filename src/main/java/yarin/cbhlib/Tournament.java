package yarin.cbhlib;

import java.nio.ByteBuffer;

public class Tournament extends DataRecord {
    private static String[] nations = new String[]{};
    private String title;
    private String place;
    private Date date;
    private int category;
    private boolean complete;
    private boolean boardPoints;
    private boolean threePointsWin;
    private boolean teamTournament;
    private int noRounds;
    private int nation;
    private int count;
    private TournamentType type;
    private TournamentTimeControls timeControl;

    public String getTitle() {
        return title;
    }

    public String getPlace()
    {
        return place;
    }

    public Date getTournamentDate()
    {
        return date;
    }

    public int getCategory()
    {
        return category;
    }

    public boolean isComplete()
    {
        return complete;
    }

    public boolean isBoardPoints()
    {
        return boardPoints;
    }

    public boolean isThreePointsWin() {
        return threePointsWin;
    }

    public boolean isTeamTournament() {
        return teamTournament;
    }

    public int getNoRounds()
    {
        return noRounds;
    }

    public TournamentType getType()
    {
        return type;
    }

    public TournamentTimeControls getTimeControl()
    {
        return timeControl;
    }

    public int getCount() {
        return count;
    }

    // TODO: Map out the nations
    /*
      96 = MNC
      94 = MEX
      21 = BIH
      149 = URS
      49 = FRA
      53 = GER
     */
    public String getNationString()
    {
        return nation == 0 ? "" : ("#" + Integer.toString(nation));
    }

    public String getNoRoundsString()
    {
        return noRounds == 0 ? "" : Integer.toString(noRounds);
    }

    public String getCategoryString()
    {
        if (getCategory() == 0)
            return "";
        return ByteBufferUtil.getRomanNumber(getCategory());
    }

    public String getTournamentTypeString()
    {
        String s = "";
        if (timeControl.contains(TournamentTimeControls.TournamentTimeControl.Blitz))
            s += "blitz";
        if (timeControl.contains(TournamentTimeControls.TournamentTimeControl.Rapid)) {
            if (s.length() > 0)
                s += ", ";
            s += "rapid";
        }
        if (timeControl.contains(TournamentTimeControls.TournamentTimeControl.Corresp)) {
            if (s.length() > 0)
                s += ", ";
            s += "corr";
        }
        if (getType() == TournamentType.Undefined)
            return s;
        if (s.length() == 0)
            return getType().toString();
        return getType().toString().toLowerCase() + " (" + s + ")";
    }

    public String toString() {
        return title;
    }

    /**
     * Internal constructor used when loading a tournament from a CBH database.
     * @param database the database this tournament resides in
     * @param tournamentId the tournament number
     * @param cbtData the binary record data
     */
    Tournament(Database database, int tournamentId, ByteBuffer cbtData) {
        super(database, tournamentId);
        title = ByteBufferUtil.getZeroTerminatedString(cbtData, 9, 40);
        place = ByteBufferUtil.getZeroTerminatedString(cbtData, 49, 30);
        date = new Date(ByteBufferUtil.getLittleEndian24BitValue(cbtData, 79));
        type = TournamentType.values()[cbtData.get(83) & 31];
        timeControl = new TournamentTimeControls();
        if ((cbtData.get(83) & 0x20) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
        if ((cbtData.get(83) & 0x40) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
        if ((cbtData.get(83) & 0x80) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);
        teamTournament = (cbtData.get(84) & 1) > 0;
        nation = ByteBufferUtil.getUnsignedByte(cbtData, 85);
        category = cbtData.get(87);
        //if ((cbtData.get(88) & 3) != 0 && (cbtData.get(88) & 3) != 3)
        //throw new CBHFormatException("Tournament complete flag has invalid value");
        complete = (cbtData.get(88) & 2) > 0;
        boardPoints = (cbtData.get(88) & 4) == 4;
        threePointsWin = (cbtData.get(88) & 8) > 0;
        noRounds = cbtData.get(89);
        count = cbtData.getInt(91);
    }
}
