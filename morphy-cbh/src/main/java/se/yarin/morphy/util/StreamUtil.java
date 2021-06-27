package se.yarin.morphy.util;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtil {

    /**
     * Merges two streams of identical IdObjects. The two source streams
     * must be in strictly increasing id order.
     */
    public static <T extends IdObject> Stream<T> mergeJoin(
            @NotNull Stream<T> leftStream,
            @NotNull Stream<T> rightStream,
            @NotNull BiFunction<T, T, T> merger) {
        final Iterator<T> leftIterator = leftStream.iterator();
        final Iterator<T> rightIterator = rightStream.iterator();


        final Iterator<T> mergeIterator = new Iterator<T>() {
            private T nextLeft = null;
            private T nextRight = null;
            private boolean hasInit = false;

            void init() {
                assert !hasInit;
                nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
                nextRight = rightIterator.hasNext() ? rightIterator.next() : null;
                prepareNext();
                hasInit = true;
            }

            private void prepareNext() {
                while (nextLeft != null && nextRight != null && nextLeft.id() != nextRight.id()) {
                    if (nextLeft.id() < nextRight.id()) {
                        nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
                    } else {
                        nextRight = rightIterator.hasNext() ? rightIterator.next() : null;
                    }
                }
            }
            @Override
            public boolean hasNext() {
                if (!hasInit) {
                    init();
                }
                if (nextLeft == null || nextRight == null) {
                    return false;
                }
                assert nextLeft.id() == nextRight.id();
                return true;
            }

            @Override
            public T next() {
                if (!hasInit) {
                    init();
                }
                if (nextLeft == null || nextRight == null) {
                     throw new NoSuchElementException();
                }
                assert nextLeft.id() == nextRight.id();
                T result = merger.apply(nextLeft, nextRight);
                nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
                prepareNext();
                return result;
            }
        };

        final Iterable<T> iterable = () -> mergeIterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Merges two streams of identical IdObjects.
     */
    public static <T extends IdObject> Stream<T> hashJoin(
            @NotNull Stream<T> leftStream,
            @NotNull Stream<T> rightStream,
            @NotNull BiFunction<T, T, T> merger) {
        Map<Integer, T> hashMap = rightStream.collect(Collectors.toMap(IdObject::id, Function.identity()));
        return leftStream
                .filter(idObject -> hashMap.containsKey(idObject.id()))
                .map(idObject -> merger.apply(idObject, hashMap.get(idObject.id())));
    }

}
