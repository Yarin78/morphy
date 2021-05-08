package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;

import java.text.SimpleDateFormat;

public class LastChangedTimestampColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Last changed";
    }

    @Override
    public String getValue(Game game) {
        if (game.getLastChangedTimestamp() == 0) {
            return "";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(game.getLastChangedTime().getTime());
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
