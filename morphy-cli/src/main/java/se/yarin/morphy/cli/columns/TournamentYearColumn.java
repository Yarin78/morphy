package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentYearColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Year";
    }

    @Override
    public String getTournamentValue(Database database, Tournament tournament) {
        int year = tournament.date().year();
        return year == 0 ? "????" : String.format("%4d", year);
    }

    @Override
    public String getId() {
        return "tournament-year";
    }
}
