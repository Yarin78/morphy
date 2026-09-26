package se.yarin.morphy.chessbase.text;

import org.immutables.value.Value;

@Value.Immutable
public interface TextModel {
  TextHeaderModel header();

  TextContentsModel contents();
}
