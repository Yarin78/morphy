package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class RatingColumn implements GameColumn {

    private final boolean isWhite;

    public RatingColumn(boolean isWhite) {
        this.isWhite = isWhite;
    }

    @Override
    public String getHeader() {
        return "";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Game game) {
        if (game.guidingText()) {
            return "";
        }
        int rating = isWhite ? game.whiteElo() : game.blackElo();
        String elo = rating == 0 ? "" : Integer.toString(rating);
        return String.format("%4s", elo);
    }

    @Override
    public String getId() {
        return "rating";
    }

    @Override
    public int width() {
        return 4;
    }
}
