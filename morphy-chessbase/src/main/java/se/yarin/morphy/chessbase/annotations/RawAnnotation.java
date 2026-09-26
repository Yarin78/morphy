package se.yarin.morphy.chessbase.annotations;

public interface RawAnnotation {
  int annotationType();

  byte[] rawData();
}
