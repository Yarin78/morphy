package se.yarin.morphy.games.annotations;

public interface RawAnnotation {
    int getAnnotationType();

    byte[] getRawData();
}
