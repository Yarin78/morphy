package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

import java.util.Arrays;
import java.util.stream.Collectors;

public interface GameColumn {

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

    String getValue(Database database, GameHeader header, GameModel game);

    String getId();

    // This also determines in what order the columns will be shown
    GameColumn[] ALL = {
            new GameIdColumn(),
            new NameColumn(true),
            new RatingColumn(true),
            new NameColumn(false),
            new RatingColumn(false),
            new ResultsColumn(),
            new NumMovesColumn(),
            new VCSColumn(),
            new EcoColumn(),
            new TournamentTitleColumn(),
            new TournamentPlaceColumn(),
            new TournamentNationColumn(),
            new TournamentCategoryColumn(),
            new TournamentRoundsColumn(),
            new TournamentTimeControlColumn(),
            new TournamentTypeColumn(),
            new TournamentDateColumn(),
            new RoundColumn(),
            new DateColumn(),
            new AnnotatorColumn(),
            new SourceColumn(),
    };

    static String allColumnsString() {
        return Arrays.stream(ALL).map(GameColumn::getId).collect(Collectors.joining(", "));
    }
}