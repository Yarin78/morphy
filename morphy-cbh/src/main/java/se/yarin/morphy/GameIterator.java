package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.exceptions.MorphyInternalException;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.search.GameIteratorFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class GameIterator implements Iterator<Game> {
    private static final int BATCH_SIZE = 1000;

    private final @NotNull DatabaseReadTransaction transaction;
    private @Nullable final GameIteratorFilter filter;
    private @Nullable List<Game> batch = new ArrayList<>();
    private int batchPos, nextBatchStart;

    public GameIterator(@NotNull DatabaseReadTransaction transaction, int startId, @Nullable GameIteratorFilter filter) {
        this.transaction = transaction;
        this.nextBatchStart = startId;
        this.filter = filter;
        getNextBatch();
    }

    private void getNextBatch() {
        transaction.ensureTransactionIsOpen();

        // Since we have an optional filter, we may have to try multiple times to get a new batch
        // because the next batch might be empty
        while (batch != null && batchPos >= batch.size()) {
            int endIdExclusive = Math.min(transaction.database().count() + 1, nextBatchStart + BATCH_SIZE);
            if (nextBatchStart >= endIdExclusive) {
                batch = null;
            } else {
                List<GameHeader> gameHeaders = transaction.database().gameHeaderIndex().getRange(
                        nextBatchStart, endIdExclusive, filter == null ? null : filter.gameHeaderFilter());
                List<ExtendedGameHeader> extendedGameHeaders = transaction.database().extendedGameHeaderStorage().getRange(
                        nextBatchStart, endIdExclusive, filter == null ? null : filter.extendedGameHeaderFilter());
                if (gameHeaders.size() != extendedGameHeaders.size()) {
                    throw new MorphyInternalException("Number of elements returned from the GameHeader and ExtendedGameHeader storage mismatches");
                }
                ArrayList<Game> games = new ArrayList<>(gameHeaders.size());
                for (int i = 0; i < gameHeaders.size(); i++) {
                    GameHeader header = gameHeaders.get(i);
                    ExtendedGameHeader extendedHeader = extendedGameHeaders.get(i);
                    // null values mean it didn't match the filter
                    if (header != null && extendedHeader != null) {
                        games.add(new Game(transaction.database(), header, extendedHeader));
                    }
                }
                this.batch = games;
                nextBatchStart = endIdExclusive;
                batchPos = 0;
            }
        }
    }

    @Override
    public boolean hasNext() {
        return batch != null;
    }

    @Override
    public @NotNull Game next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of game iteration reached");
        }
        assert batch != null;
        Game nextGame = batch.get(batchPos++);
        if (batchPos == batch.size()) {
            getNextBatch();
        }
        return nextGame;
    }
}
