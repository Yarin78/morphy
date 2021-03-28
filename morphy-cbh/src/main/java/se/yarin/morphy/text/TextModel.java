package se.yarin.morphy.text;

import org.immutables.value.Value;

@Value.Immutable
public interface TextModel {
    TextHeaderModel header();

    TextContentsModel contents();
}

