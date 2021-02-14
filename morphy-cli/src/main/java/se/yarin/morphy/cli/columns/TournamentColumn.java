package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

import java.util.Arrays;
import java.util.stream.Collectors;

public interface TournamentColumn {
    String getHeader();

    default int width() {
        return getHeader().length();
    }

    default int marginLeft() {
        return 1;
    }

    default int marginRight() {
        return 1;
    }

    default boolean trimValueToWidth() { return true; }

    String getTournamentValue(Database db, TournamentEntity game);

    String getTournamentId();

    TournamentColumn[] ALL = {
            new TournamentIdColumn(),
            new TournamentYearColumn(),
            new TournamentDateColumn(),
            new TournamentTitleColumn(),
            new TournamentPlaceColumn(),
            new TournamentNationColumn(),
            new TournamentTypeColumn(),
            new TournamentTimeControlColumn(),
            new TournamentCategoryColumn(),
            new TournamentRoundsColumn()
    };

    static String allColumnsString() {
        return Arrays.stream(ALL).map(TournamentColumn::getTournamentId).collect(Collectors.joining(", "));
    }
}
