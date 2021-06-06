package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class Distinct<T> extends QueryOperator<T> {
    private final @NotNull QueryOperator<T> source;

    public Distinct(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source) {
        super(queryContext);
        // TODO: If T is a game or entity, equality should be on id
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public Stream<T> operatorStream() {
        return source.stream().distinct();
    }

    @Override
    public OperatorCost estimateCost() {
        OperatorCost sourceCost = source.estimateCost();
        return ImmutableOperatorCost.builder()
                .rows(sourceCost.rows())
                .numDeserializations(0)
                .pageReads(0)
                .build();
    }

    @Override
    public String toString() {
        return "Distinct()";
    }
}
