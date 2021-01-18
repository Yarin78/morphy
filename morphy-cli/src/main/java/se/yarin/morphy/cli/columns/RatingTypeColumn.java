package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.RatingType;
import se.yarin.chess.GameModel;

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
    public String getValue(Database database, GameHeader header, GameModel game) {
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(header.getId());
        RatingType ratingType = isWhite ? extendedGameHeader.getWhiteRatingType() : extendedGameHeader.getBlackRatingType();
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