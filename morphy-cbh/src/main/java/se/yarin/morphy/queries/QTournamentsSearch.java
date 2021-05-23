package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.TournamentTitleFilter;
import se.yarin.morphy.entities.filters.TournamentYearTitleFilter;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public class QTournamentsSearch extends ItemQuery<Tournament> {
    private final @NotNull EntityFilter<Tournament> filter;

    public QTournamentsSearch(@NotNull String name, boolean caseSensitive, boolean exactMatch) {
        int year = 0;
        if (Pattern.matches("^[0-9]{4}.*", name)) {
            year = Integer.parseInt(name.substring(0, 4));
            name = name.substring(4).strip();
        } else if (Pattern.matches(".*[0-9]{4}$", name)) {
            year = Integer.parseInt(name.substring(name.length() - 4));
            name = name.substring(0, name.length() - 4).strip();
        }

        if (year > 0) {
            filter = new TournamentYearTitleFilter(year, name, caseSensitive, exactMatch);
        } else {
            filter = new TournamentTitleFilter(name, caseSensitive, exactMatch);
        }
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
        return filter.matches(tournament);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive and we have year, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.tournamentTransaction().stream().filter(filter::matches);
    }
}
