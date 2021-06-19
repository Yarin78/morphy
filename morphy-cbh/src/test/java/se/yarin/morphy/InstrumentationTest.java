package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import se.yarin.morphy.metrics.*;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class InstrumentationTest {

    public static class TestMetric implements Metrics {
        private int reads, writes;

        public static MetricsRef<TestMetric> register(Instrumentation instrumentation, String name) {
            return instrumentation.register("test", name, TestMetric::new);
        }

        public void addReads(int count) {
            reads += count;
        }

        public void addWrites(int count) {
            writes += count;
        }

        @Override
        public void merge(@NotNull Metrics metrics) {
            TestMetric other = (TestMetric) metrics;
            reads += other.reads;
            writes += other.writes;
        }

        @Override
        public void clear() {
            reads = 0;
            writes = 0;
        }

        @Override
        public String formatHeaderRow() {
            return "reads writes\n";
        }

        @Override
        public String formatTableRow() {
            return reads + " " + writes;
        }

        @Override
        public boolean isEmpty(int threshold) {
            return reads <= threshold && writes <= threshold;
        }
    }

    @Test
    public void testRegisterMetrics() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> metricsRef = TestMetric.register(i,"my metric");

        assertEquals(0, metricsRef.get().reads);
        assertEquals(0, i.<TestMetric>getMetrics(metricsRef.metricsKey()).reads);
    }

    @Test
    public void updateMetrics() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> metricsRef = TestMetric.register(i,"my metric");

        metricsRef.update(metric -> metric.addReads(5));
        metricsRef.update(metric -> metric.addWrites(2));

        assertEquals(5, metricsRef.get().reads);
        assertEquals(5, i.<TestMetric>getMetrics(metricsRef.metricsKey()).reads);
        assertEquals(2, metricsRef.get().writes);
        assertEquals(2, i.<TestMetric>getMetrics(metricsRef.metricsKey()).writes);
    }

    @Test
    public void updateMetricsInContext() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> metricsRef = TestMetric.register(i,"my metric");

        metricsRef.update(metric -> metric.addWrites(2));
        assertEquals(2, metricsRef.get().writes);

        i.pushContext("txn open", false);
        metricsRef.update(metric -> metric.addWrites(3));
        assertEquals(3, metricsRef.get().writes);
        MetricsRepository subMetrics = i.popContext();
        assertEquals(3, ((TestMetric) subMetrics.getMetrics("test", "my metric")).writes);

        assertEquals(2, metricsRef.get().writes);
        assertEquals(2, ((TestMetric) i.getMetrics("test", "my metric")).writes);
    }

    @Test
    public void updateMetricsInContextMergesToParent() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> metricsRef = TestMetric.register(i,"my metric");

        metricsRef.update(metric -> metric.addWrites(2));

        try (var subMetrics = i.pushContext("txn open", true)) {
            metricsRef.update(metric -> metric.addWrites(3));
            assertEquals(3, subMetrics.<TestMetric>getMetrics("test", "my metric").writes);
        }

        assertEquals(5, metricsRef.get().writes);
        assertEquals(5, i.<TestMetric>getMetrics("test", "my metric").writes);
    }

    @Test
    public void updateMetricsInNestedContext() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> metricsRef = TestMetric.register(i,"my metric");

        metricsRef.update(metrics -> metrics.addReads(2));

        i.pushContext("level 1", true);
        metricsRef.update(metrics -> metrics.addReads(5));

        i.pushContext("level 2", true);
        metricsRef.update(metrics -> metrics.addReads(8));
        metricsRef.update(metrics -> metrics.addReads(1));

        MetricsRepository level2 = i.popContext();
        assertEquals(9, metricsRef.get(level2).reads);
        assertEquals(2, metricsRef.get(i).reads); // Global count not yet updated

        metricsRef.update(metrics -> metrics.addReads(3));

        MetricsRepository level1 = i.popContext();
        assertEquals(17, metricsRef.get(level1).reads); // level 1 and level 2

        assertEquals(19, metricsRef.get(i).reads); // Global count now updated
    }

    @Test
    public void multipleThreadNestedContextTest() throws InterruptedException {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> fooStats = TestMetric.register(i, "foo");

        int numThreads = 5;
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        MetricsRepository[] ti = new MetricsRepository[numThreads];

        for (int j = 0; j < numThreads; j++) {
            final int threadNum = j;
            new Thread(() -> {
                i.pushContext("thread " + threadNum, true);
                readyLatch.countDown();
                try {
                    readyLatch.await();
                    for (int k = 0; k < threadNum + 100; k++) {
                        fooStats.update(metrics -> metrics.addReads(1));
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
                ti[threadNum] = i.popContext();
                doneLatch.countDown();
            }, "thread " + threadNum).start();
        }

        doneLatch.await();
        for (int j = 0; j < numThreads; j++) {
            assertEquals(100 + j, fooStats.get(ti[j]).reads);
        }

        assertEquals(100 * numThreads + (numThreads * (numThreads - 1)) / 2, fooStats.get().reads);
    }

    @Test
    public void addMultipleStatsInNestedContext() {
        Instrumentation i = new Instrumentation();

        MetricsRef<ItemMetrics> fooStats = ItemMetrics.register(i, "foo");
        MetricsRef<ItemMetrics> barStats = ItemMetrics.register(i, "bar");
        MetricsRef<FileMetrics> fileStats = FileMetrics.register(i, "file");
        fooStats.update(metrics -> metrics.addDeserialization(1));
        barStats.update(metrics -> metrics.addDeserialization(2));
        fileStats.update(metrics -> metrics.addLogicalReads(3));

        i.pushContext("test context", true);
        fooStats.update(metrics -> metrics.addDeserialization(4));
        barStats.update(metrics -> metrics.addDeserialization(6));
        fileStats.update(metrics -> metrics.addLogicalReads(8));

        MetricsRepository pi = i.popContext();
        assertEquals(4, fooStats.get(pi).deserializations());
        assertEquals(6, barStats.get(pi).deserializations());
        assertEquals(8, fileStats.get(pi).logicalPageReads());

        assertEquals(5, fooStats.get().deserializations());
        assertEquals(8, barStats.get().deserializations());
        assertEquals(11, fileStats.get().logicalPageReads());
    }

    @Test
    public void createNewStatsWhenInContext() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> foo = TestMetric.register(i, "foo");
        i.pushContext("context", true);
        MetricsRef<TestMetric> bar = TestMetric.register(i, "bar");
        foo.update(metric -> metric.addReads(1));
        MetricsRepository metrics = i.popContext();
        assertEquals(1, metrics.size());
        assertEquals(2, i.size());
        bar.update(metric -> metric.addReads(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setMissingMetricsInContextFails() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> foo = TestMetric.register(i, "foo");
        i.pushContext("context", true);
        MetricsRef<TestMetric> bar = TestMetric.register(i, "bar");
        bar.update(metric -> metric.addReads(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void reregisterMetricsFails() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> foo = TestMetric.register(i, "foo");
        MetricsRef<TestMetric> fooAgain = TestMetric.register(i, "foo");
    }

    @Test
    public void reregisterMetricsWorks() {
        Instrumentation i = new Instrumentation();
        MetricsRef<TestMetric> foo = i.register("test", "foo", TestMetric::new, true);
        MetricsRef<TestMetric> fooAgain = i.register("test", "foo", TestMetric::new, true);

        foo.update(metric -> metric.addReads(2));
        fooAgain.update(metric -> metric.addReads(3));

        assertEquals(5, foo.get().reads);
        assertEquals(5, fooAgain.get().reads);
    }

}
