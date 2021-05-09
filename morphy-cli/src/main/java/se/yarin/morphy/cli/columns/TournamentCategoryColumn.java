package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentCategoryColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Cat";
    }

    @Override
    public int width() {
        return 7;
    }

    @Override
    public String getTournamentValue(Database database, Tournament tournament) {
        return tournament.getCategoryRoman();
    }

    @Override
    public String getId() {
        return "tournament-category";
    }
}
