package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

import java.text.SimpleDateFormat;

public class CreationTimestampColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Created";
    }

    @Override
    public String getValue(Game game) {
        if (game.getCreationTimestamp() == 0) {
            return "";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(game.getCreationTime().getTime());
    }

    @Override
    public String getId() {
        return "created";
    }

    @Override
    public int width() {
        return 19;
    }
}
