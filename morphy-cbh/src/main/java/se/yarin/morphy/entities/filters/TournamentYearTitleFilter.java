package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class TournamentYearTitleFilter implements EntityIndexFilter<Tournament>  {
    private final int year;
    @NotNull private final String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentYearTitleFilter(int year, @NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.year = year;
        this.title = caseSensitive ? title : title.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matchesTitle(@NotNull String tournamentTitle) {
        if (exactMatch) {
            return caseSensitive ? tournamentTitle.equals(title) : tournamentTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? tournamentTitle.startsWith(title) : tournamentTitle.toLowerCase().startsWith(title);
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

    @Override
    public String toString() {
        String titleStr = caseSensitive ? "title" : "lower(title)";

        if (exactMatch) {
            return "year=%d and %s='%s'".formatted(year, titleStr, title);
        } else {
            return "year=%d %s like '%s%%'".formatted(year, titleStr, title);
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }

    @Override
    public Tournament start() {
        return Tournament.of(caseSensitive ? title : "", new Date(year));
    }

    @Override
    public Tournament end() {
        return Tournament.of((caseSensitive ? title : "") + "zzz", new Date(year));
    }
}
