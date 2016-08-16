package se.yarin.cbhlib.annotations;

public interface RawAnnotation {
    int getAnnotationType();

    byte[] getRawData();
}
