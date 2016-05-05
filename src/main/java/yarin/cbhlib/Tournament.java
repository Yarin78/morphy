package yarin.cbhlib;

import java.nio.ByteBuffer;

public class Tournament extends DataRecord {
    private static String[] _nations = new String[]{};
    private String title;
    private String place;
    private Date date;
    private int category;
    private boolean complete;
    private boolean boardPoints;
    private int noRounds;
    private int nation;
    private int count;
    private TournamentType _type;
    private TournamentTimeControls _timeControl;

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

    public int getNoRounds()
    {
        return noRounds;
    }

    public TournamentType getType()
    {
        return _type;
    }

    public TournamentTimeControls getTimeControl()
    {
        return _timeControl;
    }

    public int getCount() {
        return count;
    }

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
        if (_timeControl.contains(TournamentTimeControls.TournamentTimeControl.Blitz))
            s += "blitz";
        if (_timeControl.contains(TournamentTimeControls.TournamentTimeControl.Rapid)) {
            if (s.length() > 0)
                s += ", ";
            s += "rapid";
        }
        if (_timeControl.contains(TournamentTimeControls.TournamentTimeControl.Corresp)) {
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
        _type = TournamentType.values()[cbtData.get(83) & 31];
        _timeControl = new TournamentTimeControls();
        if ((cbtData.get(83) & 0x20) > 0)
            _timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
        if ((cbtData.get(83) & 0x40) > 0)
            _timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
        if ((cbtData.get(83) & 0x80) > 0)
            _timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);
        nation = cbtData.get(85);
        category = cbtData.get(87);
        //if ((cbtData.get(88) & 3) != 0 && (cbtData.get(88) & 3) != 3)
        //throw new CBHFormatException("yarin.cbhlib.Tournament complete flag has invalid value");
        complete = (cbtData.get(88) & 2) > 0;
        boardPoints = (cbtData.get(88) & 4) == 4;
        noRounds = cbtData.get(89);
        count = cbtData.getInt(91);
    }
}
