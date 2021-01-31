package se.yarin.cbhlib.games;

import lombok.Getter;

public class TextModel {
    @Getter
    private final TextHeaderModel header;

    @Getter
    private final TextContentsModel contents;

    public TextModel(TextHeaderModel header, TextContentsModel contents) {
        this.header = header;
        this.contents = contents;
    }
}
