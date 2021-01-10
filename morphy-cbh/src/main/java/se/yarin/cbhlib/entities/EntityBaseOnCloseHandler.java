package se.yarin.cbhlib.entities;

import java.io.IOException;

public interface EntityBaseOnCloseHandler<T extends Entity & Comparable<T>> {
    void closing(EntityBase<T> entityBase) throws IOException;
}
