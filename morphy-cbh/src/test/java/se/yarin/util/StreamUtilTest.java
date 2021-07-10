package se.yarin.util;

import org.junit.Test;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.util.StreamUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class StreamUtilTest {

    private static class MyObj implements IdObject {
        private int id;

        public MyObj(int id) {
            this.id = id;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyObj myObj = (MyObj) o;
            return id == myObj.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Test
    public void testJoins() {
        Random random = new Random(0);
        for (int i = 0; i < 25; i++) {
            double PROB = random.nextDouble() * 0.2;
            ArrayList<MyObj> left = new ArrayList<>(), right = new ArrayList<>();
            for (int id = 0; id < 50000; id++) {
                if (random.nextDouble() < PROB) {
                    left.add(new MyObj(id));
                }
                if (random.nextDouble() < PROB) {
                    right.add(new MyObj(id));
                }
            }

            List<MyObj> hashJoinResult = StreamUtil.hashJoin(left.stream(), right.stream(), (leftEl, rightEl) -> leftEl).collect(Collectors.toList());
            List<MyObj> mergeJoinResult = StreamUtil.mergeJoin(left.stream(), right.stream(), (leftEl, rightEl) -> leftEl).collect(Collectors.toList());

            assertEquals(hashJoinResult, mergeJoinResult);
            // System.out.println(hashJoinResult.size() + " " + mergeJoinResult.size());
        }
    }

    @Test(expected = AssertionError.class)
    public void testMergeJoinOutOfOrder() {
        List<MyObj> left = List.of(new MyObj(5), new MyObj(3));
        List<MyObj> right = List.of(new MyObj(4), new MyObj(6));

        StreamUtil.mergeJoin(left.stream(), right.stream(), (leftEl, rightEl) -> leftEl).collect(Collectors.toList());
    }
}
