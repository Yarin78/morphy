package se.yarin.cbhlib.util;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.storage.FileBlobStorage;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

// TODO: Write tests, improve performance
public class SimpleLRUCache<K, V> {
    private static final Logger log = LoggerFactory.getLogger(SimpleLRUCache.class);

    private final int capacity;
    private final Map<K, Data> cache;
    private final LinkedList<Data> dataList;

    private int cacheGetRequests = 0, cacheHits = 0, cacheMisses = 0;

    @lombok.Data
    @AllArgsConstructor
    private class Data {
        private K key;
        private V value;
    }

    public SimpleLRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.dataList = new LinkedList<>();

    }
    public V get(K key) {
        cacheGetRequests += 1;
        V res = null;
        if (cache.containsKey(key)) {
            cacheHits += 1;
            Data data = cache.get(key);
            // Remove the data from its location
            dataList.remove(data);
            // Add it to the end of the list
            dataList.add(data);
            res = data.getValue();
        } else {
            cacheMisses += 1;
        }
        if (log.isDebugEnabled() && cacheGetRequests % 100000 == 0) {
            log.debug("{} cache requests, {} hits and {} misses", cacheGetRequests, cacheHits, cacheMisses);
        }
        return res;
    }

    public void clear() {
        cache.clear();
        dataList.clear();
    }

    public void evict(K key) {
        if (cache.containsKey(key)) {
            Data oldData = cache.get(key);
            // Remove old data from linked list
            dataList.remove(oldData);  // TODO: This is a linear operation
            cache.remove(key);
        }
    }

    public void set(K key, V value) {
        if (cache.containsKey(key)) {
            Data oldData = cache.get(key);
            // Remove old data from linked list
            dataList.remove(oldData);  // TODO: This is a linear operation
            Data newData = new Data(key, value);
            // Update the value
            cache.put(key, newData);
            // Add new data at the end of the linked list
            dataList.add(newData);
        } else {
            Data data = new Data(key, value);
            if (cache.size() >= capacity) {
                // Remove the oldest value from both map and linked list
                cache.remove(dataList.pollFirst().getKey());
            }
            cache.put(key, data);
            dataList.add(data);
        }
    }
}
