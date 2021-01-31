package se.yarin.cbhlib.games;

import lombok.Getter;
import lombok.Setter;

public class TextHeaderModel {
    @Getter @Setter
    private String tournament = "";

    @Getter @Setter
    private int tournamentYear = 0;

    @Getter @Setter
    private String annotator = "";

    @Getter @Setter
    private String source = "";

    @Getter @Setter
    private int round = 0;

    @Getter @Setter
    private int subRound = 0;
}
