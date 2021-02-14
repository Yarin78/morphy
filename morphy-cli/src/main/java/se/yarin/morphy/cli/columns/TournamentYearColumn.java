package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentYearColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Year";
    }

    @Override
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        int year = tournament.getDate().year();
        return year == 0 ? "????" : String.format("%4d", year);
    }

    @Override
    public String getId() {
        return "tournament-year";
    }
}
