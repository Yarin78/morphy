package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.util.ByteBufferUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RatingRangeFilter extends SearchFilterBase implements SearchFilter, SerializedGameHeaderFilter {
    private static final Pattern ratingRangePattern = Pattern.compile("^([0-9]+)?-([0-9]+)?$");

    @Getter
    private final int minRating;

    @Getter
    private final int maxRating;

    @Getter
    private final RatingColor color;

    public enum RatingColor {
        ANY,
        BOTH,
        WHITE,
        BLACK,
        AVERAGE,
        DIFFERENCE,
    }

    public RatingRangeFilter(Database database, int minRating, int maxRating, RatingColor color) {
        super(database);
        this.minRating = minRating;
        this.maxRating = maxRating;
        this.color = color;
    }

    public RatingRangeFilter(Database database, String ratingRange, RatingColor color) {
        super(database);
        this.color = color;
        Matcher matcher = ratingRangePattern.matcher(ratingRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid rating range specified: " + ratingRange);
        }
        this.minRating = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
        this.maxRating = matcher.group(2) == null ? 9999 : Integer.parseInt(matcher.group(2));
    }

    @Override
    public boolean matches(Game game) {
        if (game.isGuidingText()) {
            return false;
        }
        int whiteRating = game.getWhiteElo(), blackRating = game.getBlackElo();
        return matches(whiteRating, blackRating);
    }

    private boolean matches(int whiteRating, int blackRating) {
        boolean whiteMatches = whiteRating >= minRating && whiteRating <= maxRating;
        boolean blackMatches = blackRating >= minRating && blackRating <= maxRating;
        switch (color) {
            case ANY -> { return whiteMatches || blackMatches; }
            case BOTH -> { return whiteMatches && blackMatches; }
            case WHITE -> { return whiteMatches; }
            case BLACK -> { return blackMatches; }
            case AVERAGE -> {
                return (whiteRating + blackRating) / 2 >= minRating && (whiteRating + blackRating) / 2 <= maxRating;
            }
            case DIFFERENCE -> {
                return whiteRating > 0 && blackRating > 0 && Math.abs(whiteRating - blackRating) >= minRating && Math.abs(whiteRating - blackRating) <= maxRating;
            }
            default -> throw new IllegalStateException("Unexpected value: " + color);
        }
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text has no rating
            return false;
        }
        int whiteRating = ByteBufferUtil.getUnsignedShortB(serializedGameHeader, 31);
        int blackRating = ByteBufferUtil.getUnsignedShortB(serializedGameHeader, 33);
        return matches(whiteRating, blackRating);
    }
}
