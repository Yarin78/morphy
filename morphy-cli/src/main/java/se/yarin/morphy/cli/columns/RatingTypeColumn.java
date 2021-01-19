package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.RatingType;

public class RatingTypeColumn implements GameColumn {

    private final boolean isWhite;

    public RatingTypeColumn(boolean isWhite) {
        this.isWhite = isWhite;
    }

    @Override
    public String getHeader() {
        return "Rating type";
    }

    @Override
    public String getValue(Game game) {
        RatingType ratingType = isWhite ? game.getWhiteRatingType() : game.getBlackRatingType();
        return ratingType.toString();
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getId() {
        return "rating-type";
    }
}