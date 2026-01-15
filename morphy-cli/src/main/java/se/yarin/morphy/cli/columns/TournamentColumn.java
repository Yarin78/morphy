package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

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

  default boolean trimValueToWidth() {
    return true;
  }

  String getTournamentValue(Database db, Tournament tournament);

  String getTournamentId();

  TournamentColumn[] ALL = {
    new TournamentIdColumn(),
    new TournamentTitleColumn(),
    new TournamentPlaceColumn(),
    new TournamentYearColumn(),
    new TournamentDateColumn(),
    new TournamentTypeColumn(),
    new TournamentNationColumn(),
    new TournamentCategoryColumn(),
    new TournamentRoundsColumn(),
    new TournamentNumGamesColumn(),
    new TournamentFirstGameColumn(),
    new TournamentCompleteColumn(),
    new TournamentTimeControlColumn(),
    new DatabaseColumn()
  };

  static String allColumnsString() {
    return Arrays.stream(ALL)
        .map(TournamentColumn::getTournamentId)
        .collect(Collectors.joining(", "));
  }
}
