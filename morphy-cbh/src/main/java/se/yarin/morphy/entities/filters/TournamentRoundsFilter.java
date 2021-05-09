package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TournamentRoundsFilter implements ItemStorageFilter<Tournament> {
    private final int minRounds;
    private final int maxRounds;

    public TournamentRoundsFilter(@NotNull String rounds) {
        Pattern pattern = Pattern.compile("^([0-9]+)?-([0-9]+)?$");
        Matcher matcher = pattern.matcher(rounds);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid round range specified: " + rounds);
        }
        this.minRounds = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
        this.maxRounds = matcher.group(2) == null ? 9999 : Integer.parseInt(matcher.group(2));
    }

    public TournamentRoundsFilter(int minRounds, int maxRounds) {
        this.minRounds = minRounds;
        this.maxRounds = maxRounds;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return tournament.rounds() >= minRounds && tournament.rounds() <= maxRounds;
    }
}
