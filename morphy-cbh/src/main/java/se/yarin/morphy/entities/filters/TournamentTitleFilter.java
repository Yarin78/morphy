package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class TournamentTitleFilter implements EntityFilter<Tournament>  {
    @NotNull
    private final String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.title = caseSensitive ? title : title.toLowerCase();
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(String tournamentTitle) {
        if (exactMatch) {
            return caseSensitive ? tournamentTitle.equals(title) : tournamentTitle.equalsIgnoreCase(title);
        }
        return caseSensitive ? tournamentTitle.startsWith(title) : tournamentTitle.toLowerCase().startsWith(title);
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return matches(tournament.title());
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        ByteBuffer buf = ByteBuffer.wrap(serializedItem);
        String tournamentTitle = ByteBufferUtil.getFixedSizeByteString(buf, 40);
        return matches(tournamentTitle);
    }

    @Override
    public String toString() {
        String titleStr = caseSensitive ? "title" : "lower(title)";

        if (exactMatch) {
            return "%s='%s'".formatted(titleStr, title);
        } else {
            return "%s like '%s%%'".formatted(titleStr, title);
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }
}
