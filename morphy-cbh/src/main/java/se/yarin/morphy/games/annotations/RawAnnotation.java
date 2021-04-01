package se.yarin.morphy.games.annotations;

public interface RawAnnotation {
    int annotationType();

    byte[] rawData();
}
