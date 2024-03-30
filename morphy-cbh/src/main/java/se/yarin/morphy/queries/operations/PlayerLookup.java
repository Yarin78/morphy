package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PlayerLookup extends QueryOperator<Player> {
    private final @Nullable EntityFilter<Player> playerFilter;
    private final @NotNull QueryOperator<Player> source;
    private final @NotNull EntityIndexReadTransaction<Player> txn;

    public PlayerLookup(@NotNull QueryContext queryContext, @NotNull QueryOperator<Player> source, @Nullable EntityFilter<Player> playerFilter) {
        super(queryContext, true);
        assert !source.hasFullData();
        this.txn = transaction().playerTransaction();
        this.source = source;
        this.playerFilter = playerFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public @NotNull QuerySortOrder<Player> sortOrder() {
        return source.sortOrder();
    }

    public boolean mayContainDuplicates() {
        return source.mayContainDuplicates();
    }

    public Stream<QueryData<Player>> operatorStream() {
        return this.source.stream()
//                TODO: Something like this is nicer, but this loses the weight
//                .map(player -> txn.get(player.id(), playerFilter))
//                .filter(Objects::nonNull)
//                .map(QueryData::new);
                .flatMap(data -> {
                    Player player = txn.get(data.id(), playerFilter);
                    if (player == null) {
                        return Stream.of();
                    } else {
                        return Stream.of(new QueryData<>(data.id(), player, data.weight()));
                    }
                });
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        double ratio = context().queryPlanner().playerFilterEstimate(playerFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(sourceCost.estimateRows() * ratio));

        operatorCost
                .estimateRows(estimateRows)
                .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .estimatePageReads(context().queryPlanner().estimateGamePageReads(sourceCost.estimateRows()));
    }

    @Override
    public String toString() {
        if (playerFilter != null) {
            return "PlayerLookup(filter: " + playerFilter + ")";
        }
        return "PlayerLookup()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}
