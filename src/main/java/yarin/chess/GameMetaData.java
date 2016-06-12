package yarin.chess;

import lombok.Data;
import lombok.NonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class GameMetaData {
    @NonNull private String whiteFirstName = "";
    @NonNull private String whiteLastName = "";
    @NonNull private String blackFirstName = "";
    @NonNull private String blackLastName = "";
    private int whiteElo, blackElo; // 0 = not set

    @NonNull private String whiteTeam = "";
    @NonNull private String blackTeam = "";

    @NonNull private String result = "";

    @NonNull private String playedDate = ""; // TODO: Create special Date class
    @NonNull private String eco = "";

    @NonNull private String eventName = "";
    @NonNull private String eventSite = "";
    @NonNull private String eventCountry = "";
    private int round, subRound; // 0 = not set

    @NonNull private String source = "";

    @NonNull private String annotator = "";

    private final Map<String, String> custom = new HashMap<>();

    public String getWhiteName() {
        if (whiteFirstName.length() == 0)
            return whiteLastName;
        return whiteLastName + ", " + whiteFirstName;
    }

    public String getBlackName() {
        if (blackFirstName.length() == 0)
            return blackLastName;
        return blackLastName + ", " + blackFirstName;
    }

    public GameMetaData() {
    }

    public void addExtra(@NonNull String key, @NonNull String value) {
        custom.put(key, value);
    }
}
