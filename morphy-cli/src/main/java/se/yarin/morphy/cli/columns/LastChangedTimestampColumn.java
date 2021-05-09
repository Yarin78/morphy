package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

import java.text.SimpleDateFormat;

public class LastChangedTimestampColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Last changed";
    }

    @Override
    public String getValue(Game game) {
        if (game.lastChangedTimestamp() == 0) {
            return "";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(game.lastChangedTime().getTime());
    }

    @Override
    public String getId() {
        return "updated";
    }

    @Override
    public int width() {
        return 19;
    }
}
