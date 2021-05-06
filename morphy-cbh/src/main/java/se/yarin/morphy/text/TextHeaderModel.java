package se.yarin.morphy.text;

import org.immutables.value.Value;
import se.yarin.chess.Date;

@Value.Immutable
public abstract class TextHeaderModel {
    @Value.Default
    public String tournament() {
        return "";
    }

    @Value.Default
    public Date tournamentDate() {
        return Date.unset();
    }

    @Value.Default
    public String annotator() {
        return "";
    }

    @Value.Default
    public String source() {
        return "";
    }

    @Value.Default
    public int round() {
        return 0;
    }

    @Value.Default
    public int subRound() {
        return 0;
    }
}
