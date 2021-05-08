package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

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
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getCategoryRoman();
    }

    @Override
    public String getId() {
        return "tournament-category";
    }
}
