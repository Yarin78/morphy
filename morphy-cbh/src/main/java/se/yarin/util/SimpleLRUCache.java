package se.yarin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple LRU (Least Recently Used) cache implementation.
 *
 * <p>Uses {@link LinkedHashMap} in access-order mode for O(1) performance on all operations.
 */
public class SimpleLRUCache<K, V> {
  private static final Logger log = LoggerFactory.getLogger(SimpleLRUCache.class);

  private final int capacity;
  private final LinkedHashMap<K, V> cache;

  private int cacheGetRequests = 0, cacheHits = 0, cacheMisses = 0;

  public SimpleLRUCache(int capacity) {
    this.capacity = capacity;
    // accessOrder=true makes it maintain access order (LRU) rather than insertion order
    this.cache =
        new LinkedHashMap<>(capacity, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > SimpleLRUCache.this.capacity;
          }
        };
  }

  public V get(K key) {
    cacheGetRequests += 1;
    V value = cache.get(key);
    if (value != null) {
      cacheHits += 1;
    } else if (cache.containsKey(key)) {
      // Handle null values stored in cache
      cacheHits += 1;
    } else {
      cacheMisses += 1;
    }
    if (log.isDebugEnabled() && cacheGetRequests % 100000 == 0) {
      log.debug(
          "{} cache requests, {} hits and {} misses", cacheGetRequests, cacheHits, cacheMisses);
    }
    return value;
  }

  public void clear() {
    cache.clear();
  }

  public void evict(K key) {
    cache.remove(key);
  }

  public void set(K key, V value) {
    cache.put(key, value);
  }

  public int size() {
    return cache.size();
  }

  public int getCapacity() {
    return capacity;
  }

  public int getCacheGetRequests() {
    return cacheGetRequests;
  }

  public int getCacheHits() {
    return cacheHits;
  }

  public int getCacheMisses() {
    return cacheMisses;
  }
}
