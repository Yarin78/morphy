package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class TournamentYearTitleFilter implements EntityFilter<Tournament>  {
    private final int year;
    @NotNull private final String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentYearTitleFilter(int year, @NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.year = year;
        this.title = title;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matchesTitle(@NotNull String tournamentTitle) {
        if (exactMatch) {
            return caseSensitive ? tournamentTitle.equals(title) : tournamentTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? tournamentTitle.startsWith(title) : tournamentTitle.toLowerCase().startsWith(title.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return tournament.date().year() == year && matchesTitle(tournament.title());
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        Date tournamentDate = CBUtil.decodeDate(ByteBufferUtil.getIntL(serializedItem, 70));
        if (tournamentDate.year() != year) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(serializedItem);
        String tournamentTitle = ByteBufferUtil.getFixedSizeByteString(buf, 40);
        return matchesTitle(tournamentTitle);
    }
}
