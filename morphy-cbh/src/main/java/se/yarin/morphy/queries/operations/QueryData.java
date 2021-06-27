package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;

public class QueryData<T extends IdObject> {
    private int id;
    private @Nullable T data;
    private double weight;

    public int id() {
        return id;
    }

    public T data() {
        return data;
    }

    public double weight() {
        return weight;
    }

    public QueryData(int id) {
        this(id, null, 1.0);
    }

    public QueryData(@NotNull T data) {
        this(data.id(), data, 1.0);
    }

    public QueryData(int id, @Nullable T data, double weight) {
        if (data != null && data.id() != id) {
            throw new IllegalArgumentException("The id must much that of the data");
        }
        if (this.id < 0) {
            // Implies we're streaming non-entities which indicates an error elsewhere
            throw new IllegalArgumentException("Id must be non-negative");
        }
        this.id = id;
        this.data = data;
        this.weight = weight;
    }
}
